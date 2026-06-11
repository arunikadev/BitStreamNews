package com.example.bit_stream_news.ui.category;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bit_stream_news.R;

import java.util.List;

/**
 * CategoryAdapter — RecyclerView.Adapter for the category grid.
 *
 * Binds a list of {@link Category} data objects to item_category_card.xml
 * and fires {@link OnCategoryClickListener} on tap.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    // ── Data model ────────────────────────────────────────────────────────────

    /** Lightweight struct representing one category tile. */
    public static class Category {
        public final String icon;       // e.g. "[@]", "[*]", "[#]"
        public final String name;       // display name, e.g. "WORLD"
        public final String description;// subtitle, e.g. "Global data stream"
        public final String apiTopic;   // RapidAPI topic key, e.g. "world"
        public final String levelLabel; // e.g. "LVL 01"

        public Category(String icon, String name, String description,
                        String apiTopic, String levelLabel) {
            this.icon        = icon;
            this.name        = name;
            this.description = description;
            this.apiTopic    = apiTopic;
            this.levelLabel  = levelLabel;
        }
    }

    // ── Click listener ────────────────────────────────────────────────────────

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<Category>           categories;
    private final OnCategoryClickListener  clickListener;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CategoryAdapter(List<Category> categories,
                           OnCategoryClickListener clickListener) {
        this.categories    = categories;
        this.clickListener = clickListener;
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position));
    }

    @Override
    public int getItemCount() { return categories.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class CategoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvIcon;
        private final TextView tvName;
        private final TextView tvDesc;
        private final TextView tvLevel;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon  = itemView.findViewById(R.id.tv_category_icon);
            tvName  = itemView.findViewById(R.id.tv_category_name);
            tvDesc  = itemView.findViewById(R.id.tv_category_desc);
            tvLevel = itemView.findViewById(R.id.tv_level_badge);
        }

        void bind(Category category) {
            if (tvIcon  != null) tvIcon.setText(category.icon);
            if (tvName  != null) tvName.setText(category.name);
            if (tvDesc  != null) tvDesc.setText(category.description);
            if (tvLevel != null) tvLevel.setText(category.levelLabel);

            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onCategoryClick(category);
            });
        }
    }
}
