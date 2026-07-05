package com.example.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class ProductImageAdapter extends RecyclerView.Adapter<ProductImageAdapter.ViewHolder> {
    private List<String> images;

    public ProductImageAdapter(List<String> images) {
        this.images = images;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imagePath = images.get(position);
        String fullPath;
        if (imagePath.startsWith("http")) {
            fullPath = imagePath;
        } else {
            String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
            fullPath = "file:///android_asset/" + cleanPath;
        }

        Glide.with(holder.imageView.getContext())
                .load(fullPath)
                .placeholder(R.drawable.ic_blog) // fallback placeholder
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return images != null ? images.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivProductDetailImage);
        }
    }
}
