package com.example.bit_stream_news.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * NewsApiResponse — Wrapper for NewsAPI.org JSON response.
 *
 * Response shape (newsapi.org):
 * {
 *   "status": "ok",
 *   "totalResults": 38,
 *   "articles": [
 *     {
 *       "source": { "id": "the-verge", "name": "The Verge" },
 *       "author": "John Doe",
 *       "title": "Article title here",
 *       "description": "Short summary of the article.",
 *       "url": "https://theverge.com/...",
 *       "urlToImage": "https://cdn.vox-cdn.com/...",
 *       "publishedAt": "2024-06-11T14:00:00Z",
 *       "content": "Full article text up to 200 chars [+1234 chars]"
 *     }
 *   ]
 * }
 *
 * All fields above are available on the free tier.
 * 'description' = short summary (always present).
 * 'content'     = truncated body (~200 chars + marker), still useful as a preview.
 */
public class NewsApiResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("totalResults")
    private int totalResults;

    /** Main articles array — used by both /top-headlines and /everything */
    @SerializedName("articles")
    private List<Article> articles;

    public String getStatus()      { return status; }
    public int getTotalResults()   { return totalResults; }

    /** Always returns a non-null list. */
    public List<Article> getArticles() {
        return articles != null ? articles : java.util.Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class Article {

        @SerializedName("title")
        private String title;

        /** Short summary — usually 1-2 sentences. Always filled by NewsAPI. */
        @SerializedName("description")
        private String description;

        /**
         * Article body preview (~200 chars) followed by "[+N chars]".
         * We strip the marker and use this as extra body text.
         */
        @SerializedName("content")
        private String content;

        /** Direct link to the article page. */
        @SerializedName("url")
        private String url;

        /** Hero image URL. */
        @SerializedName("urlToImage")
        private String urlToImage;

        /** ISO-8601 publish date e.g. "2024-06-11T14:00:00Z". */
        @SerializedName("publishedAt")
        private String publishedAt;

        /** Nested source object {"id": "...", "name": "..."}. */
        @SerializedName("source")
        private Source source;

        @SerializedName("author")
        private String author;

        // ── Getters ───────────────────────────────────────────────────────────

        public String getTitle()       { return title; }
        public String getDescription() { return description; }
        public String getContent()     { return content; }
        public String getUrl()         { return url; }
        public String getUrlToImage()  { return urlToImage; }
        public String getPublishedAt() { return publishedAt; }
        public String getAuthor()      { return author; }

        /** Article ID — NewsAPI has no dedicated ID, so we use URL hash in the Repository. */
        public String getArticleId()   { return url; }

        /**
         * Returns the best description text available:
         *   1. If description is present and non-empty → use it.
         *   2. If content is longer (after stripping truncation marker) → use content.
         *   3. Fallback: title.
         *
         * Strips "[+N chars]" markers that NewsAPI appends to content.
         */
        public String getBestDescription() {
            String d = cleanText(description);
            String c = cleanText(content);

            if (d == null && c == null) return null;
            if (d == null) return c;
            if (c == null) return d;
            // Prefer whichever is longer
            return c.length() > d.length() ? c : d;
        }

        /** Strips "[+NNNN chars]" suffix that NewsAPI appends to content field. */
        private String cleanText(String text) {
            if (text == null || text.trim().isEmpty()) return null;
            String cleaned = text.replaceAll("\\[\\+\\d+ chars\\]$", "").trim();
            return cleaned.isEmpty() ? null : cleaned;
        }

        /**
         * Returns source name.
         * NewsAPI always provides a nested source object.
         */
        public String getSourceName() {
            if (source != null && source.getName() != null && !source.getName().isEmpty()) {
                return source.getName();
            }
            if (author != null && !author.isEmpty()) return author;
            return "UNKNOWN";
        }

        // ── Nested source ─────────────────────────────────────────────────────

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
