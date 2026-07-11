package com.example.healthup.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;

/**
 * Detects whether a phone already has a Firebase Auth account via synthetic
 * {@code phone@healthup.app} email. Uses create-then-delete probe because
 * {@code fetchSignInMethodsForEmail} is empty under email-enumeration protection.
 */
public final class SyntheticAuthProbe {

    public interface Callback {
        void onExists();

        void onNotExists();

        void onError(@NonNull Exception error);
    }

    private SyntheticAuthProbe() {
    }

    public static void probePhoneAccount(
            @NonNull FirebaseAuth firebaseAuth,
            @NonNull String normalizedPhone,
            @NonNull Callback callback
    ) {
        String syntheticEmail = normalizedPhone + UserProfileBuilder.SYNTHETIC_EMAIL_DOMAIN;
        String probePassword = UUID.randomUUID() + "Aa1!";
        firebaseAuth.createUserWithEmailAndPassword(syntheticEmail, probePassword)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            callback.onExists();
                        } else if (exception != null) {
                            callback.onError(exception);
                        } else {
                            callback.onError(new IllegalStateException("Auth probe failed"));
                        }
                        return;
                    }

                    FirebaseUser created = firebaseAuth.getCurrentUser();
                    if (created == null) {
                        callback.onError(new IllegalStateException("Auth probe created no user"));
                        return;
                    }

                    created.delete().addOnCompleteListener(deleteTask -> {
                        firebaseAuth.signOut();
                        if (deleteTask.isSuccessful()) {
                            callback.onNotExists();
                        } else {
                            Exception deleteError = deleteTask.getException();
                            callback.onError(deleteError != null
                                    ? deleteError
                                    : new IllegalStateException("Auth probe cleanup failed"));
                        }
                    });
                });
    }

    @NonNull
    public static String syntheticEmailForPhone(@NonNull String normalizedPhone) {
        return normalizedPhone + UserProfileBuilder.SYNTHETIC_EMAIL_DOMAIN;
    }
}
