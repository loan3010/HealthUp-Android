package com.example.healthup.auth;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.healthup.BuildConfig;
import com.example.healthup.R;

/**
 * Resolves the Google Sign-In Web client ID used for Firebase idToken exchange.
 * Priority: BuildConfig (from google-services.json at compile time) → strings.xml.
 */
public final class GoogleWebClientIdResolver {

    private static final String PLACEHOLDER_MARKER = "YOUR_WEB_CLIENT_ID";

    private GoogleWebClientIdResolver() {
    }

    @Nullable
    public static String resolve(@Nullable Context context) {
        String fromBuildConfig = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (isValidClientId(fromBuildConfig)) {
            return fromBuildConfig;
        }

        if (context != null) {
            String fromStrings = context.getString(R.string.default_web_client_id);
            if (isValidClientId(fromStrings)) {
                return fromStrings;
            }
        }

        return null;
    }

    public static boolean isValidClientId(@Nullable String clientId) {
        return !TextUtils.isEmpty(clientId)
                && !clientId.contains(PLACEHOLDER_MARKER)
                && clientId.endsWith(".apps.googleusercontent.com");
    }
}
