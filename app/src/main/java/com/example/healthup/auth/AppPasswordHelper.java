package com.example.healthup.auth;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * App-managed passwords on Spark (no Cloud Functions):
 * <ul>
 *   <li>{@code passwordHash} — user-chosen password (Firestore)</li>
 *   <li>Firebase Auth password — {@link #authSecretForPhone(String)} derived from phone
 *       (session only; user never types this)</li>
 * </ul>
 */
public final class AppPasswordHelper {

    public static final String FIELD_PASSWORD_HASH = "passwordHash";
    public static final String FIELD_PASSWORD_AUTH_MODE = "passwordAuthMode";
    /** Profile uses Firestore hash + derived Auth secret. */
    public static final String MODE_APP = "app";

    /** Student-project pepper (not production-grade secrecy). */
    private static final String PEPPER = "HealthUp-Spark-AppPassword-v1";

    private AppPasswordHelper() {
    }

    @NonNull
    public static String hashUserPassword(@NonNull String rawPassword) {
        return sha256Hex(PEPPER + "\nuser\n" + rawPassword);
    }

    public static boolean matchesUserPassword(
            @NonNull String rawPassword,
            @Nullable String storedHash
    ) {
        return !TextUtils.isEmpty(storedHash)
                && storedHash.equals(hashUserPassword(rawPassword));
    }

    /**
     * Firebase Auth password for this phone. Stable across password resets.
     * Must meet Auth strength rules (length / complexity).
     */
    @NonNull
    public static String authSecretForPhone(@NonNull String normalizedPhone) {
        String digest = sha256Hex(PEPPER + "\nauth\n" + normalizedPhone);
        // Prefix keeps a letter/digit/symbol mix Auth accepts as "strong enough".
        return "Hu1!" + digest.substring(0, 28);
    }

    /** Stable Firebase Auth password for admin email accounts (session only). */
    @NonNull
    public static String authSecretForAdminEmail(@NonNull String normalizedEmail) {
        String digest = sha256Hex(PEPPER + "\nadmin-auth\n" + normalizedEmail.toLowerCase(Locale.ROOT));
        return "Ha1!" + digest.substring(0, 28);
    }

    /**
     * Synthetic Auth email per admin Firestore profile — avoids collision with legacy
     * real-email Auth accounts after password reset.
     */
    @NonNull
    public static String syntheticAuthEmailForAdmin(@NonNull String profileDocId) {
        return "admin-" + profileDocId.toLowerCase(Locale.ROOT) + UserProfileBuilder.SYNTHETIC_EMAIL_DOMAIN;
    }

    /** True when login should verify {@link #FIELD_PASSWORD_HASH} before opening an Auth session. */
    public static boolean canUseAppPasswordLogin(@Nullable DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return false;
        }
        return isAppPasswordMode(doc)
                || !TextUtils.isEmpty(doc.getString(FIELD_PASSWORD_HASH));
    }

    public static boolean isAppPasswordMode(@Nullable DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return false;
        }
        return MODE_APP.equals(doc.getString(FIELD_PASSWORD_AUTH_MODE))
                && !TextUtils.isEmpty(doc.getString(FIELD_PASSWORD_HASH));
    }

    public static boolean isAppPasswordMode(@Nullable Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object mode = data.get(FIELD_PASSWORD_AUTH_MODE);
        Object hash = data.get(FIELD_PASSWORD_HASH);
        return MODE_APP.equals(mode) && hash instanceof String && !TextUtils.isEmpty((String) hash);
    }

    @NonNull
    public static Map<String, Object> passwordFieldsForNewPassword(@NonNull String rawPassword) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(FIELD_PASSWORD_HASH, hashUserPassword(rawPassword));
        fields.put(FIELD_PASSWORD_AUTH_MODE, MODE_APP);
        return fields;
    }

    @Nullable
    public static String authEmailFromProfile(@Nullable DocumentSnapshot doc) {
        if (doc == null) {
            return null;
        }
        String email = doc.getString("email");
        return TextUtils.isEmpty(email) ? null : email.trim();
    }

    @NonNull
    private static String sha256Hex(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always present on Android; fallback keeps compile happy.
            return Base64.encodeToString(
                    input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }
}
