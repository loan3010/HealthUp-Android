package com.example.healthup.util;

import android.view.View;
import android.widget.TextView;

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
        View back = root.findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> {
                if (host.getActivity() != null) {
                    host.getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        TextView tvTitle = root.findViewById(R.id.tv_utility_title);
        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }

        TextView legacyTitle = root.findViewById(R.id.tv_title);
        if (legacyTitle != null && title != null) {
            legacyTitle.setText(title);
        }
    }
}
