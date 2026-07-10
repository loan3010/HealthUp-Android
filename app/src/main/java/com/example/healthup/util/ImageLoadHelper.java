package com.example.healthup.util;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.healthup.R;

public final class ImageLoadHelper {

    private ImageLoadHelper() {
    }

    public static Object resolveLoadTarget(@Nullable String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return R.drawable.ic_loading;
        }
        if (imageUrl.startsWith("http") || imageUrl.startsWith("file://")
                || imageUrl.startsWith("content://") || imageUrl.startsWith("data:")) {
            return imageUrl;
        }
        String cleanPath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
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
        if (imageUrl == null || imageUrl.isEmpty()) {
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
