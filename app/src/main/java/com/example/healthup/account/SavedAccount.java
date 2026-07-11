package com.example.healthup.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Locally saved account card for quick switch (device storage only).
 */
public class SavedAccount {

    public static final String PROVIDER_PASSWORD = "password";
    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_FACEBOOK = "facebook";

    public final String uid;
    public final String displayName;
    public final String email;
    public final String phone;
    public final String avatarUrl;
    public final String authProvider;
    public final boolean googleLinked;

    public SavedAccount(
            @NonNull String uid,
            @NonNull String displayName,
            @Nullable String email,
            @Nullable String phone,
            @Nullable String avatarUrl,
            @NonNull String authProvider
    ) {
        this(uid, displayName, email, phone, avatarUrl, authProvider, false);
    }

    public SavedAccount(
            @NonNull String uid,
            @NonNull String displayName,
            @Nullable String email,
            @Nullable String phone,
            @Nullable String avatarUrl,
            @NonNull String authProvider,
            boolean googleLinked
    ) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email == null ? "" : email;
        this.phone = phone == null ? "" : phone;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.authProvider = authProvider;
        this.googleLinked = googleLinked;
    }

    @NonNull
    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("uid", uid);
        o.put("displayName", displayName);
        o.put("email", email);
        o.put("phone", phone);
        o.put("avatarUrl", avatarUrl);
        o.put("authProvider", authProvider);
        o.put("googleLinked", googleLinked);
        return o;
    }

    @Nullable
    static SavedAccount fromJson(@NonNull JSONObject o) {
        String uid = o.optString("uid", "");
        if (uid.isEmpty()) {
            return null;
        }
        return new SavedAccount(
                uid,
                o.optString("displayName", ""),
                o.optString("email", ""),
                o.optString("phone", ""),
                o.optString("avatarUrl", ""),
                o.optString("authProvider", PROVIDER_PASSWORD),
                o.optBoolean("googleLinked", false)
        );
    }

    public boolean isSocialProvider() {
        return PROVIDER_GOOGLE.equals(authProvider) || PROVIDER_FACEBOOK.equals(authProvider);
    }

    public boolean isGoogleSwitchable() {
        return PROVIDER_GOOGLE.equals(authProvider) || googleLinked;
    }
}
