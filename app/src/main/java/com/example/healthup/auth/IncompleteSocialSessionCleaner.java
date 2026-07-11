package com.example.healthup.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Discards an incomplete Google/Facebook sign-up (Auth user created before OTP).
 */
public final class IncompleteSocialSessionCleaner {

    public interface Callback {
        void onComplete();
    }

    private IncompleteSocialSessionCleaner() {
    }

    public static void cleanup(@Nullable Callback callback) {
        PendingGoogleLink.clear();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            auth.signOut();
            notifyComplete(callback);
            return;
        }

        user.delete().addOnCompleteListener(task -> {
            auth.signOut();
            notifyComplete(callback);
        });
    }

    private static void notifyComplete(@Nullable Callback callback) {
        if (callback != null) {
            callback.onComplete();
        }
    }
}
