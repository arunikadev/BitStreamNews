package com.example.bit_stream_news;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * NewsDetailActivity — Full article detail screen.
 *
 * Receives article data via Intent extras (put by MainActivity.launchNewsDetail).
 * Does NOT call the repository or network — all data is carried via the Intent.
 *
 * Features:
 *  - Loads hero image with Glide (placeholder + error fallback)
 *  - Displays title, description, source, published date
 *  - Back button navigates up (or back-slides to HomeFeedFragment)
 *  - Share button fires ACTION_SEND with the article URL
 *  - "Read Full Article" button opens article URL in browser via ACTION_VIEW
 */
public class NewsDetailActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────

    private ImageView ivDetailImage;
    private TextView  tvCategoryBadge;
    private TextView  tvPublishedAt;
    private TextView  tvDetailTitle;
    private TextView  tvDetailDescription;
    private TextView  tvSourceName;
    private View      btnBack;
    private View      btnShareToolbar;
    private View      btnShare;
    private View      btnReadFull;
    private View      tvImageCaption;

    // ── Article data ──────────────────────────────────────────────────────────

    private String articleTitle;
    private String articleDescription;
    private String articleUrl;
    private String articleImageUrl;
    private String articleSource;
    private String articlePublishedAt;
    private String articleCategory;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_detail);

        extractExtras();
        bindViews();
        populateViews();
        setupClickListeners();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Pixel slide-out to the right (back gesture)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void extractExtras() {
        Intent intent = getIntent();
        articleTitle       = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_TITLE);
        articleDescription = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_DESCRIPTION);
        articleUrl         = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_URL);
        articleImageUrl    = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_IMAGE_URL);
        articleSource      = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_SOURCE);
        articlePublishedAt = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_PUBLISHED);
        articleCategory    = intent.getStringExtra(MainActivity.EXTRA_ARTICLE_CATEGORY);
    }

    private void bindViews() {
        ivDetailImage       = findViewById(R.id.iv_detail_image);
        tvCategoryBadge     = findViewById(R.id.tv_category_badge);
        tvPublishedAt       = findViewById(R.id.tv_published_at);
        tvDetailTitle       = findViewById(R.id.tv_detail_title);
        tvDetailDescription = findViewById(R.id.tv_detail_description);
        tvSourceName        = findViewById(R.id.tv_source_name);
        btnBack             = findViewById(R.id.btn_back);
        btnShareToolbar     = findViewById(R.id.btn_share_toolbar);
        btnShare            = findViewById(R.id.btn_share);
        btnReadFull         = findViewById(R.id.btn_read_full);
    }

    private void populateViews() {
        // Category badge
        if (tvCategoryBadge != null && articleCategory != null) {
            tvCategoryBadge.setText(articleCategory.toUpperCase());
        }

        // Timestamp
        if (tvPublishedAt != null) {
            tvPublishedAt.setText(formatTimestamp(articlePublishedAt));
        }

        // Title
        if (tvDetailTitle != null && articleTitle != null) {
            tvDetailTitle.setText(articleTitle.toUpperCase());
        }

        // Description / body
        if (tvDetailDescription != null) {
            String body = (articleDescription != null && !articleDescription.isEmpty())
                    ? articleDescription
                    : getString(R.string.detail_image_placeholder);
            tvDetailDescription.setText(body);
        }

        // Source
        if (tvSourceName != null && articleSource != null) {
            tvSourceName.setText(articleSource.toUpperCase());
        }

        // Hero image via Glide
        if (ivDetailImage != null) {
            if (articleImageUrl != null && !articleImageUrl.isEmpty()) {
                Glide.with(this)
                        .load(articleImageUrl)
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .centerCrop()
                        .into(ivDetailImage);
            } else {
                ivDetailImage.setImageResource(android.R.color.darker_gray);
            }
        }
    }

    private void setupClickListeners() {
        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                onBackPressed();
            });
        }

        // Share (toolbar icon)
        if (btnShareToolbar != null) {
            btnShareToolbar.setOnClickListener(v -> shareArticle());
        }

        // Share (body button)
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareArticle());
        }

        // Read Full Article → open in browser
        if (btnReadFull != null) {
            btnReadFull.setOnClickListener(v -> openInBrowser());
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Fires an ACTION_SEND chooser so the user can share the article URL
     * via any installed app (WhatsApp, Telegram, clipboard, etc.)
     */
    private void shareArticle() {
        if (articleUrl == null || articleUrl.isEmpty()) return;

        String shareText = (articleTitle != null ? articleTitle + "\n\n" : "")
                + articleUrl;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, articleTitle);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(
                shareIntent,
                getString(R.string.detail_share)));
    }

    /**
     * Opens the full article URL in the device's default browser
     * via an implicit Intent with ACTION_VIEW.
     */
    private void openInBrowser() {
        if (articleUrl == null || articleUrl.isEmpty()) return;
        Intent browserIntent = new Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(articleUrl));
        startActivity(browserIntent);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Formats a datetime string for display.
     *
     * Handles two formats:
     *   ISO-8601:          "2024-06-08T14:42:00Z"   → "[2024.06.08_14:42_UTC]"
     *   Real-Time News Data: "2024-06-08 14:42:00"  → "[2024.06.08_14:42]"
     *
     * Falls back gracefully on any parse error.
     */
    private String formatTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "[UNKNOWN_DATE]";
        try {
            String separator = raw.contains("T") ? "T" : " ";
            String[] parts = raw.split(separator);
            String date = parts[0].replace("-", ".");
            String time = "00:00";
            if (parts.length > 1 && parts[1].length() >= 5) {
                time = parts[1].substring(0, 5); // "HH:mm"
            }
            String tz = separator.equals("T") ? "_UTC" : "";
            return "[" + date + "_" + time + tz + "]";
        } catch (Exception e) {
            return "[" + raw + "]";
        }
    }
}
