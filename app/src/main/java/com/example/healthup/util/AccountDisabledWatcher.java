package com.example.healthup.util;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Realtime watcher: when admin locks an account, show popup with reason and sign out.
 */
public final class AccountDisabledWatcher {

    private ListenerRegistration registration;
    private boolean dialogVisible;
    @Nullable
    private String lastShownReason;

    public void attach(@NonNull AppCompatActivity activity) {
        detach();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        registration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    handleSnapshot(activity, snapshot);
                });
    }

    public void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        dialogVisible = false;
    }

    public static boolean isDisabled(@Nullable DocumentSnapshot snapshot) {
        return snapshot != null
                && snapshot.exists()
                && Boolean.TRUE.equals(snapshot.getBoolean("disabled"));
    }

    @Nullable
    public static String getDisabledReason(@Nullable DocumentSnapshot snapshot) {
        if (snapshot == null) return null;
        return snapshot.getString("disabledReason");
    }

    /** Block navigation after login if account is locked. */
    public static void checkBeforeEnterApp(@NonNull Activity activity, @NonNull Runnable onAllowed) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            onAllowed.run();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (isDisabled(doc)) {
                        showDisabledDialog(activity, getDisabledReason(doc), true);
                    } else {
                        onAllowed.run();
                    }
                })
                .addOnFailureListener(e -> onAllowed.run());
    }

    private void handleSnapshot(@NonNull AppCompatActivity activity, @NonNull DocumentSnapshot snapshot) {
        if (!isDisabled(snapshot)) {
            lastShownReason = null;
            return;
        }
        String reason = getDisabledReason(snapshot);
        if (dialogVisible) {
            return;
        }
        if (TextUtils.equals(lastShownReason, reason)) {
            return;
        }
        showDisabledDialog(activity, reason, true);
        lastShownReason = reason;
    }

    public static void showDisabledDialog(@NonNull Activity activity,
                                          @Nullable String reason,
                                          boolean signOutAfter) {
        if (activity.isFinishing()) {
            return;
        }
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_account_disabled, null);
        TextView tvReason = view.findViewById(R.id.tvAccountDisabledReason);
        tvReason.setText(TextUtils.isEmpty(reason)
                ? activity.getString(R.string.account_disabled_reason_fallback)
                : reason);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.account_disabled_title)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.account_disabled_ok, (d, w) -> {
                    if (signOutAfter) {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(activity, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(intent);
                        if (activity instanceof AppCompatActivity) {
                            ((AppCompatActivity) activity).finish();
                        }
                    }
                })
                .show();
    }
}
