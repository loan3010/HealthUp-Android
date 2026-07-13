package com.example.healthup.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.healthup.R;

public final class UtilityHeaderHelper {

    private UtilityHeaderHelper() {
    }

    public static void bind(@NonNull View root, @NonNull Fragment host, @StringRes int titleRes) {
        bind(root, host, host.getString(titleRes));
    }

    public static void bind(@NonNull View root, @NonNull Fragment host, @Nullable String title) {
        setupBack(root, host);
        setupTitle(root, title);
    }

    private static void setupBack(@NonNull View root, @NonNull Fragment host) {
        View back = root.findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> {
                if (host.getActivity() != null) {
                    host.getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }
    }

    private static void setupTitle(@NonNull View root, @Nullable String title) {
        TextView tvTitle = root.findViewById(R.id.tv_utility_title);
        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }

        TextView legacyTitle = root.findViewById(R.id.tv_title);
        if (legacyTitle != null && title != null) {
            legacyTitle.setText(title);
        }
    }

    public static void showAction(@NonNull View root, @IdRes int actionId, View.OnClickListener listener) {
        View actionsLayout = root.findViewById(R.id.layout_header_actions);
        if (actionsLayout != null) {
            actionsLayout.setVisibility(View.VISIBLE);
            View action = root.findViewById(actionId);
            if (action != null) {
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(listener);
            }
        }
    }
}
