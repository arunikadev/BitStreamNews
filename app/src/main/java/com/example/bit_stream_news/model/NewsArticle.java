package com.example.bit_stream_news.model;

/**
 * NewsArticle — Plain Old Java Object representing a single news article.
 *
 * Fields are mapped from:
 *   - RapidAPI JSON response  (title, url, description, urlToImage, publishedAt, source.name)
 *   - SQLite local cache table (all fields, plus cachedAt timestamp)
 *
 * The `id` is derived as a hash of the article URL so it works both as a
 * primary key in SQLite and as a stable identity key for DiffUtil.
 *
 * Immutable by design — use the Builder to construct instances when parsing
 * from JSON or reading from the database.
 */
public class NewsArticle {

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Stable unique ID: SHA-1 hash of {@link #url}, or raw API id if provided. */
    private String id;

    /** Headline text of the article. */
    private String title;

    /** Short description / excerpt. May be null if the API omits it. */
    private String description;

    /** Canonical URL to the full article on the source website. */
    private String url;

    /** URL of the article thumbnail / hero image. May be null. */
    private String imageUrl;

    /** ISO-8601 publication timestamp, e.g. "2024-05-22T14:42:00Z". */
    private String publishedAt;

    /**
     * Category string assigned locally (e.g. "technology", "world").
     * Stored in SQLite so we can filter without re-fetching from the API.
     */
    private String category;

    /** Source / publisher name, e.g. "Reuters". */
    private String source;

    /**
     * Unix epoch millis recording when this row was written to SQLite.
     * Used by {@link com.example.bit_stream_news.database.NewsDbHelper#clearOldCache()}
     * to evict stale entries.
     */
    private long cachedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-arg constructor required by Gson and for manual field assignment. */
    public NewsArticle() {}

    /** Full constructor used by the Builder. */
    public NewsArticle(String id, String title, String description, String url,
                       String imageUrl, String publishedAt, String category,
                       String source, long cachedAt) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.url         = url;
        this.imageUrl    = imageUrl;
        this.publishedAt = publishedAt;
        this.category    = category;
        this.source      = source;
        this.cachedAt    = cachedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public String getUrl()         { return url; }
    public String getImageUrl()    { return imageUrl; }
    public String getPublishedAt() { return publishedAt; }
    public String getCategory()    { return category; }
    public String getSource()      { return source; }
    public long   getCachedAt()    { return cachedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id)                   { this.id = id; }
    public void setTitle(String title)             { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setUrl(String url)                 { this.url = url; }
    public void setImageUrl(String imageUrl)       { this.imageUrl = imageUrl; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public void setCategory(String category)       { this.category = category; }
    public void setSource(String source)           { this.source = source; }
    public void setCachedAt(long cachedAt)         { this.cachedAt = cachedAt; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String title;
        private String description;
        private String url;
        private String imageUrl;
        private String publishedAt;
        private String category;
        private String source;
        private long   cachedAt = System.currentTimeMillis();

        public Builder id(String id)                   { this.id = id;                   return this; }
        public Builder title(String title)             { this.title = title;             return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder url(String url)                 { this.url = url;                 return this; }
        public Builder imageUrl(String imageUrl)       { this.imageUrl = imageUrl;       return this; }
        public Builder publishedAt(String publishedAt) { this.publishedAt = publishedAt; return this; }
        public Builder category(String category)       { this.category = category;       return this; }
        public Builder source(String source)           { this.source = source;           return this; }
        public Builder cachedAt(long cachedAt)         { this.cachedAt = cachedAt;       return this; }

        public NewsArticle build() {
            return new NewsArticle(id, title, description, url, imageUrl,
                                   publishedAt, category, source, cachedAt);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "NewsArticle{id='" + id + "', title='" + title + "', source='" + source + "'}";
    }
}
