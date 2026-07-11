package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.healthup.R;
import com.example.models.FAQCategory;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class FAQCategoryAdapter extends RecyclerView.Adapter<FAQCategoryAdapter.ViewHolder> {

    private final List<FAQCategory> categories;
    private final OnCategoryClickListener listener;
    private int selectedPosition = -1;

    public interface OnCategoryClickListener {
        void onCategoryClick(FAQCategory category, int position, boolean isSelected);
    }

    public FAQCategoryAdapter(List<FAQCategory> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_faq_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FAQCategory category = categories.get(position);
        holder.tvName.setText(category.getName());
        holder.ivIcon.setImageResource(category.getIconResId());

        boolean isSelected = selectedPosition == position;
        
        if (isSelected) {
            holder.cardView.setCardBackgroundColor(holder.itemView.getContext().getResources().getColor(R.color.primary_green));
            holder.ivIcon.setColorFilter(holder.itemView.getContext().getResources().getColor(R.color.white));
            holder.cardView.setStrokeWidth(0);
        } else {
            holder.cardView.setCardBackgroundColor(holder.itemView.getContext().getResources().getColor(R.color.white));
            holder.ivIcon.setColorFilter(holder.itemView.getContext().getResources().getColor(R.color.primary_green));
            holder.cardView.setStrokeWidth(1);
            holder.cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFF0F0F0));
        }

        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedPosition;
            if (selectedPosition == holder.getAdapterPosition()) {
                // Deselect if clicking the same one
                selectedPosition = -1;
                listener.onCategoryClick(null, -1, false);
            } else {
                selectedPosition = holder.getAdapterPosition();
                listener.onCategoryClick(category, selectedPosition, true);
            }
            notifyItemChanged(previousSelected);
            notifyItemChanged(selectedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        MaterialCardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_faq_category_icon);
            tvName = itemView.findViewById(R.id.tv_faq_category_name);
            cardView = itemView.findViewById(R.id.card_faq_category);
        }
    }
}
