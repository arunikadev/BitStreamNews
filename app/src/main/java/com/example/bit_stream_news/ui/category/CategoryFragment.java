package com.example.bit_stream_news.ui.category;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bit_stream_news.MainActivity;
import com.example.bit_stream_news.R;
import com.example.bit_stream_news.model.NewsArticle;
import com.example.bit_stream_news.repository.NewsRepository;
import com.example.bit_stream_news.ui.home.NewsAdapter;

import java.util.Arrays;
import java.util.List;

/**
 * CategoryFragment — "Level Select" category browse screen.
 *
 * Two display modes in a single RecyclerView:
 *
 *   MODE_GRID — 2-column grid of category tiles (CategoryAdapter).
 *               Shown on first entry and when the user presses Back.
 *
 *   MODE_LIST — Single-column list of news articles (NewsAdapter).
 *               Shown after the user taps a category tile.
 *               Loads from SQLite. Because all articles are cached as
 *               category="general" on initial load, we fall back to
 *               showing the full cache when no specific-category match
 *               is found, rather than silently returning to the grid.
 */
public class CategoryFragment extends Fragment
        implements CategoryAdapter.OnCategoryClickListener,
                   NewsAdapter.OnArticleClickListener {

    // ── Display modes ─────────────────────────────────────────────────────────

    private static final int MODE_GRID = 0;
    private static final int MODE_LIST = 1;

    // ── Category definitions ──────────────────────────────────────────────────

    private static final List<CategoryAdapter.Category> CATEGORIES = Arrays.asList(
        new CategoryAdapter.Category("[G]", "WORLD",   "Global data stream",      "general",       "LVL 01"),
        new CategoryAdapter.Category("[#]", "TECH",    "Silicon & software logs", "technology",    "LVL 02"),
        new CategoryAdapter.Category("[$]", "FINANCE", "Market volatility data",  "business",      "LVL 03"),
        new CategoryAdapter.Category("[S]", "SCIENCE", "Discovery & lab reports", "science",       "LVL 04"),
        new CategoryAdapter.Category("[H]", "HEALTH",  "Bio-integrity updates",   "health",        "LVL 05"),
        new CategoryAdapter.Category("[L]", "CYBER",   "Security breach alerts",  "technology",    "LVL 06"),
        new CategoryAdapter.Category("[A]", "CULTURE", "Art & societal shifts",   "entertainment", "LVL 07"),
        new CategoryAdapter.Category("[R]", "SPORTS",  "Performance metrics",     "sports",        "LVL 08")
    );

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView rvCategories;
    private View         layoutLoading;
    private View         layoutError;
    private TextView     tvCategoryHeader;   // shows selected category name above list

    // ── Adapters ──────────────────────────────────────────────────────────────

    private CategoryAdapter categoryAdapter;
    private NewsAdapter     newsAdapter;

    // ── State ─────────────────────────────────────────────────────────────────

    private int    currentMode    = MODE_GRID;
    private String activeCategory = null;
    private NewsRepository repository;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = NewsRepository.getInstance(requireContext());

        rvCategories     = view.findViewById(R.id.rv_categories);
        layoutLoading    = view.findViewById(R.id.layout_loading);
        layoutError      = view.findViewById(R.id.layout_error);
        tvCategoryHeader = view.findViewById(R.id.tv_category_section_header);

        categoryAdapter = new CategoryAdapter(CATEGORIES, this);
        newsAdapter     = new NewsAdapter(this);

        showGrid();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvCategories     = null;
        layoutLoading    = null;
        layoutError      = null;
        tvCategoryHeader = null;
    }

    // ── Mode switching ────────────────────────────────────────────────────────

    /** Show the 2-column category tile grid. */
    private void showGrid() {
        currentMode = MODE_GRID;
        if (rvCategories == null) return;

        setVisibility(tvCategoryHeader, false);
        setVisibility(layoutLoading,   false);
        setVisibility(layoutError,     false);
        setVisibility(rvCategories,    true);

        rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvCategories.setAdapter(categoryAdapter);
    }

    /**
     * Show articles for the selected category.
     *
     * Strategy (quota-safe, no extra API calls):
     *  1. Query SQLite for articles where category = topic.
     *  2. If matches found → show them.
     *  3. If NO matches (articles were stored as "general") → show ALL cached
     *     articles with the category label as header. This gives the user
     *     something to see instead of silently returning to the grid.
     */
    private void showCategoryArticles(String topic, String displayName) {
        currentMode    = MODE_LIST;
        activeCategory = topic;

        setVisibility(layoutLoading,   true);
        setVisibility(rvCategories,    false);
        setVisibility(layoutError,     false);
        setVisibility(tvCategoryHeader, false);

        repository.getNewsByCategory(topic, new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<NewsArticle> articles) {
                if (!isAdded() || rvCategories == null) return;

                if (articles.isEmpty()) {
                    // No specific-category match in cache.
                    // Fall back to ALL cached articles rather than going back to grid.
                    loadAllArticlesAsFallback(displayName);
                    return;
                }

                // Found category-specific articles
                showArticleList(articles, displayName);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                setVisibility(layoutLoading, false);
                setVisibility(layoutError,   true);
                setVisibility(rvCategories,  false);
            }
        });
    }

    /**
     * Fallback: load all cached articles when the requested category
     * returns empty (happens because initial fetch stores all as "general").
     */
    private void loadAllArticlesAsFallback(String displayName) {
        repository.getNews(new NewsRepository.NewsCallback() {
            @Override
            public void onSuccess(List<NewsArticle> articles) {
                if (!isAdded() || rvCategories == null) return;

                if (articles.isEmpty()) {
                    // Truly no data at all — go back to grid
                    showGrid();
                    return;
                }
                showArticleList(articles, displayName + " (ALL_CACHED)");
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showGrid();
            }
        });
    }

    /** Switch RecyclerView to list mode and display the articles. */
    private void showArticleList(List<NewsArticle> articles, String headerLabel) {
        newsAdapter.setArticles(articles);

        if (rvCategories != null) {
            rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvCategories.setAdapter(newsAdapter);
        }

        // Show section header with selected category name
        if (tvCategoryHeader != null) {
            tvCategoryHeader.setText("> " + headerLabel.toUpperCase());
            setVisibility(tvCategoryHeader, true);
        }

        setVisibility(layoutLoading, false);
        setVisibility(rvCategories,  true);
        setVisibility(layoutError,   false);
    }

    /**
     * Call from the host Activity's onBackPressed.
     * @return true if CategoryFragment consumed the back press.
     */
    public boolean handleBackPress() {
        if (currentMode == MODE_LIST) {
            showGrid();
            return true;
        }
        return false;
    }

    // ── CategoryAdapter.OnCategoryClickListener ───────────────────────────────

    @Override
    public void onCategoryClick(CategoryAdapter.Category category) {
        showCategoryArticles(category.apiTopic, category.name);
    }

    // ── NewsAdapter.OnArticleClickListener ────────────────────────────────────

    @Override
    public void onArticleClick(NewsArticle article) {
        Context ctx = getContext();
        if (ctx instanceof MainActivity) {
            ((MainActivity) ctx).launchNewsDetail(article);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setVisibility(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
