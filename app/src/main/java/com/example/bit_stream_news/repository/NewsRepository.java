package com.example.bit_stream_news.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.bit_stream_news.database.NewsDbHelper;
import com.example.bit_stream_news.model.NewsApiResponse;
import com.example.bit_stream_news.model.NewsArticle;
import com.example.bit_stream_news.network.ApiClient;
import com.example.bit_stream_news.network.NewsApiService;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

/**
 * NewsRepository — Single source of truth for news data.
 *
 * CACHE-FIRST STRATEGY (quota protection)
 * ─────────────────────────────────────────────────────────────────────────────
 * API quota is 100 req/month. This repository NEVER calls the network unless:
 *   1. The SQLite cache is completely empty (first launch / cleared).
 *   2. The user explicitly presses the Refresh button.
 *
 *  getNews()          → serve SQLite → if empty, auto-fetch API once
 *  refreshNews()      → force fetch API → overwrite SQLite (Refresh button only)
 *  getNewsByCategory()→ serve SQLite filtered (no API call)
 *
 * THREADING MODEL
 * ─────────────────────────────────────────────────────────────────────────────
 * All database reads/writes and all Retrofit calls run on an ExecutorService
 * (single background thread). Results are posted back to the main thread via
 * a Handler so that UI callbacks are always safe to update Views directly.
 *
 *   [Main Thread] → repository.getNews(callback)
 *        ↓
 *   [Executor Thread] → SQLite read → (optional) Retrofit → SQLite write
 *        ↓
 *   [Handler → Main Thread] → callback.onSuccess(articles) / onError(msg)
 *
 * USAGE IN FRAGMENT
 * ─────────────────────────────────────────────────────────────────────────────
 *   NewsRepository repo = NewsRepository.getInstance(requireContext());
 *
 *   // Normal load (cache-first):
 *   repo.getNews(new NewsRepository.NewsCallback() {
 *       public void onSuccess(List<NewsArticle> articles) { adapter.submitList(articles); }
 *       public void onError(String message) { showError(message); }
 *   });
 *
 *   // On Refresh button click ONLY:
 *   repo.refreshNews(callback);
 */
public class NewsRepository {

    private static final String TAG = "NewsRepository";

    // ── API defaults ──────────────────────────────────────────────────────────

    private static final String DEFAULT_LANGUAGE = "en";       // ISO language code
    private static final String DEFAULT_COUNTRY  = "US";       // ISO country code (uppercase for this API)
    private static final int    DEFAULT_LIMIT     = 20;         // keep low to save quota

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final NewsDbHelper    dbHelper;
    private final NewsApiService  apiService;
    private final ExecutorService executor;
    private final Handler         mainHandler;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile NewsRepository sInstance;

    public static NewsRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (NewsRepository.class) {
                if (sInstance == null) {
                    sInstance = new NewsRepository(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private NewsRepository(Context context) {
        this.dbHelper    = NewsDbHelper.getInstance(context);
        this.apiService  = ApiClient.getInstance();
        // Single background thread — serialises all DB + network ops
        this.executor    = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "news-repo-thread");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Evict very old cache entries on every cold start (background)
        executor.execute(dbHelper::clearOldCache);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load news with cache-first logic.
     *
     * Flow:
     *   1. Read SQLite on background thread.
     *   2. If results found → deliver to callback immediately.
     *   3. If SQLite empty → fetch from API → save to SQLite → deliver results.
     *
     * @param callback receives results on the main thread.
     */
    public void getNews(NewsCallback callback) {
        executor.execute(() -> {
            try {
                List<NewsArticle> cached = dbHelper.getAllNews();
                if (!cached.isEmpty()) {
                    Log.d(TAG, "Cache hit: " + cached.size() + " articles served from SQLite.");
                    postSuccess(callback, cached);
                } else {
                    Log.d(TAG, "Cache empty — fetching from API (quota: 1 req).");
                    fetchFromApiAndCache(DEFAULT_LANGUAGE, DEFAULT_COUNTRY,
                                        null, DEFAULT_LIMIT, callback);
                }
            } catch (Exception e) {
                Log.e(TAG, "getNews failed", e);
                postError(callback, "Failed to load news: " + e.getMessage());
            }
        });
    }

    /**
     * Force-refresh from the API, regardless of cache state.
     *
     * IMPORTANT: Only call this when the user explicitly presses the
     * Refresh button. Do NOT call on every screen load — you have
     * only 100 requests per month!
     *
     * Flow:
     *   1. Fetch from API on background thread.
     *   2. On success → overwrite SQLite (INSERT OR REPLACE).
     *   3. Deliver fresh results to callback on main thread.
     *
     * @param callback receives results on the main thread.
     */
    public void refreshNews(NewsCallback callback) {
        executor.execute(() -> {
            Log.d(TAG, "Force refresh requested by user (consumes 1 API request).");
            fetchFromApiAndCache(DEFAULT_LANGUAGE, DEFAULT_COUNTRY,
                                 null, DEFAULT_LIMIT, callback);
        });
    }

    /**
     * Returns news filtered by category, always from SQLite (no API call).
     *
     * If no articles exist for the requested category, returns all cached
     * articles as a fallback (rather than burning quota on a category fetch).
     *
     * @param category  e.g. "technology", "world", "sports". Pass "all" or null for all.
     * @param callback  receives results on the main thread.
     */
    public void getNewsByCategory(String category, NewsCallback callback) {
        executor.execute(() -> {
            try {
                List<NewsArticle> articles = dbHelper.getNewsByCategory(category);
                Log.d(TAG, "Category '" + category + "': " + articles.size() + " articles from cache.");
                postSuccess(callback, articles);
            } catch (Exception e) {
                Log.e(TAG, "getNewsByCategory failed", e);
                postError(callback, "Failed to filter by category: " + e.getMessage());
            }
        });
    }

    /**
     * Refresh news for a specific category from the API.
     *
     * Only call when the user explicitly requests a refresh while browsing
     * a specific category tab.
     *
     * @param category  Topic/category string.
     * @param callback  receives results on the main thread.
     */
    public void refreshNewsByCategory(String category, NewsCallback callback) {
        executor.execute(() -> {
            Log.d(TAG, "Category refresh for '" + category + "' (consumes 1 API request).");
            fetchFromApiAndCache(DEFAULT_LANGUAGE, null, category, DEFAULT_LIMIT, callback);
        });
    }

    // ── Private — network + cache logic ──────────────────────────────────────

    /**
     * Executes a synchronous Retrofit call on the current background thread,
     * maps the response to {@link NewsArticle} objects, saves them to SQLite,
     * then posts the result to the callback on the main thread.
     *
     * @param language  API language param.
     * @param country   API country param (null to omit).
     * @param topic     API topic/category param (null for top-headlines).
     * @param limit     Max articles to request.
     * @param callback  Callback to receive results.
     */
    private void fetchFromApiAndCache(String language, String country,
                                      String topic, int limit,
                                      NewsCallback callback) {
        try {
            // Choose the right endpoint
            Call<NewsApiResponse> call;
            if (topic != null && !topic.isEmpty()) {
                call = apiService.getNewsByCategory(language, topic, limit);
            } else {
                call = apiService.getTopHeadlines(language, country, limit);
            }

            Response<NewsApiResponse> response = call.execute(); // synchronous on executor thread

            if (!response.isSuccessful() || response.body() == null) {
                String errMsg = "API error " + response.code() + ": "
                        + (response.errorBody() != null ? response.errorBody().string() : "no body");
                Log.e(TAG, errMsg);
                // Serve stale cache as fallback
                List<NewsArticle> stale = dbHelper.getAllNews();
                if (!stale.isEmpty()) {
                    Log.w(TAG, "Serving stale cache as fallback (" + stale.size() + " articles).");
                    postSuccess(callback, stale);
                } else {
                    postError(callback, "No data available. " + errMsg);
                }
                return;
            }

            // Map API articles → local NewsArticle model
            List<NewsApiResponse.Article> rawArticles = response.body().getArticles();
            Log.d(TAG, "Raw articles from API: " + rawArticles.size());

            // Log first article for debugging field names
            if (!rawArticles.isEmpty()) {
                NewsApiResponse.Article first = rawArticles.get(0);
                Log.d(TAG, "Sample article — title: '" + first.getTitle()
                        + "', url: '" + first.getUrl()
                        + "', source: '" + first.getSourceName() + "'");
            }

            List<NewsArticle> articles = mapToNewsArticles(rawArticles, topic);

            if (articles.isEmpty()) {
                Log.w(TAG, "Mapped 0 articles from " + rawArticles.size() + " raw. Check field names!");
                // Try to serve stale cache rather than showing error
                List<NewsArticle> stale = dbHelper.getAllNews();
                if (!stale.isEmpty()) {
                    postSuccess(callback, stale);
                } else {
                    postError(callback, "No articles could be parsed from server response.");
                }
                return;
            }

            // Persist to SQLite cache
            dbHelper.insertNews(articles);
            Log.d(TAG, "Cached " + articles.size() + " fresh articles to SQLite.");

            postSuccess(callback, articles);

        } catch (Exception e) {
            Log.e(TAG, "Network request failed", e);
            // Attempt to serve stale cache so the user always sees something
            try {
                List<NewsArticle> stale = dbHelper.getAllNews();
                if (!stale.isEmpty()) {
                    Log.w(TAG, "Network error — serving stale cache.");
                    postSuccess(callback, stale);
                } else {
                    postError(callback, "Connection failed: " + e.getMessage());
                }
            } catch (Exception dbEx) {
                postError(callback, "Connection failed: " + e.getMessage());
            }
        }
    }

    // ── Private — mapping ─────────────────────────────────────────────────────

    /**
     * Maps raw API {@link NewsApiResponse.Article} objects to local
     * {@link NewsArticle} model objects, assigning stable IDs and category.
     */
    private List<NewsArticle> mapToNewsArticles(
            List<NewsApiResponse.Article> rawArticles, String categoryHint) {

        List<NewsArticle> result = new ArrayList<>(rawArticles.size());
        long now = System.currentTimeMillis();
        int skipped = 0;

        for (NewsApiResponse.Article raw : rawArticles) {
            // Only require title — URL can come from 'link' or 'url' via @SerializedName alternate
            if (raw.getTitle() == null || raw.getTitle().isEmpty()) {
                skipped++;
                continue;
            }

            // Build a stable ID: prefer articleId from API, fallback to URL hash, fallback to title hash
            String idSource = raw.getUrl() != null ? raw.getUrl()
                            : raw.getArticleId() != null ? raw.getArticleId()
                            : raw.getTitle();
            String id = hashUrl(idSource);

            // Use a placeholder URL if none provided (so the DB row is still valid)
            String url = raw.getUrl() != null ? raw.getUrl() : "";

            result.add(NewsArticle.builder()
                    .id(id)
                    .title(raw.getTitle())
                    .description(raw.getBestDescription())   // picks longer of description vs content
                    .url(url)
                    .imageUrl(raw.getUrlToImage())
                    .publishedAt(raw.getPublishedAt())
                    .category(categoryHint != null ? categoryHint : "general")
                    .source(raw.getSourceName())
                    .cachedAt(now)
                    .build());
        }

        if (skipped > 0) Log.w(TAG, "Skipped " + skipped + " articles with no title.");
        Log.d(TAG, "Mapped " + result.size() + " valid articles.");
        return result;
    }

    /**
     * Produces a short, stable hex ID from a URL string using SHA-1.
     * Returns a truncated 16-char hex string (64-bit collision resistance —
     * sufficient for local DB keys).
     */
    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { // first 8 bytes = 16 hex chars
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback: use hashCode if SHA-1 unavailable (shouldn't happen on Android)
            return String.valueOf(url.hashCode());
        }
    }

    // ── Private — thread posting ──────────────────────────────────────────────

    private void postSuccess(NewsCallback callback, List<NewsArticle> articles) {
        mainHandler.post(() -> callback.onSuccess(articles));
    }

    private void postError(NewsCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Callback interface for all repository operations.
     * Methods are always invoked on the main (UI) thread.
     */
    public interface NewsCallback {
        /** Called when data is ready. List is never null but may be empty. */
        void onSuccess(List<NewsArticle> articles);
        /** Called when both cache and network fail. Show error UI. */
        void onError(String message);
    }
}
