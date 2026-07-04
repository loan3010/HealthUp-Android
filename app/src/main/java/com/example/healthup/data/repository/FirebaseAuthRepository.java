package com.example.healthup.data.repository;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FirebaseAuthRepository {

    private static final long PHONE_AUTH_TIMEOUT_SECONDS = 60L;

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFunctions firebaseFunctions;

    public FirebaseAuthRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFunctions.getInstance());
    }

    public FirebaseAuthRepository(FirebaseAuth firebaseAuth, FirebaseFunctions firebaseFunctions) {
        this.firebaseAuth = firebaseAuth;
        this.firebaseFunctions = firebaseFunctions;
    }

    public interface PasswordUpdateCallback {
        void onSuccess();

        void onError(@NonNull String errorCode);
    }

    /**
     * Attempts Cloud Function reset first, then falls back to Phone Auth sign-in + updatePassword.
     *
     * @param localPhone local VN phone used as Firestore document key (09xxxxxxxx)
     * @param phoneE164  E.164 phone for Firebase Phone Auth fallback (+849xxxxxxxx)
     */
    public void updatePasswordForPhone(
            @NonNull String localPhone,
            @NonNull String phoneE164,
            @NonNull String newPassword,
            @NonNull Activity activity,
            @NonNull PasswordUpdateCallback callback
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("phone", localPhone);
        data.put("newPassword", newPassword);

        firebaseFunctions.getHttpsCallable("resetPassword")
                .call(data)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    String errorCode = FirebaseAuthErrorMapper.map(e);
                    if ("function_not_deployed".equals(errorCode)) {
                        updatePasswordViaPhoneAuth(phoneE164, newPassword, activity, callback);
                    } else {
                        callback.onError(errorCode);
                    }
                });
    }

    private void updatePasswordViaPhoneAuth(
            @NonNull String phoneE164,
            @NonNull String newPassword,
            @NonNull Activity activity,
            @NonNull PasswordUpdateCallback callback
    ) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneE164)
                .setTimeout(PHONE_AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        signInAndUpdatePassword(credential, newPassword, callback);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull com.google.firebase.FirebaseException e) {
                        callback.onError(FirebaseAuthErrorMapper.map(e));
                    }

                    @Override
                    public void onCodeSent(
                            @NonNull String verificationId,
                            @NonNull PhoneAuthProvider.ForceResendingToken token
                    ) {
                        callback.onError("function_not_deployed");
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void signInAndUpdatePassword(
            @NonNull PhoneAuthCredential credential,
            @NonNull String newPassword,
            @NonNull PasswordUpdateCallback callback
    ) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onError(FirebaseAuthErrorMapper.map(task.getException()));
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        callback.onError("user_not_found");
                        return;
                    }

                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused -> {
                                firebaseAuth.signOut();
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                firebaseAuth.signOut();
                                callback.onError(FirebaseAuthErrorMapper.map(e));
                            });
                });
    }
}
