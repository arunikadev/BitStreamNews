package com.example.bit_stream_news.ui.bookmark;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.bit_stream_news.ui.home.NewsAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

/**
 * BookmarkFragment — Displays all articles the user has saved.
 *
 * Data source: SQLite (is_bookmarked = 1), no network calls.
 * Threading:   Executor + Handler (same pattern as NewsRepository).
 *
 * Features:
 *  - RecyclerView list of saved articles (NewsAdapter)
 *  - Empty state when no saves exist
 *  - [CLR] button to remove all bookmarks
 *  - Tapping an article opens NewsDetailActivity via MainActivity
 *  - Bookmark [★] button in each card removes the save from here
 */
public class BookmarkFragment extends Fragment
        implements NewsAdapter.OnArticleClickListener,
                   NewsAdapter.OnBookmarkClickListener {

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView rvBookmarks;
    private View         layoutLoading;
    private View         layoutEmpty;
    private TextView     tvBookmarkCount;
    private View         btnClearBookmarks;

    // ── Data ──────────────────────────────────────────────────────────────────

    private NewsAdapter    adapter;
    private NewsDbHelper   dbHelper;
    private ExecutorService executor;
    private Handler         mainHandler;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookmark, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper    = NewsDbHelper.getInstance(requireContext());
        executor    = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        rvBookmarks       = view.findViewById(R.id.rv_bookmarks);
        layoutLoading     = view.findViewById(R.id.layout_loading);
        layoutEmpty       = view.findViewById(R.id.layout_empty);
        tvBookmarkCount   = view.findViewById(R.id.tv_bookmark_count);
        btnClearBookmarks = view.findViewById(R.id.btn_clear_bookmarks);

        adapter = new NewsAdapter(this);
        adapter.setBookmarkListener(this);

        rvBookmarks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBookmarks.setAdapter(adapter);

        btnClearBookmarks.setOnClickListener(v -> clearAllBookmarks());

        loadBookmarks();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh whenever user returns to this tab
        loadBookmarks();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) executor.shutdown();
        rvBookmarks       = null;
        layoutLoading     = null;
        layoutEmpty       = null;
        tvBookmarkCount   = null;
        btnClearBookmarks = null;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadBookmarks() {
        setVisibility(layoutLoading, true);
        setVisibility(layoutEmpty,   false);
        setVisibility(rvBookmarks,   false);

        executor.execute(() -> {
            List<NewsArticle> bookmarks = dbHelper.getBookmarkedNews();
            mainHandler.post(() -> {
                if (!isAdded()) return;
                setVisibility(layoutLoading, false);

                if (bookmarks.isEmpty()) {
                    setVisibility(layoutEmpty,  true);
                    setVisibility(rvBookmarks,  false);
                    updateCount(0);
                } else {
                    adapter.setArticles(bookmarks);
                    setVisibility(rvBookmarks,  true);
                    setVisibility(layoutEmpty,  false);
                    updateCount(bookmarks.size());
                }
            });
        });
    }

    private void clearAllBookmarks() {
        executor.execute(() -> {
            List<NewsArticle> bookmarks = dbHelper.getBookmarkedNews();
            for (NewsArticle article : bookmarks) {
                dbHelper.removeBookmark(article.getId());
            }
            mainHandler.post(() -> {
                if (!isAdded()) return;
                adapter.setArticles(java.util.Collections.emptyList());
                setVisibility(rvBookmarks,  false);
                setVisibility(layoutEmpty,  true);
                updateCount(0);
            });
        });
    }

    private void updateCount(int count) {
        if (tvBookmarkCount != null) {
            tvBookmarkCount.setText(count + " SAVES_FOUND");
        }
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

    /**
     * Called when user taps [★] on a card inside the Bookmark screen.
     * Since newState=false means they removed the bookmark, reload the list.
     */
    @Override
    public void onBookmarkClick(NewsArticle article, boolean newState) {
        executor.execute(() -> {
            if (newState) {
                dbHelper.bookmarkArticle(article.getId());
            } else {
                dbHelper.removeBookmark(article.getId());
                // Reload to remove the un-bookmarked item from the list
                mainHandler.post(this::loadBookmarks);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setVisibility(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
