package com.example.healthup.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves display names and keeps users/{authUid} in sync after phone-based login.
 */
public final class UserProfileResolver {

    private static final String COLLECTION_USERS = "users";
    private static final String AUTH_EMAIL_SUFFIX = "@healthup.app";

    private UserProfileResolver() {
    }

    public static boolean hasProfileName(@Nullable DocumentSnapshot document) {
        return !TextUtils.isEmpty(resolveNameFromDocument(document));
    }

    @Nullable
    public static String resolveNameFromDocument(@Nullable DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return null;
        }
        String name = firstNonEmpty(
                document.getString("fullName"),
                document.getString("name"),
                document.getString("username"),
                document.getString("displayName")
        );
        if (TextUtils.isEmpty(name)) {
            String phone = document.getString("phone");
            if (!TextUtils.isEmpty(phone)) {
                name = PhoneNormalizer.normalize(phone);
            }
        }
        return TextUtils.isEmpty(name) ? null : name;
    }

    @NonNull
    public static String resolveDisplayName(
            @Nullable DocumentSnapshot document,
            @NonNull FirebaseUser firebaseUser
    ) {
        String name = resolveNameFromDocument(document);

        if (TextUtils.isEmpty(name)) {
            name = firebaseUser.getDisplayName();
        }
        if (TextUtils.isEmpty(name)) {
            String phoneNumber = firebaseUser.getPhoneNumber();
            if (!TextUtils.isEmpty(phoneNumber)) {
                name = PhoneNormalizer.normalize(phoneNumber);
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = extractPhoneFromAuthEmail(firebaseUser.getEmail());
        }
        if (TextUtils.isEmpty(name)) {
            String email = firebaseUser.getEmail();
            if (!TextUtils.isEmpty(email) && !email.endsWith(AUTH_EMAIL_SUFFIX)) {
                name = email;
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = "Người dùng";
        }
        return name;
    }

    @Nullable
    public static String extractPhoneFromAuthEmail(@Nullable String email) {
        if (TextUtils.isEmpty(email) || !email.endsWith(AUTH_EMAIL_SUFFIX)) {
            return null;
        }
        String phone = email.substring(0, email.length() - AUTH_EMAIL_SUFFIX.length());
        return PhoneNormalizer.isValidLocalPhone(phone) ? phone : null;
    }

    /**
     * Merges profile fields from a phone-lookup document into users/{authUid}.
     * Handles legacy docs whose document id may differ from the Firebase Auth uid.
     */
    public static void syncProfileAfterLogin(
            @NonNull String authUid,
            @Nullable DocumentSnapshot sourceDoc
    ) {
        if (TextUtils.isEmpty(authUid) || sourceDoc == null || !sourceDoc.exists()) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        putIfPresent(updates, "fullName", sourceDoc.getString("fullName"));
        putIfPresent(updates, "name", sourceDoc.getString("name"));
        putIfPresent(updates, "phone", sourceDoc.getString("phone"));
        putIfPresent(updates, "email", sourceDoc.getString("email"));
        putIfPresent(updates, "displayEmail", sourceDoc.getString("displayEmail"));
        putIfPresent(updates, "username", sourceDoc.getString("username"));
        putIfPresent(updates, "tier", sourceDoc.getString("tier"));
        putIfPresent(updates, "avatarUrl", sourceDoc.getString("avatarUrl"));

        if (!updates.containsKey("phone")) {
            String phone = extractPhoneFromAuthEmail(sourceDoc.getString("email"));
            if (!TextUtils.isEmpty(phone)) {
                updates.put("phone", phone);
            }
        }

        if (updates.isEmpty()) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(COLLECTION_USERS)
                .document(authUid)
                .set(updates, SetOptions.merge());
    }

    private static void putIfPresent(Map<String, Object> map, String key, @Nullable String value) {
        if (!TextUtils.isEmpty(value)) {
            map.put(key, value);
        }
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
