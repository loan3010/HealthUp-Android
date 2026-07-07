package com.example.healthup.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.chat.TextNormalizer;

public final class UsernameValidator {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private UsernameValidator() {
    }

    /**
     * Normalizes user input to a lowercase alphanumeric username (no accents or spaces).
     */
    @NonNull
    public static String normalize(@NonNull String raw) {
        String normalized = TextNormalizer.normalize(raw.trim());
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    /**
     * @return error key ({@code required}, {@code invalid}) or {@code null} when valid
     */
    @Nullable
    public static String validate(@NonNull String raw) {
        if (TextUtils.isEmpty(raw.trim())) {
            return "required";
        }
        String username = normalize(raw);
        if (TextUtils.isEmpty(username)) {
            return "invalid";
        }
        if (username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
            return "invalid";
        }
        if (!username.matches("[a-z0-9]+")) {
            return "invalid";
        }
        return null;
    }
}
