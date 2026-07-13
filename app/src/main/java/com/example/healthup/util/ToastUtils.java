package com.example.healthup.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.healthup.R;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

public final class ToastUtils {

    private ToastUtils() {
    }

    public static void show(@Nullable Context context, @StringRes int messageRes) {
        if (context == null) return;
        show(context, context.getString(messageRes));
    }

    public static void show(@Nullable Context context, @Nullable String message) {
        if (context == null || message == null || !(context instanceof Activity)) return;

        Activity activity = (Activity) context;
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) return;

        Snackbar snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_SHORT);
        
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.TRANSPARENT);
        snackbarView.setElevation(0);
        
        ViewGroup snackbarLayout = (ViewGroup) snackbarView;
        snackbarLayout.removeAllViews(); // Xóa sạch nội dung mặc định của Snackbar
        
        View customView = LayoutInflater.from(context).inflate(R.layout.layout_custom_toast, null);
        
        TextView textView = customView.findViewById(R.id.tv_toast_message);
        textView.setText(message);

        // Căn giữa Toast mới vào chính giữa chiều ngang
        com.google.android.material.snackbar.Snackbar.SnackbarLayout.LayoutParams params = 
                new com.google.android.material.snackbar.Snackbar.SnackbarLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 250; // Đẩy lên cao cho giống Toast
        
        snackbarLayout.addView(customView, params);
        snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);
        snackbar.show();
        snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);
        snackbar.show();
    }
}
