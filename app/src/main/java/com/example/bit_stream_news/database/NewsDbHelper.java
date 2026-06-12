package com.example.bit_stream_news.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.bit_stream_news.model.NewsArticle;

import java.util.ArrayList;
import java.util.List;

/**
 * NewsDbHelper — SQLiteOpenHelper managing the local news cache.
 *
 * SCHEMA
 * ──────
 * Table: news
 *   id           TEXT PRIMARY KEY   — URL-based hash for stable identity
 *   title        TEXT NOT NULL
 *   description  TEXT
 *   url          TEXT NOT NULL UNIQUE
 *   image_url    TEXT
 *   published_at TEXT
 *   category     TEXT DEFAULT 'general'
 *   source       TEXT
 *   cached_at    INTEGER NOT NULL   — Unix epoch millis
 *
 * CACHE STRATEGY
 * ──────────────
 * API quota = 100 req/month → ALWAYS serve SQLite first.
 * Only fetch fresh data when the user explicitly taps Refresh
 * or when the cache is completely empty (first launch).
 *
 * Cache entries older than CACHE_EXPIRY_MS (24 hours) are considered
 * stale but are still displayed while a background refresh is pending.
 * clearOldCache() evicts entries older than CACHE_MAX_AGE_MS (7 days)
 * to prevent the database growing unbounded.
 */
public class NewsDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "NewsDbHelper";

    // ── Database metadata ─────────────────────────────────────────────────────

    public static final String DB_NAME    = "bitstreamNews.db";
    public static final int    DB_VERSION = 2;  // v2: added is_bookmarked column

    // ── Table / column constants ──────────────────────────────────────────────

    public static final String TABLE_NEWS          = "news";
    public static final String COL_ID              = "id";
    public static final String COL_TITLE           = "title";
    public static final String COL_DESCRIPTION     = "description";
    public static final String COL_URL             = "url";
    public static final String COL_IMAGE_URL       = "image_url";
    public static final String COL_PUBLISHED_AT    = "published_at";
    public static final String COL_CATEGORY        = "category";
    public static final String COL_SOURCE          = "source";
    public static final String COL_CACHED_AT       = "cached_at";
    public static final String COL_BOOKMARKED      = "is_bookmarked";

    // ── Cache TTL ─────────────────────────────────────────────────────────────

    /** Entries older than this (24 h) are treated as soft-stale. */
    public static final long CACHE_EXPIRY_MS  = 24L * 60 * 60 * 1000;

    /** Entries older than this (7 days) are hard-evicted to save disk space. */
    public static final long CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    // ── DDL ───────────────────────────────────────────────────────────────────

    private static final String SQL_CREATE_NEWS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NEWS + " ("
            + COL_ID           + " TEXT PRIMARY KEY, "
            + COL_TITLE        + " TEXT NOT NULL, "
            + COL_DESCRIPTION  + " TEXT, "
            + COL_URL          + " TEXT NOT NULL UNIQUE, "
            + COL_IMAGE_URL    + " TEXT, "
            + COL_PUBLISHED_AT + " TEXT, "
            + COL_CATEGORY     + " TEXT DEFAULT 'general', "
            + COL_SOURCE       + " TEXT, "
            + COL_CACHED_AT    + " INTEGER NOT NULL, "
            + COL_BOOKMARKED   + " INTEGER NOT NULL DEFAULT 0"
            + ");";

    private static final String SQL_CREATE_IDX_CATEGORY =
            "CREATE INDEX IF NOT EXISTS idx_news_category ON "
            + TABLE_NEWS + " (" + COL_CATEGORY + ");";

    private static final String SQL_CREATE_IDX_CACHED_AT =
            "CREATE INDEX IF NOT EXISTS idx_news_cached_at ON "
            + TABLE_NEWS + " (" + COL_CACHED_AT + ");";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile NewsDbHelper sInstance;

    public static NewsDbHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (NewsDbHelper.class) {
                if (sInstance == null) {
                    sInstance = new NewsDbHelper(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private NewsDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        // Enable WAL for better concurrent read performance
        SQLiteDatabase db = getWritableDatabase();
        db.enableWriteAheadLogging();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_NEWS);
        db.execSQL(SQL_CREATE_IDX_CATEGORY);
        db.execSQL(SQL_CREATE_IDX_CACHED_AT);
        Log.d(TAG, "Database created with table: " + TABLE_NEWS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading DB from v" + oldVersion + " to v" + newVersion);
        if (oldVersion < 2) {
            // v1 → v2: add is_bookmarked column (non-destructive)
            db.execSQL("ALTER TABLE " + TABLE_NEWS
                    + " ADD COLUMN " + COL_BOOKMARKED + " INTEGER NOT NULL DEFAULT 0");
            Log.d(TAG, "Migration v1→v2: added is_bookmarked column.");
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Bulk-inserts or replaces a list of articles into the cache.
     *
     * Uses INSERT OR REPLACE so that refreshed articles automatically
     * overwrite stale rows with the same id (URL hash).
     *
     * Wrapped in a single transaction for performance — avoids one
     * disk-sync per row when inserting batches of 20-100 articles.
     *
     * @param articles list of articles to persist; must not be null.
     */
    public void insertNews(List<NewsArticle> articles) {
        if (articles == null || articles.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (NewsArticle article : articles) {
                ContentValues cv = articleToValues(article, now);
                long rowId = db.insertWithOnConflict(
                        TABLE_NEWS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                if (rowId == -1) {
                    Log.w(TAG, "Failed to insert article: " + article.getUrl());
                }
            }
            db.setTransactionSuccessful();
            Log.d(TAG, "Inserted/replaced " + articles.size() + " articles into cache.");
        } finally {
            db.endTransaction();
        }
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all cached articles, ordered newest-first by published_at.
     *
     * @return list of {@link NewsArticle}; empty list if cache is empty.
     */
    public List<NewsArticle> getAllNews() {
        return queryNews(null, null,
                COL_PUBLISHED_AT + " DESC");
    }

    /**
     * Returns cached articles filtered by category, newest-first.
     *
     * @param category category string, e.g. "technology" — case-insensitive.
     * @return filtered list; empty if no matching articles in cache.
     */
    public List<NewsArticle> getNewsByCategory(String category) {
        if (category == null || category.isEmpty() || category.equalsIgnoreCase("all")) {
            return getAllNews();
        }
        return queryNews(
                COL_CATEGORY + " = ?",
                new String[]{ category.toLowerCase() },
                COL_PUBLISHED_AT + " DESC");
    }

    /**
     * Returns true if the cache is completely empty (no rows at all).
     * Used by the Repository to decide on first-launch API fetch.
     */
    public boolean isCacheEmpty() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NEWS, null);
        long count = 0;
        if (cursor.moveToFirst()) count = cursor.getLong(0);
        cursor.close();
        return count == 0;
    }

    /**
     * Returns the timestamp (millis) of the most recently cached article.
     * Returns 0 if the cache is empty.
     */
    public long getLatestCachedAt() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT MAX(" + COL_CACHED_AT + ") FROM " + TABLE_NEWS, null);
        long latest = 0;
        if (cursor.moveToFirst()) latest = cursor.getLong(0);
        cursor.close();
        return latest;
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Deletes articles older than {@link #CACHE_MAX_AGE_MS} (7 days).
     *
     * Should be called on app startup (from a background thread) to keep
     * the database lean. Safe to call frequently — no-op if cache is fresh.
     *
     * @return number of rows deleted.
     */
    public int clearOldCache() {
        long cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS;
        SQLiteDatabase db = getWritableDatabase();
        int deleted = db.delete(TABLE_NEWS,
                COL_CACHED_AT + " < ?",
                new String[]{ String.valueOf(cutoff) });
        if (deleted > 0) {
            Log.d(TAG, "Evicted " + deleted + " stale cache entries older than 7 days.");
        }
        return deleted;
    }

    /**
     * Wipes the entire cache BUT preserves bookmarked articles.
     * Called before a hard refresh.
     */
    public void clearAllCache() {
        SQLiteDatabase db = getWritableDatabase();
        // Only delete non-bookmarked rows so saved articles survive refresh
        int deleted = db.delete(TABLE_NEWS, COL_BOOKMARKED + " = 0", null);
        Log.d(TAG, "Cache cleared: " + deleted + " non-bookmarked rows removed.");
    }

    // ── Bookmark operations ───────────────────────────────────────────────────

    /**
     * Marks an article as bookmarked (is_bookmarked = 1).
     * The article must already exist in the news table (it does, because
     * the user can only bookmark after viewing from the cache).
     *
     * @param articleId  the id column value (URL hash).
     */
    public void bookmarkArticle(String articleId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues(1);
        cv.put(COL_BOOKMARKED, 1);
        int rows = db.update(TABLE_NEWS, cv, COL_ID + " = ?", new String[]{articleId});
        Log.d(TAG, "bookmarkArticle: updated " + rows + " row(s) for id=" + articleId);
    }

    /**
     * Removes bookmark from an article (is_bookmarked = 0).
     *
     * @param articleId  the id column value.
     */
    public void removeBookmark(String articleId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues(1);
        cv.put(COL_BOOKMARKED, 0);
        int rows = db.update(TABLE_NEWS, cv, COL_ID + " = ?", new String[]{articleId});
        Log.d(TAG, "removeBookmark: updated " + rows + " row(s) for id=" + articleId);
    }

    /**
     * Returns true if the article is currently bookmarked.
     *
     * @param articleId  the id column value.
     */
    public boolean isBookmarked(String articleId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NEWS,
                new String[]{COL_BOOKMARKED},
                COL_ID + " = ?",
                new String[]{articleId},
                null, null, null);
        boolean bookmarked = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                bookmarked = cursor.getInt(0) == 1;
            }
            cursor.close();
        }
        return bookmarked;
    }

    /**
     * Returns all bookmarked articles, ordered by when they were cached.
     *
     * @return list of bookmarked {@link NewsArticle}; empty if none saved.
     */
    public List<NewsArticle> getBookmarkedNews() {
        return queryNews(
                COL_BOOKMARKED + " = 1",
                null,
                COL_CACHED_AT + " DESC");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Core query helper. Runs a SELECT on the news table with optional
     * WHERE clause and ORDER BY, then maps each row to a {@link NewsArticle}.
     */
    private List<NewsArticle> queryNews(String selection,
                                        String[] selectionArgs,
                                        String orderBy) {
        List<NewsArticle> results = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_NEWS,
                null,              // all columns
                selection,
                selectionArgs,
                null,              // groupBy
                null,              // having
                orderBy,
                null               // limit
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                results.add(cursorToArticle(cursor));
            }
            cursor.close();
        }

        Log.d(TAG, "queryNews returned " + results.size() + " articles.");
        return results;
    }

    /** Maps a {@link Cursor} row to a {@link NewsArticle}. */
    private NewsArticle cursorToArticle(Cursor cursor) {
        int bookmarkIdx = cursor.getColumnIndex(COL_BOOKMARKED);
        boolean bookmarked = (bookmarkIdx >= 0) && (cursor.getInt(bookmarkIdx) == 1);
        return NewsArticle.builder()
                .id(cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)))
                .title(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)))
                .description(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)))
                .url(cursor.getString(cursor.getColumnIndexOrThrow(COL_URL)))
                .imageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)))
                .publishedAt(cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISHED_AT)))
                .category(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)))
                .source(cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE)))
                .cachedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_CACHED_AT)))
                .bookmarked(bookmarked)
                .build();
    }

    /** Builds a {@link ContentValues} map from a {@link NewsArticle}. */
    private ContentValues articleToValues(NewsArticle article, long cachedAt) {
        ContentValues cv = new ContentValues(9);
        cv.put(COL_ID,           article.getId());
        cv.put(COL_TITLE,        article.getTitle());
        cv.put(COL_DESCRIPTION,  article.getDescription());
        cv.put(COL_URL,          article.getUrl());
        cv.put(COL_IMAGE_URL,    article.getImageUrl());
        cv.put(COL_PUBLISHED_AT, article.getPublishedAt());
        cv.put(COL_CATEGORY,     article.getCategory() != null
                                 ? article.getCategory().toLowerCase() : "general");
        cv.put(COL_SOURCE,       article.getSource());
        cv.put(COL_CACHED_AT,    cachedAt);
        return cv;
    }
}
