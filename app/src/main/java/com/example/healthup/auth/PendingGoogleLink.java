package com.example.healthup.auth;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds a pending Google idToken so we can link Google to an existing
 * phone+password account after OTP + password confirmation.
 */
public final class PendingGoogleLink {

    @Nullable
    private static String pendingIdToken;
    @Nullable
    private static String pendingEmail;

    private PendingGoogleLink() {
    }

    public static void set(@NonNull String idToken, @Nullable String email) {
        pendingIdToken = idToken;
        pendingEmail = email;
    }

    public static void clear() {
        pendingIdToken = null;
        pendingEmail = null;
    }

    @Nullable
    public static String getIdToken() {
        return pendingIdToken;
    }

    @Nullable
    public static String getEmail() {
        return pendingEmail;
    }

    public static boolean hasPending() {
        return !TextUtils.isEmpty(pendingIdToken);
    }
}
