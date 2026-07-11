package com.example.healthup.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds new password while the user opens the Firebase reset-password email link
 * (Spark-friendly; no Cloud Functions).
 */
public final class PendingPasswordReset {

    private static final String PREFS = "pending_password_reset";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_AT = "at";
    private static final long TTL_MS = 30 * 60 * 1000L;

    public static final class Data {
        @NonNull public final String phone;
        @NonNull public final String uid;
        @NonNull public final String email;
        @NonNull public final String password;

        Data(@NonNull String phone, @NonNull String uid, @NonNull String email, @NonNull String password) {
            this.phone = phone;
            this.uid = uid;
            this.email = email;
            this.password = password;
        }
    }

    private PendingPasswordReset() {
    }

    public static void save(
            @NonNull Context context,
            @NonNull String phone,
            @NonNull String uid,
            @NonNull String email,
            @NonNull String password
    ) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PHONE, phone)
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email.trim().toLowerCase())
                .putString(KEY_PASSWORD, password)
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply();
    }

    public static void clear(@NonNull Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    @Nullable
    public static Data load(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = prefs.getLong(KEY_AT, 0L);
        if (at <= 0 || System.currentTimeMillis() - at > TTL_MS) {
            clear(context);
            return null;
        }
        String phone = prefs.getString(KEY_PHONE, null);
        String uid = prefs.getString(KEY_UID, null);
        String email = prefs.getString(KEY_EMAIL, null);
        String password = prefs.getString(KEY_PASSWORD, null);
        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(uid)
                || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            return null;
        }
        return new Data(phone, uid, email, password);
    }
}
