package com.example.bit_stream_news.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * NewsApiResponse — top-level wrapper returned by the RapidAPI news endpoint.
 *
 * ACTUAL response from "Real-Time News Data" API (real-time-news-data.p.rapidapi.com):
 * {
 *   "status": "OK",
 *   "request_id": "xxx",
 *   "data": [
 *     {
 *       "article_id": "xxx",
 *       "title": "xxx",
 *       "link": "https://...",        ← URL field is "link", NOT "url"
 *       "description": "xxx",
 *       "content": "xxx",
 *       "pubDate": "2024-06-08 ...",  ← date field is "pubDate"
 *       "image_url": "https://...",   ← image field is "image_url"
 *       "source_id": "xxx",
 *       "source_name": "Reuters",     ← source is flat string, NOT nested object
 *       "source_url": "https://...",
 *       "category": ["technology"],
 *       "country": ["us"],
 *       "language": "english"
 *     }
 *   ]
 * }
 *
 * Also handles NewsAPI.org style (articles array, url, source.name) for fallback.
 */
public class NewsApiResponse {

    // ── Root level ────────────────────────────────────────────────────────────

    @SerializedName("status")
    private String status;

    @SerializedName("totalResults")
    private int totalResults;

    /** NewsAPI.org style — articles array */
    @SerializedName("articles")
    private List<Article> articles;

    /** Real-Time News Data style — data array */
    @SerializedName("data")
    private List<Article> data;

    @SerializedName("success")
    private boolean success;

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getStatus()       { return status; }
    public int getTotalResults()    { return totalResults; }
    public boolean isSuccess()      { return success; }

    /**
     * Returns whichever list is populated.
     * Real-Time News Data API → uses "data"
     * NewsAPI.org             → uses "articles"
     */
    public List<Article> getArticles() {
        if (data     != null && !data.isEmpty())     return data;
        if (articles != null && !articles.isEmpty()) return articles;
        return java.util.Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class Article {

        @SerializedName("title")
        private String title;

        /**
         * Short description / excerpt.
         * Real-Time News Data: "description" (may be short/truncated)
         * NewsAPI.org:         "description"
         */
        @SerializedName(value = "description", alternate = {"excerpt", "summary"})
        private String description;

        /**
         * Full article content — often longer than description.
         * Real-Time News Data: "full_description" or "content"
         * We store this separately and pick the longer one for display.
         */
        @SerializedName(value = "full_description", alternate = {"content", "body", "text"})
        private String content;

        /**
         * FIXED: Real-Time News Data uses "link", NOT "url".
         * NewsAPI.org uses "url".
         * Both are listed as alternates.
         */
        @SerializedName(value = "link", alternate = {"url", "article_url", "href"})
        private String url;

        /**
         * Image URL field.
         * Real-Time News Data: "image_url"
         * NewsAPI.org:         "urlToImage"
         */
        @SerializedName(value = "image_url", alternate = {"urlToImage", "image", "thumbnail", "photo_url"})
        private String urlToImage;

        /**
         * Publish date.
         * Real-Time News Data: "pubDate"  e.g. "2024-06-08 14:30:00"
         * NewsAPI.org:         "publishedAt" e.g. "2024-06-08T14:30:00Z"
         */
        @SerializedName(value = "pubDate", alternate = {"publishedAt", "date", "published_at", "pub_date"})
        private String publishedAt;

        /**
         * FIXED: Real-Time News Data returns source as flat string "source_name",
         * NOT as a nested {id, name} object.
         */
        @SerializedName(value = "source_name", alternate = {"publisher", "author", "source_id"})
        private String sourceName;

        /** NewsAPI.org style — nested source object. */
        @SerializedName("source")
        private Source source;

        /** Article ID from the API (used as stable key if available). */
        @SerializedName(value = "article_id", alternate = {"id", "uuid"})
        private String articleId;

        // ── Getters ───────────────────────────────────────────────────────────

        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getContent()     { return content; }
        public String getUrl()         { return url; }
        public String getUrlToImage()  { return urlToImage; }
        public String getPublishedAt() { return publishedAt; }
        public String getArticleId()   { return articleId; }

        /**
         * Returns the best available description text.
         * Picks the longer of description vs content — whichever has
         * more actual text. Falls back to title if both are null.
         * Strips the "[+N chars]" truncation marker that some APIs append.
         */
        public String getBestDescription() {
            String d = cleanText(description);
            String c = cleanText(content);

            if (d == null && c == null) return null;
            if (d == null) return c;
            if (c == null) return d;
            // Return whichever is longer
            return c.length() > d.length() ? c : d;
        }

        /** Strips API-specific truncation markers like "[+2345 chars]". */
        private String cleanText(String text) {
            if (text == null || text.trim().isEmpty()) return null;
            // Remove "[+NNNN chars]" suffix
            String cleaned = text.replaceAll("\\[\\+\\d+ chars\\]$", "").trim();
            return cleaned.isEmpty() ? null : cleaned;
        }

        /**
         * Returns the source/publisher name.
         * Tries: flat source_name → nested source.name → fallback.
         */
        public String getSourceName() {
            if (sourceName != null && !sourceName.isEmpty()) return sourceName;
            if (source != null && source.getName() != null)  return source.getName();
            return "UNKNOWN";
        }

        // ── Nested Source (NewsAPI.org style) ─────────────────────────────────

        public static class Source {
            @SerializedName("id")
            private String id;

            @SerializedName("name")
            private String name;

            public String getId()   { return id; }
            public String getName() { return name; }
        }
    }
}
