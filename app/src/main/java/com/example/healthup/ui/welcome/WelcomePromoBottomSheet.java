package com.example.healthup.ui.welcome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class WelcomePromoBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "WelcomePromoBottomSheet";
    private static final String PREFS_NAME = "healthup_prefs";
    private static final String KEY_WELCOME_PROMO_SHOWN = "welcome_promo_shown";

    public static boolean shouldShow(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return false;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_WELCOME_PROMO_SHOWN, false);
    }

    public static void showIfNeeded(
            @NonNull androidx.fragment.app.FragmentManager fragmentManager,
            @NonNull Context context
    ) {
        if (fragmentManager.findFragmentByTag(TAG) != null || !shouldShow(context)) {
            return;
        }
        new WelcomePromoBottomSheet().show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_welcome_promo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View closeButton = view.findViewById(R.id.welcomePromoClose);
        MaterialButton loginButton = view.findViewById(R.id.welcomePromoLoginButton);
        View exploreButton = view.findViewById(R.id.welcomePromoExploreButton);

        View.OnClickListener dismissListener = v -> dismissPromo();
        closeButton.setOnClickListener(dismissListener);
        exploreButton.setOnClickListener(dismissListener);

        loginButton.setOnClickListener(v -> {
            markShown();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            dismissAllowingStateLoss();
        });
    }

    private void dismissPromo() {
        markShown();
        dismissAllowingStateLoss();
    }

    private void markShown() {
        requireContext().getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WELCOME_PROMO_SHOWN, true)
                .apply();
    }
}
