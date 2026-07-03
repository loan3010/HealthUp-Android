package com.example.healthup;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.models.Category;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    private List<Category> categories;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.tvName.setText(category.getName());
        
        // Glide for category icon
        String iconUrl = category.getIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty() && (iconUrl.startsWith("icons/") || iconUrl.startsWith("images/icons/"))) {
            String cleanPath = iconUrl.startsWith("images/") ? iconUrl : "images/" + iconUrl;
            Glide.with(holder.itemView.getContext())
                    .load("file:///android_asset/" + cleanPath)
                    .placeholder(R.drawable.ic_cat_fruit)
                    .into(holder.ivIcon);
        } else if (iconUrl != null && iconUrl.startsWith("http")) {
            Glide.with(holder.itemView.getContext())
                    .load(iconUrl)
                    .placeholder(R.drawable.ic_cat_fruit)
                    .into(holder.ivIcon);
        } else {
            // Default icon from assets if no URL is provided
            Glide.with(holder.itemView.getContext())
                    .load("file:///android_asset/images/icons/fruit.png")
                    .into(holder.ivIcon);
        }

        // Optional: Highlight first item as "Active" like in design (e.g. Granola)
        if (position == 0) {
            holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_default)));
            holder.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.neutral_white)));
            holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_default));
            holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_tint_5)));
            holder.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_default)));
            holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.neutral_black));
            holder.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivCategory);
            tvName = itemView.findViewById(R.id.tvCategoryName);
        }
    }
}
