package com.example.bit_stream_news.network;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * ApiClient — Thread-safe Retrofit singleton.
 *
 * Provides a single shared {@link NewsApiService} instance for the entire app.
 * All HTTP headers required by RapidAPI are injected by the OkHttp interceptor
 * so that individual endpoint methods stay clean.
 *
 * IMPORTANT — API key is embedded here for now.
 * In production, move it to local.properties → BuildConfig to keep it
 * out of version control.
 *
 * BASE URL: https://real-time-news-data.p.rapidapi.com/
 * (Trailing slash is required by Retrofit.)
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // ── Config ────────────────────────────────────────────────────────────────

    public static final String BASE_URL =
            "https://real-time-news-data.p.rapidapi.com/";

    /** RapidAPI host for the "Real-Time News Data" API. */
    private static final String RAPID_API_HOST =
            "real-time-news-data.p.rapidapi.com";

    /**
     * Your RapidAPI key. Quota: 100 req/month — treat this carefully.
     * Move to BuildConfig for production: BuildConfig.RAPID_API_KEY
     */
    private static final String RAPID_API_KEY =
            "c83a05807bmsh4eb712db1696341p185a14jsnbead526a1a8d";

    // ── Timeouts ──────────────────────────────────────────────────────────────

    private static final int CONNECT_TIMEOUT_S = 15;
    private static final int READ_TIMEOUT_S    = 20;
    private static final int WRITE_TIMEOUT_S   = 15;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile NewsApiService sInstance;
    private static volatile Retrofit       sRetrofit;

    /** Returns the shared {@link NewsApiService} instance (lazy-init, thread-safe). */
    public static NewsApiService getInstance() {
        if (sInstance == null) {
            synchronized (ApiClient.class) {
                if (sInstance == null) {
                    sRetrofit  = buildRetrofit();
                    sInstance  = sRetrofit.create(NewsApiService.class);
                    Log.d(TAG, "ApiClient initialised. Base URL: " + BASE_URL);
                }
            }
        }
        return sInstance;
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private static Retrofit buildRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(buildOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static OkHttpClient buildOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S,        TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S,      TimeUnit.SECONDS)
                // RapidAPI auth headers — injected on every request
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request  = original.newBuilder()
                            .header("x-rapidapi-key",  RAPID_API_KEY)
                            .header("x-rapidapi-host", RAPID_API_HOST)
                            .header("Accept",          "application/json")
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(request);
                });

        // HTTP request/response logging in DEBUG builds only
        // In release, the logger is omitted to avoid leaking the API key in logcat
        if (isDebugBuild()) {
            HttpLoggingInterceptor logger = new HttpLoggingInterceptor(
                    message -> Log.d(TAG, message));
            logger.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logger);
        }

        return builder.build();
    }

    /**
     * Heuristic: checks if the BuildConfig.DEBUG flag is true.
     * Wrapped in try/catch in case it's called before Application starts.
     */
    private static boolean isDebugBuild() {
        try {
            Class<?> buildConfig = Class.forName(
                    "com.example.bit_stream_news.BuildConfig");
            return (boolean) buildConfig.getField("DEBUG").get(null);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Prevent instantiation ─────────────────────────────────────────────────

    private ApiClient() {}
}
