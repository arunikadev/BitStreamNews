package com.example.bit_stream_news.network;

import com.example.bit_stream_news.model.NewsApiResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * NewsApiService — Retrofit interface for Real-Time News Data API.
 *
 * Base URL (set in ApiClient): https://real-time-news-data.p.rapidapi.com/
 *
 * Actual endpoint reference for this API:
 *   GET /top-headlines
 *     ?language=en          — ISO 639-1 language code
 *     &country=US           — ISO 3166-1 alpha-2 country code (UPPERCASE)
 *     &limit=20             — max results (default 50, we use 20 to save quota)
 *
 *   GET /top-headlines
 *     ?language=en
 *     &topic=technology     — topic filter: technology|world|business|science|health|sports|entertainment|breaking-news|nation
 *     &limit=20
 *
 *   GET /search
 *     ?query=keyword        — search terms
 *     &language=en
 *     &limit=20
 *
 * Auth headers (x-rapidapi-key, x-rapidapi-host) are injected by ApiClient's OkHttp interceptor.
 * Response structure: { "status": "OK", "data": [ { "title", "link", "source_name", ... } ] }
 */
public interface NewsApiService {

    /**
     * Top headlines — no category filter.
     * Uses 'country' param to get localised results.
     */
    @GET("top-headlines")
    Call<NewsApiResponse> getTopHeadlines(
            @Query("language") String language,
            @Query("country")  String country,
            @Query("limit")    int    limit
    );

    /**
     * Top headlines filtered by topic/category.
     * The 'topic' param replaces 'country' when filtering by category.
     * Accepted topics: technology | world | business | science | health |
     *                  sports | entertainment | breaking-news | nation
     */
    @GET("top-headlines")
    Call<NewsApiResponse> getNewsByCategory(
            @Query("language") String language,
            @Query("topic")    String topic,
            @Query("limit")    int    limit
    );

    /**
     * Free-text search.
     */
    @GET("search")
    Call<NewsApiResponse> searchNews(
            @Query("query")    String query,
            @Query("language") String language,
            @Query("limit")    int    limit
    );
}
