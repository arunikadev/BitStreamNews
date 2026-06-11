package com.example.bit_stream_news.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bit_stream_news.R;
import com.example.bit_stream_news.model.NewsArticle;

import java.util.ArrayList;
import java.util.List;

/**
 * NewsAdapter — RecyclerView.Adapter binding {@link NewsArticle} objects
 * to {@code item_news_card.xml} ViewHolders.
 *
 * Supports:
 *  - Full list replacement with DiffUtil for efficient partial updates
 *  - Local search filter (operates on a separate display list, no API call)
 *  - Click callbacks via {@link OnArticleClickListener} interface
 *  - Glide image loading with placeholder and graceful no-image fallback
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    // ── Click listener interface ──────────────────────────────────────────────

    public interface OnArticleClickListener {
        void onArticleClick(NewsArticle article);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Full dataset — never filtered. Kept for restoring after search clears. */
    private List<NewsArticle> fullList    = new ArrayList<>();

    /** Currently displayed list — may be a filtered subset of fullList. */
    private List<NewsArticle> displayList = new ArrayList<>();

    private final OnArticleClickListener clickListener;

    // ── Constructor ───────────────────────────────────────────────────────────

    public NewsAdapter(OnArticleClickListener clickListener) {
        this.clickListener = clickListener;
    }

    // ── Data management ───────────────────────────────────────────────────────

    /**
     * Replaces the full dataset and notifies via DiffUtil for smooth updates.
     * Also resets any active search filter.
     *
     * @param newArticles fresh list from cache or API.
     */
    public void setArticles(List<NewsArticle> newArticles) {
        if (newArticles == null) newArticles = new ArrayList<>();

        List<NewsArticle> old = new ArrayList<>(displayList);
        this.fullList    = new ArrayList<>(newArticles);
        this.displayList = new ArrayList<>(newArticles);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new ArticleDiffCallback(old, displayList));
        diff.dispatchUpdatesTo(this);
    }

    /**
     * Filters displayList to articles whose title or description
     * contains the query string (case-insensitive).
     *
     * Passing null or empty string restores the full list.
     * Zero API calls — operates purely on in-memory data.
     *
     * @param query search text from the EditText in HomeFeedFragment.
     */
    public void filter(String query) {
        List<NewsArticle> filtered;

        if (query == null || query.trim().isEmpty()) {
            filtered = new ArrayList<>(fullList);
        } else {
            String lc = query.toLowerCase().trim();
            filtered = new ArrayList<>();
            for (NewsArticle article : fullList) {
                if (matchesQuery(article, lc)) {
                    filtered.add(article);
                }
            }
        }

        List<NewsArticle> old = new ArrayList<>(displayList);
        this.displayList = filtered;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(
                new ArticleDiffCallback(old, displayList));
        diff.dispatchUpdatesTo(this);
    }

    private boolean matchesQuery(NewsArticle article, String lc) {
        if (article.getTitle() != null
                && article.getTitle().toLowerCase().contains(lc)) return true;
        if (article.getDescription() != null
                && article.getDescription().toLowerCase().contains(lc)) return true;
        if (article.getSource() != null
                && article.getSource().toLowerCase().contains(lc)) return true;
        return false;
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_card, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        holder.bind(displayList.get(position));
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class NewsViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivThumbnail;
        private final TextView  tvTitle;
        private final TextView  tvDescription;
        private final TextView  tvSource;
        private final TextView  tvTime;
        private final TextView  tvCategory;
        private final View      tvNoImage;

        NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail    = itemView.findViewById(R.id.iv_card_thumbnail);
            tvTitle        = itemView.findViewById(R.id.tv_card_title);
            tvDescription  = itemView.findViewById(R.id.tv_card_description);
            tvSource       = itemView.findViewById(R.id.tv_card_source);
            tvTime         = itemView.findViewById(R.id.tv_card_time);
            tvCategory     = itemView.findViewById(R.id.tv_card_category);
            tvNoImage      = itemView.findViewById(R.id.tv_no_image);
        }

        void bind(NewsArticle article) {
            // Title
            if (tvTitle != null) {
                tvTitle.setText(article.getTitle() != null
                        ? article.getTitle().toUpperCase() : "");
            }

            // Description
            if (tvDescription != null) {
                tvDescription.setText(article.getDescription() != null
                        ? article.getDescription() : "");
            }

            // Source badge
            if (tvSource != null) {
                tvSource.setText(article.getSource() != null
                        ? article.getSource().toUpperCase() : "N/A");
            }

            // Category
            if (tvCategory != null) {
                tvCategory.setText(article.getCategory() != null
                        ? article.getCategory().toUpperCase() : "NEWS");
            }

            // Timestamp: extract HH:mm from ISO string
            if (tvTime != null) {
                tvTime.setText(formatTime(article.getPublishedAt()));
            }

            // Thumbnail via Glide
            if (ivThumbnail != null) {
                String imageUrl = article.getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    if (tvNoImage != null) tvNoImage.setVisibility(View.GONE);
                    ivThumbnail.setVisibility(View.VISIBLE);
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .placeholder(android.R.color.darker_gray)
                            .error(android.R.color.darker_gray)
                            .centerCrop()
                            .into(ivThumbnail);
                } else {
                    // No image available — show placeholder text
                    ivThumbnail.setVisibility(View.GONE);
                    if (tvNoImage != null) tvNoImage.setVisibility(View.VISIBLE);
                }
            }

            // Click handler
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onArticleClick(article);
                }
            });
        }

        /** Extracts "HH:mm" from an ISO-8601 string. Returns "[--:--]" on failure. */
        private String formatTime(String iso) {
            if (iso == null || iso.isEmpty()) return "[--:--]";
            try {
                int tIdx = iso.indexOf('T');
                if (tIdx < 0 || tIdx + 6 > iso.length()) return "[??:??]";
                return "[" + iso.substring(tIdx + 1, tIdx + 6) + "]";
            } catch (Exception e) {
                return "[??:??]";
            }
        }
    }

    // ── DiffUtil callback ─────────────────────────────────────────────────────

    private static class ArticleDiffCallback extends DiffUtil.Callback {

        private final List<NewsArticle> oldList;
        private final List<NewsArticle> newList;

        ArticleDiffCallback(List<NewsArticle> oldList, List<NewsArticle> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            String oldId = oldList.get(oldPos).getId();
            String newId = newList.get(newPos).getId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            NewsArticle o = oldList.get(oldPos);
            NewsArticle n = newList.get(newPos);
            return safeEquals(o.getTitle(),       n.getTitle())
                && safeEquals(o.getDescription(), n.getDescription())
                && safeEquals(o.getImageUrl(),    n.getImageUrl());
        }

        private boolean safeEquals(String a, String b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.equals(b);
        }
    }
}
