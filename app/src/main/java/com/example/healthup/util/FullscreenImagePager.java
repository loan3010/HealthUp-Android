package com.example.healthup.util;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.example.healthup.ImageSliderAdapter;
import com.example.healthup.R;

import java.util.ArrayList;
import java.util.List;

/** Fullscreen swipeable image viewer with n/N chip. */
public final class FullscreenImagePager {

    private FullscreenImagePager() {}

    public static void show(@NonNull Context context, @NonNull List<String> urls, int startIndex) {
        if (urls.isEmpty()) return;
        List<String> images = new ArrayList<>();
        for (String u : urls) {
            if (u != null && !u.trim().isEmpty()) images.add(u.trim());
        }
        if (images.isEmpty()) return;

        int start = Math.max(0, Math.min(startIndex, images.size() - 1));
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_image_pager, null);
        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        ViewPager2 pager = root.findViewById(R.id.pager_fullscreen_images);
        TextView tvIndex = root.findViewById(R.id.tv_fullscreen_index);
        ImageButton btnClose = root.findViewById(R.id.btn_fullscreen_close);

        pager.setAdapter(new ImageSliderAdapter(images, false));
        pager.setUserInputEnabled(images.size() > 1);
        pager.setCurrentItem(start, false);

        if (images.size() > 1) {
            tvIndex.setVisibility(View.VISIBLE);
            tvIndex.setText((start + 1) + "/" + images.size());
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    tvIndex.setText((position + 1) + "/" + images.size());
                }
            });
        } else {
            tvIndex.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
