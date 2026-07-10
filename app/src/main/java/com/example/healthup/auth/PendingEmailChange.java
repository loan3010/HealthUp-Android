package com.example.healthup.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Remembers an in-progress email change so we can finish sync after the
 * verify link invalidates the local Auth session.
 */
public final class PendingEmailChange {

    private static final String PREFS = "change_email_prefs";
    private static final String KEY_EMAIL = "pending_email";
    private static final String KEY_UID = "pending_uid";

    public static final class Data {
        @NonNull public final String uid;
        @NonNull public final String email;

        public Data(@NonNull String uid, @NonNull String email) {
            this.uid = uid;
            this.email = email;
        }
    }

    private PendingEmailChange() {
    }

    public static void save(@NonNull Context context, @NonNull String uid, @NonNull String email) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email.trim().toLowerCase())
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
        String uid = prefs.getString(KEY_UID, null);
        String email = prefs.getString(KEY_EMAIL, null);
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(email)) {
            return null;
        }
        return new Data(uid, email);
    }

    @Nullable
    public static String emailForUid(@NonNull Context context, @NonNull String uid) {
        Data data = load(context);
        if (data == null || !uid.equals(data.uid)) {
            return null;
        }
        return data.email;
    }
}
