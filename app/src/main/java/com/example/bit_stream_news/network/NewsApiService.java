package com.example.bit_stream_news.network;

import com.example.bit_stream_news.model.NewsApiResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * NewsApiService — Retrofit interface for NewsAPI.org
 *
 * Base URL: https://newsapi.org/v2/
 * Auth    : X-Api-Key header (injected by ApiClient interceptor)
 *
 * ── Endpoints ─────────────────────────────────────────────────────────────
 *
 * GET /top-headlines
 *   Returns breaking news for a country or category.
 *   Params:
 *     country  — 2-letter ISO code e.g. "us", "id"
 *     category — business | entertainment | general | health |
 *                science | sports | technology
 *     pageSize — max articles (1-100, default 20)
 *
 * GET /everything
 *   Full article search across all sources.
 *   Params:
 *     q        — keyword (required)
 *     language — en | id | etc.
 *     sortBy   — publishedAt | relevancy | popularity
 *     pageSize — max articles (1-100, default 20)
 *
 * Response fields (all available on free tier):
 *   title, description, content, url, urlToImage, publishedAt,
 *   source.id, source.name, author
 */
public interface NewsApiService {

    /**
     * Top headlines — general, no category filter.
     * Best for the Home Feed (latest news).
     */
    @GET("top-headlines")
    Call<NewsApiResponse> getTopHeadlines(
            @Query("country")  String country,
            @Query("pageSize") int    pageSize
    );

    /**
     * Top headlines filtered by category.
     * Used by CategoryFragment to fetch news for a specific topic.
     * Valid categories: business | entertainment | general | health |
     *                   science | sports | technology
     */
    @GET("top-headlines")
    Call<NewsApiResponse> getNewsByCategory(
            @Query("country")  String country,
            @Query("category") String category,
            @Query("pageSize") int    pageSize
    );

    /**
     * Full-text search across all sources.
     * Used when the user explicitly searches by keyword.
     */
    @GET("everything")
    Call<NewsApiResponse> searchNews(
            @Query("q")        String query,
            @Query("language") String language,
            @Query("sortBy")   String sortBy,
            @Query("pageSize") int    pageSize
    );
}
