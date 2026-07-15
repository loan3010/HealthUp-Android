package com.example.healthup.auth;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class UserProfileBuilder {

    public static final String AUTH_PROVIDER_GOOGLE = "google";
    public static final String AUTH_PROVIDER_FACEBOOK = "facebook";
    public static final String AUTH_PROVIDER_PASSWORD = "password";
    public static final String SYNTHETIC_EMAIL_DOMAIN = "@healthup.app";

    private UserProfileBuilder() {
    }

    public static boolean isSyntheticAuthEmail(@Nullable String email) {
        return !TextUtils.isEmpty(email) && email.endsWith(SYNTHETIC_EMAIL_DOMAIN);
    }

    public static boolean isRealEmail(@Nullable String email) {
        return !TextUtils.isEmpty(email) && !isSyntheticAuthEmail(email);
    }

    @NonNull
    private static String normalizeStoredEmail(@NonNull String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static Map<String, Object> buildPasswordRegistration(
            String fullName,
            String phone,
            String authEmail,
            String displayEmail,
            String username,
            @Nullable String rawPassword
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("phone", phone);
        // Auth login email stays synthetic; real email (if any) is displayEmail only.
        userData.put("email", authEmail);
        userData.put("phoneVerified", true);
        userData.put("role", "buyer");
        userData.put("authProvider", AUTH_PROVIDER_PASSWORD);
        userData.put("googleLinked", false);
        userData.put("spentAmount", 0d);
        userData.put("emailVerified", false);
        userData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (isRealEmail(displayEmail)) {
            userData.put("displayEmail", normalizeStoredEmail(displayEmail));
        }
        if (!TextUtils.isEmpty(username)) {
            userData.put("username", username);
        }
        if (!TextUtils.isEmpty(rawPassword)) {
            userData.putAll(AppPasswordHelper.passwordFieldsForNewPassword(rawPassword));
        }
        return userData;
    }

    public static Map<String, Object> buildSocialRegistration(
            String fullName,
            String phone,
            String authEmail,
            String displayEmail,
            String authProvider,
            String username
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("phone", phone);
        userData.put("email", isRealEmail(authEmail) ? normalizeStoredEmail(authEmail) : authEmail);
        userData.put("phoneVerified", true);
        userData.put("role", "buyer");
        userData.put("authProvider", authProvider);
        userData.put("spentAmount", 0d);
        userData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        boolean isGoogle = AUTH_PROVIDER_GOOGLE.equals(authProvider);
        userData.put("googleLinked", isGoogle);
        // Google/Facebook email is already verified by the provider.
        userData.put("emailVerified", isRealEmail(authEmail) || isRealEmail(displayEmail));
        if (isRealEmail(displayEmail)) {
            userData.put("displayEmail", normalizeStoredEmail(displayEmail));
        } else if (isRealEmail(authEmail)) {
            userData.put("displayEmail", normalizeStoredEmail(authEmail));
        }
        if (!TextUtils.isEmpty(username)) {
            userData.put("username", username);
        }
        return userData;
    }

    @NonNull
    public static Map<String, Object> buildGoogleLinkUpdates(@NonNull String googleEmail) {
        String normalized = normalizeStoredEmail(googleEmail);
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", normalized);
        updates.put("displayEmail", normalized);
        updates.put("emailVerified", true);
        updates.put("googleLinked", true);
        updates.put("authProvider", AUTH_PROVIDER_GOOGLE);
        return updates;
    }
}
