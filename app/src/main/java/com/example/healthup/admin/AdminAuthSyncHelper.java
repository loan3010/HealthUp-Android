package com.example.healthup.admin;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.BuildConfig;
import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.data.repository.OtpRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

/**
 * Best-effort synthetic Auth session after admin password reset.
 * Must not block the reset UI — login provisions the session if this fails.
 */
final class AdminAuthSyncHelper {

    private static final String TAG = "AdminAuthSyncHelper";

    private AdminAuthSyncHelper() {
    }

    /** Fire-and-forget; never blocks reset success UI. */
    static void syncAfterPasswordReset(@NonNull String adminUid) {
        String syntheticEmail = AppPasswordHelper.syntheticAuthEmailForAdmin(adminUid);
        String derivedSecret = AppPasswordHelper.authSecretForAdminEmail(syntheticEmail);
        FirebaseAuth auth = FirebaseAuth.getInstance();

        probeAdminAuth(auth, syntheticEmail, derivedSecret)
                .continueWithTask(probeTask -> {
                    if (probeTask.isSuccessful()) {
                        return deleteResetDoc(adminUid);
                    }
                    return auth.createUserWithEmailAndPassword(syntheticEmail, derivedSecret)
                            .continueWithTask(createTask -> {
                                if (createTask.isSuccessful()) {
                                    auth.signOut();
                                    return deleteResetDoc(adminUid);
                                }
                                Exception createError = createTask.getException();
                                if (createError instanceof FirebaseAuthUserCollisionException) {
                                    // Synthetic account already exists — login will open session.
                                    auth.signOut();
                                    return deleteResetDoc(adminUid);
                                }
                                return Tasks.forException(createError != null
                                        ? createError
                                        : new IllegalStateException("Admin Auth create failed"));
                            });
                })
                .addOnFailureListener(e ->
                        logDebug("Admin auth sync skipped (login will retry)", e));
    }

    private static Task<Void> probeAdminAuth(
            @NonNull FirebaseAuth auth,
            @NonNull String email,
            @NonNull String derivedSecret
    ) {
        return auth.signInWithEmailAndPassword(email, derivedSecret)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        return Tasks.forException(error != null
                                ? error
                                : new IllegalStateException("Admin auth probe failed"));
                    }
                    auth.signOut();
                    return Tasks.forResult(null);
                });
    }

    private static Task<Void> deleteResetDoc(@NonNull String adminUid) {
        return new OtpRepository().deleteAdminPasswordResetDoc(adminUid);
    }

    private static void logDebug(@NonNull String message, @Nullable Throwable throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, throwable);
        }
    }
}
