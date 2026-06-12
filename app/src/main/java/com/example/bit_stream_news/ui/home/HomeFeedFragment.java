package com.example.bit_stream_news.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bit_stream_news.MainActivity;
import com.example.bit_stream_news.R;
import com.example.bit_stream_news.database.NewsDbHelper;
import com.example.bit_stream_news.model.NewsArticle;
import com.example.bit_stream_news.repository.NewsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

/**
 * HomeFeedFragment — Main news list screen.
 *
 * State machine:
 *   LOADING  → progress bar visible, RecyclerView gone, error gone
 *   SUCCESS  → RecyclerView visible, progress gone, error gone
 *   ERROR    → error layout visible, RecyclerView gone, progress gone
 *   EMPTY    → error layout visible with custom message (no articles)
 *
 * Threading: All repository calls are dispatched on a background Executor
 * inside NewsRepository. Callbacks arrive on the main thread via Handler,
 * so it is safe to update Views directly in onSuccess / onError.
 *
 * Search: delegates to NewsAdapter.filter() — purely in-memory,
 * no extra API or DB call triggered.
 *
 * Category chips: stored as TextView IDs mapped to category strings.
 * Clicking a chip calls NewsRepository.getNewsByCategory() (SQLite only).
 */
public class HomeFeedFragment extends Fragment
        implements NewsAdapter.OnArticleClickListener,
                   NewsAdapter.OnBookmarkClickListener {

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView  rvNews;
    private View          layoutLoading;
    private View          layoutError;
    private ProgressBar   progressLoading;
    private EditText      etSearch;
    private View          btnRefresh;
    private View          btnRetry;

    // ── Category chip IDs → category strings ─────────────────────────────────

    private static final int[] CHIP_IDS = {
            R.id.chip_all, R.id.chip_world, R.id.chip_tech,
            R.id.chip_games, R.id.chip_vibe, R.id.chip_business
    };
    private static final String[] CHIP_CATEGORIES = {
            "all", "world", "technology", "entertainment", "general", "business"
    };

    // ── State ─────────────────────────────────────────────────────────────────

    private NewsAdapter    adapter;
    private NewsRepository repository;
    private NewsDbHelper   dbHelper;
    private ExecutorService bookmarkExecutor;
    private Handler         mainHandler;
    private String          activeCategory = "all";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository       = NewsRepository.getInstance(requireContext());
        dbHelper         = NewsDbHelper.getInstance(requireContext());
        bookmarkExecutor = Executors.newSingleThreadExecutor();
        mainHandler      = new Handler(Looper.getMainLooper());

        bindViews(view);
        setupRecyclerView();
        setupSearchBar();
        setupCategoryChips(view);
        setupButtons();

        // Initial load: cache-first
        loadNews();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bookmarkExecutor != null && !bookmarkExecutor.isShutdown()) bookmarkExecutor.shutdown();
        rvNews        = null;
        layoutLoading = null;
        layoutError   = null;
        progressLoading = null;
        etSearch      = null;
        btnRefresh    = null;
        btnRetry      = null;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        rvNews          = root.findViewById(R.id.rv_news);
        layoutLoading   = root.findViewById(R.id.layout_loading);
        layoutError     = root.findViewById(R.id.layout_error);
        progressLoading = root.findViewById(R.id.progress_loading);
        etSearch        = root.findViewById(R.id.et_search);
        btnRefresh      = root.findViewById(R.id.btn_refresh);
        btnRetry        = root.findViewById(R.id.btn_retry);
    }

    private void setupRecyclerView() {
        adapter = new NewsAdapter(this);
        adapter.setBookmarkListener(this); // enable [★] button
        if (rvNews != null) {
            rvNews.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvNews.setAdapter(adapter);
            rvNews.setHasFixedSize(false);
        }
    }

    private void setupSearchBar() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Pure in-memory filter — no API call
                adapter.filter(s.toString());
            }
        });
    }

    private void setupCategoryChips(View root) {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            View chip = root.findViewById(CHIP_IDS[i]);
            if (chip == null) continue;

            final String category = CHIP_CATEGORIES[i];
            chip.setOnClickListener(v -> onCategorySelected(category));
        }
    }

    private void setupButtons() {
        // Toolbar refresh button — calls API regardless of cache
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> refreshNews());
        }

        // Error state RETRY button — same as refresh
        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> refreshNews());
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Cache-first load. Shows SQLite data immediately; only calls API
     * if the cache is empty (first launch).
     */
    private void loadNews() {
        showLoading();
        repository.getNews(new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<NewsArticle> articles) {
                if (!isAdded()) return; // Fragment detached
                if (articles.isEmpty()) {
                    showError("NO_DATA_FOUND", "// DATABASE RETURNED EMPTY RESULT SET");
                } else {
                    showContent(articles);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showError("CONNECTION_LOST", message);
            }
        });
    }

    /**
     * Force API refresh — only called when the user explicitly taps
     * the Refresh or RETRY button. Consumes 1 API request.
     */
    private void refreshNews() {
        showLoading();
        repository.refreshNews(new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<NewsArticle> articles) {
                if (!isAdded()) return;
                if (articles.isEmpty()) {
                    showError("NO_DATA_FOUND", "// SERVER RETURNED EMPTY RESULT SET");
                } else {
                    showContent(articles);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showError("NETWORK_FAIL", message);
            }
        });
    }

    /**
     * Filter by category from SQLite — no API call.
     */
    private void onCategorySelected(String category) {
        this.activeCategory = category;
        showLoading();
        repository.getNewsByCategory(category, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<NewsArticle> articles) {
                if (!isAdded()) return;
                showContent(articles);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showError("FILTER_FAIL", message);
            }
        });
    }

    // ── State transitions ─────────────────────────────────────────────────────

    private void showLoading() {
        setVisibility(layoutLoading, true);
        setVisibility(rvNews,        false);
        setVisibility(layoutError,   false);
    }

    private void showContent(List<NewsArticle> articles) {
        setVisibility(layoutLoading, false);
        setVisibility(rvNews,        true);
        setVisibility(layoutError,   false);
        adapter.setArticles(articles);
    }

    private void showError(String title, String detail) {
        setVisibility(layoutLoading, false);
        setVisibility(rvNews,        false);
        setVisibility(layoutError,   true);

        // Update error state text if the views are accessible through include
        if (layoutError != null) {
            TextView tvTitle  = layoutError.findViewById(R.id.tv_error_title);
            TextView tvDetail = layoutError.findViewById(R.id.tv_error_detail);
            if (tvTitle  != null) tvTitle.setText(title);
            if (tvDetail != null) tvDetail.setText(detail);
        }
    }

    private void setVisibility(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ── NewsAdapter.OnArticleClickListener ────────────────────────────────────

    @Override
    public void onArticleClick(NewsArticle article) {
        Context ctx = getContext();
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).launchNewsDetail(article);
        }
    }

    // ── NewsAdapter.OnBookmarkClickListener ───────────────────────────────────

    @Override
    public void onBookmarkClick(NewsArticle article, boolean newState) {
        // Run DB operation on background executor, not main thread
        bookmarkExecutor.execute(() -> {
            if (newState) {
                dbHelper.bookmarkArticle(article.getId());
            } else {
                dbHelper.removeBookmark(article.getId());
            }
        });
    }
}
