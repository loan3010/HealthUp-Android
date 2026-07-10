package com.example.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.healthup.util.ImageLoadHelper;
import java.util.List;

public class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {

    private List<String> imageUrls;
    private final boolean centerCrop;

    public ImageSliderAdapter(List<String> imageUrls) {
        this(imageUrls, false);
    }

    public ImageSliderAdapter(List<String> imageUrls, boolean centerCrop) {
        this.imageUrls = imageUrls;
        this.centerCrop = centerCrop;
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
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                Glide.with(holder.itemView.getContext())
                        .load(ImageLoadHelper.resolveLoadTarget(imageUrl))
                        .placeholder(R.drawable.ic_loading)
                        .error(R.drawable.ic_launcher_background);
        if (centerCrop) {
            request.centerCrop().into(holder.imageView);
        } else {
            request.centerInside().into(holder.imageView);
        }
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
