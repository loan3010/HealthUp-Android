package com.example.healthup.data.repository;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.example.healthup.auth.PhoneAuthHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class FirebaseAuthRepository {

    private final FirebaseAuth firebaseAuth;

    @Nullable
    private String pendingVerificationId;
    @Nullable
    private String pendingExpectedUid;
    @Nullable
    private String pendingNewPassword;
    @Nullable
    private PasswordUpdateCallback pendingCallback;

    public FirebaseAuthRepository() {
        this(FirebaseAuth.getInstance());
    }

    public FirebaseAuthRepository(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public interface PasswordUpdateCallback {
        void onSuccess();

        void onError(@NonNull String errorCode);
    }

    public interface PhoneVerificationCallback {
        void onSmsCodeRequired();
    }

    /**
     * Verifies phone ownership via Firebase Phone Auth, signs in, then updates password.
     * Works on Spark plan without Cloud Functions when the phone is linked to the account.
     */
    public void updatePasswordForPhone(
            @NonNull String phoneE164,
            @NonNull String expectedUid,
            @NonNull String newPassword,
            @NonNull Activity activity,
            @NonNull PhoneVerificationCallback verificationCallback,
            @NonNull PasswordUpdateCallback callback
    ) {
        clearPendingState();
        pendingExpectedUid = expectedUid;
        pendingNewPassword = newPassword;
        pendingCallback = callback;

        PhoneAuthHelper.startVerification(
                firebaseAuth,
                activity,
                phoneE164,
                new PhoneAuthHelper.Callbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        signInAndUpdatePassword(credential);
                    }

                    @Override
                    public void onCodeSent(
                            @NonNull String verificationId,
                            @NonNull PhoneAuthProvider.ForceResendingToken resendToken
                    ) {
                        pendingVerificationId = verificationId;
                        verificationCallback.onSmsCodeRequired();
                    }

                    @Override
                    public void onVerificationFailed(@NonNull Exception error) {
                        finishWithError(FirebaseAuthErrorMapper.map(error));
                    }
                }
        );
    }

    public void submitSmsCodeAndUpdatePassword(@NonNull String smsCode) {
        String verificationId = pendingVerificationId;
        if (verificationId == null || pendingCallback == null) {
            return;
        }

        String normalizedCode = PhoneAuthHelper.normalizeSmsCode(smsCode);
        if (normalizedCode == null) {
            finishWithError("invalid_sms_code");
            return;
        }

        signInAndUpdatePassword(PhoneAuthHelper.buildCredential(verificationId, normalizedCode));
    }

    public void cancelPendingVerification() {
        clearPendingState();
    }

    public void linkPhoneToCurrentUser(
            @NonNull Activity activity,
            @NonNull String phoneE164,
            @NonNull Runnable onLinked,
            @NonNull Runnable onSkipped
    ) {
        PhoneAuthHelper.startVerification(
                firebaseAuth,
                activity,
                phoneE164,
                new PhoneAuthHelper.Callbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        linkCredential(credential, onLinked, onSkipped);
                    }

                    @Override
                    public void onCodeSent(
                            @NonNull String verificationId,
                            @NonNull PhoneAuthProvider.ForceResendingToken resendToken
                    ) {
                        // Registration keeps going even if Firebase SMS is not entered.
                        onSkipped.run();
                    }

                    @Override
                    public void onVerificationFailed(@NonNull Exception error) {
                        onSkipped.run();
                    }
                }
        );
    }

    public void linkPhoneWithSmsCode(
            @NonNull String verificationId,
            @NonNull String smsCode,
            @NonNull Runnable onLinked,
            @NonNull Runnable onSkipped
    ) {
        String normalizedCode = PhoneAuthHelper.normalizeSmsCode(smsCode);
        if (normalizedCode == null) {
            onSkipped.run();
            return;
        }
        linkCredential(
                PhoneAuthHelper.buildCredential(verificationId, normalizedCode),
                onLinked,
                onSkipped
        );
    }

    private void linkCredential(
            @NonNull PhoneAuthCredential credential,
            @NonNull Runnable onLinked,
            @NonNull Runnable onSkipped
    ) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            onSkipped.run();
            return;
        }

        user.linkWithCredential(credential)
                .addOnSuccessListener(unused -> onLinked.run())
                .addOnFailureListener(unused -> onSkipped.run());
    }

    private void signInAndUpdatePassword(@NonNull PhoneAuthCredential credential) {
        PasswordUpdateCallback callback = pendingCallback;
        String expectedUid = pendingExpectedUid;
        String newPassword = pendingNewPassword;

        if (callback == null || expectedUid == null || newPassword == null) {
            return;
        }

        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        finishWithError(FirebaseAuthErrorMapper.map(task.getException()));
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null || !expectedUid.equals(user.getUid())) {
                        firebaseAuth.signOut();
                        finishWithError("phone_not_linked");
                        return;
                    }

                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused -> {
                                firebaseAuth.signOut();
                                clearPendingState();
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                firebaseAuth.signOut();
                                finishWithError(FirebaseAuthErrorMapper.map(e));
                            });
                });
    }

    private void finishWithError(@NonNull String errorCode) {
        PasswordUpdateCallback callback = pendingCallback;
        clearPendingState();
        if (callback != null) {
            callback.onError(errorCode);
        }
    }

    private void clearPendingState() {
        pendingVerificationId = null;
        pendingExpectedUid = null;
        pendingNewPassword = null;
        pendingCallback = null;
    }
}
