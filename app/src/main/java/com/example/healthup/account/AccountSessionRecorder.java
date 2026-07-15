package com.example.healthup.account;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.auth.UserProfileBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Saves / updates a local account card after any successful sign-in or registration.
 */
public final class AccountSessionRecorder {

    public interface Listener {
        void onRecorded();

        void onRejectedAccountLimit();
    }

    private AccountSessionRecorder() {
    }

    public static void recordFromProfile(
            @NonNull Context context,
            @NonNull String uid,
            @Nullable DocumentSnapshot profileDoc,
            @Nullable String passwordForQuickLogin
    ) {
        recordFromProfile(context, uid, profileDoc, passwordForQuickLogin, null);
    }

    public static void recordFromProfile(
            @NonNull Context context,
            @NonNull String uid,
            @Nullable DocumentSnapshot profileDoc,
            @Nullable String passwordForQuickLogin,
            @Nullable Listener listener
    ) {
        if (profileDoc == null || !profileDoc.exists()) {
            fetchAndRecord(context, uid, passwordForQuickLogin, listener);
            return;
        }
        persist(context, uid, profileDoc, passwordForQuickLogin, listener);
    }

    public static void fetchAndRecord(
            @NonNull Context context,
            @NonNull String uid,
            @Nullable String passwordForQuickLogin
    ) {
        fetchAndRecord(context, uid, passwordForQuickLogin, null);
    }

    public static void fetchAndRecord(
            @NonNull Context context,
            @NonNull String uid,
            @Nullable String passwordForQuickLogin,
            @Nullable Listener listener
    ) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> persist(context, uid, doc, passwordForQuickLogin, listener));
    }

    private static void persist(
            @NonNull Context context,
            @NonNull String uid,
            @NonNull DocumentSnapshot doc,
            @Nullable String passwordForQuickLogin,
            @Nullable Listener listener
    ) {
        String displayName = resolveDisplayNameFromDoc(doc);
        String phone = doc.getString("phone");
        String displayEmail = doc.getString("displayEmail");
        String emailField = doc.getString("email");
        String authUserEmail = null;
        com.google.firebase.auth.FirebaseUser authUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (authUser != null
                && uid.equals(authUser.getUid())
                && UserProfileBuilder.isRealEmail(authUser.getEmail())) {
            authUserEmail = authUser.getEmail();
        }
        // Prefer live Google/Auth email over a stale Firestore displayEmail.
        String email = UserProfileBuilder.isRealEmail(authUserEmail)
                ? authUserEmail
                : (UserProfileBuilder.isRealEmail(displayEmail)
                ? displayEmail
                : (UserProfileBuilder.isRealEmail(emailField) ? emailField : ""));
        if (UserProfileBuilder.isRealEmail(email)) {
            email = email.trim().toLowerCase(java.util.Locale.ROOT);
        }
        String avatarUrl = doc.getString("avatarUrl");
        String authProvider = resolveProvider(doc);
        boolean googleLinked = isGoogleLinked(doc);

        SavedAccount account = new SavedAccount(
                uid,
                displayName,
                email,
                phone == null ? "" : phone,
                avatarUrl == null ? "" : avatarUrl,
                authProvider,
                googleLinked
        );
        if (!SavedAccountStore.upsert(context, account)) {
            if (listener != null) {
                listener.onRejectedAccountLimit();
            }
            return;
        }
        if (!TextUtils.isEmpty(passwordForQuickLogin)) {
            SavedAccountStore.savePassword(context, uid, passwordForQuickLogin);
        }
        if (listener != null) {
            listener.onRecorded();
        }
    }

    /**
     * Phone-backed profiles use password quick-switch when possible.
     * Pure social profiles without phone stay on provider UI.
     */
    @NonNull
    private static String resolveProvider(@NonNull DocumentSnapshot doc) {
        String phone = doc.getString("phone");
        if (!TextUtils.isEmpty(phone)) {
            return SavedAccount.PROVIDER_PASSWORD;
        }
        String provider = doc.getString("authProvider");
        if (SavedAccount.PROVIDER_GOOGLE.equals(provider)) {
            return SavedAccount.PROVIDER_GOOGLE;
        }
        if (SavedAccount.PROVIDER_FACEBOOK.equals(provider)) {
            return SavedAccount.PROVIDER_FACEBOOK;
        }
        if (isGoogleLinked(doc)) {
            return SavedAccount.PROVIDER_GOOGLE;
        }
        return SavedAccount.PROVIDER_PASSWORD;
    }

    static boolean isGoogleLinked(@NonNull DocumentSnapshot doc) {
        Boolean linked = doc.getBoolean("googleLinked");
        return linked != null && linked;
    }

    @NonNull
    private static String resolveDisplayNameFromDoc(@NonNull DocumentSnapshot doc) {
        String fullName = doc.getString("fullName");
        if (!TextUtils.isEmpty(fullName)) {
            return fullName;
        }
        String username = doc.getString("username");
        if (!TextUtils.isEmpty(username)) {
            return "@" + username;
        }
        String phone = doc.getString("phone");
        if (!TextUtils.isEmpty(phone)) {
            return phone;
        }
        return "Tài khoản";
    }
}
