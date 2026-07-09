package com.example.healthup.auth;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public final class PhoneAuthHelper {

    public static final long TIMEOUT_SECONDS = 60L;

    public interface Callbacks {
        void onVerificationCompleted(@NonNull PhoneAuthCredential credential);

        void onCodeSent(
                @NonNull String verificationId,
                @NonNull PhoneAuthProvider.ForceResendingToken resendToken
        );

        void onVerificationFailed(@NonNull Exception error);
    }

    private PhoneAuthHelper() {
    }

    public static void startVerification(
            @NonNull FirebaseAuth firebaseAuth,
            @NonNull Activity activity,
            @NonNull String phoneE164,
            @NonNull Callbacks callbacks
    ) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneE164)
                .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        callbacks.onVerificationCompleted(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callbacks.onVerificationFailed(e);
                    }

                    @Override
                    public void onCodeSent(
                            @NonNull String verificationId,
                            @NonNull PhoneAuthProvider.ForceResendingToken token
                    ) {
                        callbacks.onCodeSent(verificationId, token);
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @NonNull
    public static PhoneAuthCredential buildCredential(
            @NonNull String verificationId,
            @NonNull String smsCode
    ) {
        return PhoneAuthProvider.getCredential(verificationId, smsCode.trim());
    }

    @Nullable
    public static String normalizeSmsCode(@Nullable String code) {
        if (code == null) {
            return null;
        }
        String digits = code.replaceAll("[^0-9]", "");
        return digits.length() == 6 ? digits : null;
    }
}
