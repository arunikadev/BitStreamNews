package com.example.bit_stream_news.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * ApiClient — Retrofit singleton for NewsAPI.org
 *
 * Base URL : https://newsapi.org/v2/
 * Auth     : Header  X-Api-Key: <key>
 *
 * Key is injected by an OkHttp interceptor — never in the URL or
 * hardcoded in the endpoint interface.
 *
 * Free tier limits:
 *  - 100 requests / day
 *  - Delayed content (~24h behind live)
 *  - No commercial use
 */
public class ApiClient {

    private static final String BASE_URL   = "https://newsapi.org/v2/";
    private static final String API_KEY    = "a3d98efb39664e5a85180713917fea7b";
    private static final int    TIMEOUT_S  = 20;

    private static volatile NewsApiService sInstance;

    public static NewsApiService getInstance() {
        if (sInstance == null) {
            synchronized (ApiClient.class) {
                if (sInstance == null) {
                    sInstance = buildRetrofit().create(NewsApiService.class);
                }
            }
        }
        return sInstance;
    }

    private static Retrofit buildRetrofit() {
        // Logging interceptor (debug builds only)
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                // Inject API key as header on every request
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .addHeader("X-Api-Key", API_KEY)
                                .build()))
                .addInterceptor(logging)
                .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
