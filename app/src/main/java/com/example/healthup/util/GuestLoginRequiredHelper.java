package com.example.healthup.util;

import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;

public final class GuestLoginRequiredHelper {

    private GuestLoginRequiredHelper() {
    }

    public static void bind(@NonNull View root, @NonNull Fragment host) {
        View layout = root.findViewById(R.id.layoutLoginRequired);
        if (layout == null || !host.isAdded()) {
            return;
        }

        layout.setVisibility(View.VISIBLE);
        layout.findViewById(R.id.btnLoginRequired).setOnClickListener(v ->
                host.startActivity(new Intent(host.requireContext(), LoginActivity.class)));
        layout.findViewById(R.id.btnLater).setOnClickListener(v -> {
            if (host.getActivity() != null) {
                host.getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    public static void bind(@NonNull View root, @NonNull AppCompatActivity host) {
        View layout = root.findViewById(R.id.layoutLoginRequired);
        if (layout == null) {
            return;
        }

        layout.setVisibility(View.VISIBLE);
        layout.findViewById(R.id.btnLoginRequired).setOnClickListener(v ->
                host.startActivity(new Intent(host, LoginActivity.class)));
        layout.findViewById(R.id.btnLater).setOnClickListener(v -> {
            host.getOnBackPressedDispatcher().onBackPressed();
        });
    }
}
