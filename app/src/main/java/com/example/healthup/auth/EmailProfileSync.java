package com.example.healthup.auth;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Writes verified real email from Auth into Firestore users/{uid}. */
public final class EmailProfileSync {

    public interface Callback {
        void onSuccess();

        void onError(@NonNull String message);
    }

    private EmailProfileSync() {
    }

    public static void syncFromAuthUser(
            @NonNull FirebaseUser user,
            @NonNull String expectedEmail,
            @NonNull Callback callback
    ) {
        String authEmail = user.getEmail();
        if (TextUtils.isEmpty(authEmail)
                || !authEmail.equalsIgnoreCase(expectedEmail)
                || !user.isEmailVerified()
                || !UserProfileBuilder.isRealEmail(authEmail)) {
            callback.onError("not_matched");
            return;
        }
        write(user.getUid(), authEmail, callback);
    }

    public static void syncIfAuthHasRealEmail(@NonNull FirebaseUser user, @NonNull Callback callback) {
        String authEmail = user.getEmail();
        if (!user.isEmailVerified() || !UserProfileBuilder.isRealEmail(authEmail)) {
            callback.onError("not_matched");
            return;
        }
        write(user.getUid(), authEmail, callback);
    }

    @NonNull
    public static Task<Void> writeTask(@NonNull String uid, @NonNull String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", normalized);
        updates.put("displayEmail", normalized);
        updates.put("emailVerified", true);
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    private static void write(@NonNull String uid, @NonNull String email, @NonNull Callback callback) {
        writeTask(uid, email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "firestore_error";
                    callback.onError(msg);
                });
    }
}
