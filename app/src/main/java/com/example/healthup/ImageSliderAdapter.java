package com.example.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.healthup.R;
import java.util.List;

public class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {

    private List<String> imageUrls;

    public ImageSliderAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_slider, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        Object loadTarget = R.drawable.ic_loading; // Sử dụng icon loading làm placeholder

        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (imageUrl.startsWith("http") || imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
                loadTarget = imageUrl;
            } else {
                String cleanPath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                // Ưu tiên tìm trong images/products/ hoặc images/ hoặc trực tiếp
                if (cleanPath.startsWith("images/products/")) {
                    loadTarget = "file:///android_asset/" + cleanPath;
                } else if (cleanPath.startsWith("images/")) {
                    loadTarget = "file:///android_asset/" + cleanPath;
                } else {
                    loadTarget = "file:///android_asset/images/products/" + cleanPath;
                }
            }
        }

        Glide.with(holder.itemView.getContext())
                .load(loadTarget)
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_launcher_background)
                .centerInside() // Đảm bảo ảnh không bị cắt mất chi tiết quan trọng
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_slider);
        }
    }
}
