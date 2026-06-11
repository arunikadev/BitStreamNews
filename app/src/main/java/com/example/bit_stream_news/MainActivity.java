package com.example.bit_stream_news;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.bit_stream_news.model.NewsArticle;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * MainActivity — Shell activity hosting the Navigation Component.
 *
 * Responsibilities:
 *   1. Read saved theme preference from SharedPreferences and apply it
 *      via AppCompatDelegate BEFORE setContentView (required by Material3).
 *   2. Inflate activity_main.xml which contains NavHostFragment + BottomNav.
 *   3. Wire NavController ↔ BottomNavigationView via NavigationUI.
 *   4. Expose a helper method launchNewsDetail() for fragments to start
 *      NewsDetailActivity without needing a direct Activity reference.
 *
 * Theme persistence key:  SharedPreferences "app_prefs" → "theme_mode"
 * Values: "dark" → MODE_NIGHT_YES  |  anything else → MODE_NIGHT_NO
 */
public class MainActivity extends AppCompatActivity {

    // ── Prefs constants (kept in sync with SettingsFragment) ─────────────────
    public static final String PREFS_NAME  = "app_prefs";
    public static final String KEY_THEME   = "theme_mode";
    public static final String THEME_DARK  = "dark";
    public static final String THEME_LIGHT = "light";

    // ── Intent extras (for fragments to build their Intent) ───────────────────
    public static final String EXTRA_ARTICLE_ID          = "article_id";
    public static final String EXTRA_ARTICLE_TITLE       = "article_title";
    public static final String EXTRA_ARTICLE_DESCRIPTION = "article_description";
    public static final String EXTRA_ARTICLE_URL         = "article_url";
    public static final String EXTRA_ARTICLE_IMAGE_URL   = "article_image_url";
    public static final String EXTRA_ARTICLE_SOURCE      = "article_source";
    public static final String EXTRA_ARTICLE_PUBLISHED   = "article_published_at";
    public static final String EXTRA_ARTICLE_CATEGORY    = "article_category";

    // ── State ─────────────────────────────────────────────────────────────────

    private NavController navController;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ① Apply theme BEFORE super.onCreate / setContentView
        applyThemeFromPrefs();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupNavigation();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    /**
     * Reads "theme_mode" from SharedPreferences and calls
     * AppCompatDelegate.setDefaultNightMode() before any UI is inflated.
     * Must be called as the very first thing in onCreate.
     */
    private void applyThemeFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_LIGHT);
        if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void setupNavigation() {
        // Obtain NavController from the NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            throw new IllegalStateException(
                    "NavHostFragment not found. Check activity_main.xml for id=nav_host_fragment");
        }

        navController = navHostFragment.getNavController();

        // Wire BottomNavigationView to NavController
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    /**
     * Launches NewsDetailActivity with all article fields as Intent extras.
     * Called by HomeFeedFragment when a news card is tapped.
     *
     * @param article the article to display in the detail screen.
     */
    public void launchNewsDetail(NewsArticle article) {
        Intent intent = new Intent(this, NewsDetailActivity.class);
        intent.putExtra(EXTRA_ARTICLE_ID,          article.getId());
        intent.putExtra(EXTRA_ARTICLE_TITLE,       article.getTitle());
        intent.putExtra(EXTRA_ARTICLE_DESCRIPTION, article.getDescription());
        intent.putExtra(EXTRA_ARTICLE_URL,         article.getUrl());
        intent.putExtra(EXTRA_ARTICLE_IMAGE_URL,   article.getImageUrl());
        intent.putExtra(EXTRA_ARTICLE_SOURCE,      article.getSource());
        intent.putExtra(EXTRA_ARTICLE_PUBLISHED,   article.getPublishedAt());
        intent.putExtra(EXTRA_ARTICLE_CATEGORY,    article.getCategory());
        startActivity(intent);
        // Pixel slide-in: enter from right
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}