package com.example.healthup.auth;

import androidx.annotation.NonNull;

import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Removes synthetic {@code phone@healthup.app} Auth orphans when Firestore has no profile.
 * Spark-compatible: signs in with the derived app secret, verifies Firestore is still empty,
 * then deletes the Auth user — no Cloud Functions required.
 */
public final class AuthOrphanCleaner {

    public interface Callback {
        void onDeleted();

        void onNotFound();

        void onProfileExists();

        void onError(@NonNull Exception error);
    }

    private AuthOrphanCleaner() {
    }

    public static void deleteSyntheticAuthOrphan(
            @NonNull String phone,
            @NonNull Callback callback
    ) {
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        if (normalizedPhone.isEmpty()) {
            callback.onError(new IllegalArgumentException("Invalid phone"));
            return;
        }

        UserPhoneLookup.queryUsers(normalizedPhone)
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        callback.onProfileExists();
                        return;
                    }
                    signInAndDeleteOrphan(normalizedPhone, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private static void signInAndDeleteOrphan(
            @NonNull String normalizedPhone,
            @NonNull Callback callback
    ) {
        String syntheticEmail = SyntheticAuthProbe.syntheticEmailForPhone(normalizedPhone);
        String authSecret = AppPasswordHelper.authSecretForPhone(normalizedPhone);
        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.signInWithEmailAndPassword(syntheticEmail, authSecret)
                .addOnCompleteListener(signInTask -> {
                    if (!signInTask.isSuccessful()) {
                        Exception error = signInTask.getException();
                        if (error instanceof FirebaseAuthInvalidUserException) {
                            callback.onNotFound();
                        } else {
                            callback.onError(error != null
                                    ? error
                                    : new IllegalStateException("Orphan sign-in failed"));
                        }
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        auth.signOut();
                        callback.onError(new IllegalStateException("No user after orphan sign-in"));
                        return;
                    }

                    UserPhoneLookup.queryUsers(normalizedPhone)
                            .addOnSuccessListener(requery -> {
                                if (!requery.isEmpty()) {
                                    auth.signOut();
                                    callback.onProfileExists();
                                    return;
                                }

                                user.delete().addOnCompleteListener(deleteTask -> {
                                    auth.signOut();
                                    if (deleteTask.isSuccessful()) {
                                        callback.onDeleted();
                                    } else {
                                        Exception deleteError = deleteTask.getException();
                                        callback.onError(deleteError != null
                                                ? deleteError
                                                : new IllegalStateException("Orphan delete failed"));
                                    }
                                });
                            })
                            .addOnFailureListener(requeryError -> {
                                auth.signOut();
                                callback.onError(requeryError);
                            });
                });
    }
}
