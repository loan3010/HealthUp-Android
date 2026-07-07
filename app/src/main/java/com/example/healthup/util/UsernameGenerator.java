package com.example.healthup.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.healthup.chat.TextNormalizer;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;
import java.util.Random;

public final class UsernameGenerator {

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_USERNAME = "username";
    private static final int MAX_ATTEMPTS = 5;
    private static final int SUFFIX_MAX = 10_000;
    private static final String FALLBACK_BASE = "user";

    public interface Callback {
        void onSuccess(@NonNull String username);

        void onFailure(@NonNull Exception error);
    }

    private UsernameGenerator() {
    }

    /**
     * Normalizes a Vietnamese full name into a lowercase alphanumeric base (no accents, no spaces).
     * Example: "Lê Xuân Mai" -> "lexuanmai"
     */
    @NonNull
    public static String normalizeNameForUsername(@NonNull String fullName) {
        String normalized = TextNormalizer.normalize(fullName);
        String base = normalized.replaceAll("[^a-z0-9]", "");
        return TextUtils.isEmpty(base) ? FALLBACK_BASE : base;
    }

    /**
     * Generates a unique username from full name and checks Firestore for collisions.
     * Retries with a new 4-digit suffix up to {@link #MAX_ATTEMPTS} times.
     */
    public static void generateUnique(@NonNull String fullName, @NonNull Callback callback) {
        String base = normalizeNameForUsername(fullName);
        attemptGenerate(base, 0, callback);
    }

    private static void attemptGenerate(
            @NonNull String base,
            int attempt,
            @NonNull Callback callback
    ) {
        if (attempt >= MAX_ATTEMPTS) {
            callback.onFailure(new IllegalStateException("Could not generate unique username"));
            return;
        }

        String candidate = base + randomSuffix();
        FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS)
                .whereEqualTo(FIELD_USERNAME, candidate)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess(candidate);
                    } else {
                        attemptGenerate(base, attempt + 1, callback);
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    @NonNull
    private static String randomSuffix() {
        int value = new Random().nextInt(SUFFIX_MAX);
        return String.format(Locale.US, "%04d", value);
    }
}
