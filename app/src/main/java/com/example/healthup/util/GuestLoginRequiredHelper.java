package com.example.healthup.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
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

    /** Same login-required overlay for Activities (e.g. PaymentInfo). */
    public static void bind(@NonNull View root, @NonNull Activity activity) {
        View layout = root.findViewById(R.id.layoutLoginRequired);
        if (layout == null || activity.isFinishing()) {
            return;
        }

        layout.setVisibility(View.VISIBLE);
        layout.findViewById(R.id.btnLoginRequired).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, LoginActivity.class)));
        layout.findViewById(R.id.btnLater).setOnClickListener(v -> navigateBackSafely(activity));
    }

    /** Avoid exiting to launcher when this Activity is the only one on the stack. */
    public static void navigateBackSafely(@NonNull Activity activity) {
        if (activity.isTaskRoot()) {
            activity.startActivity(CheckoutIntentHelper.buildMainHomeIntent(activity));
        }
        activity.finish();
    }
}
