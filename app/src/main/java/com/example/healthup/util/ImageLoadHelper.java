package com.example.healthup.util;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.healthup.R;

public final class ImageLoadHelper {

    private ImageLoadHelper() {
    }

    public static Object resolveLoadTarget(@Nullable String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || "null".equalsIgnoreCase(imageUrl.trim())) {
            return R.drawable.ic_loading;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("http") || trimmed.startsWith("file://")
                || trimmed.startsWith("content://") || trimmed.startsWith("data:")) {
            return trimmed;
        }
        // Firebase Storage gs:// URLs are not loadable by Glide directly
        if (trimmed.startsWith("gs://")) {
            return trimmed;
        }
        String cleanPath = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        if (cleanPath.startsWith("images/products/") || cleanPath.startsWith("images/")) {
            return "file:///android_asset/" + cleanPath;
        }
        return "file:///android_asset/images/products/" + cleanPath;
    }

    public static void loadInto(ImageView imageView, @Nullable String imageUrl) {
        if (imageView == null) {
            return;
        }
        android.content.Context context = imageView.getContext();
        if (context == null) {
            return;
        }
        if (imageUrl == null || imageUrl.isEmpty() || "null".equalsIgnoreCase(imageUrl.trim())) {
            imageView.setImageResource(R.color.neutral_light_grey);
            return;
        }
        Glide.with(context)
                .load(resolveLoadTarget(imageUrl))
                .placeholder(R.color.neutral_light_grey)
                .error(R.color.neutral_light_grey)
                .centerCrop()
                .into(imageView);
    }
}
