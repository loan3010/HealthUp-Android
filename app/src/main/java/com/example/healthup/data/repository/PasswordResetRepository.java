package com.example.healthup.data.repository;

import android.app.Activity;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class PasswordResetRepository {

    public enum SendOtpResult {
        SUCCESS,
        PHONE_NOT_REGISTERED,
        ERROR
    }

    public enum VerifyOtpResult {
        SUCCESS,
        WRONG_OTP,
        EXPIRED,
        ERROR
    }

    public enum ResetPasswordResult {
        SUCCESS,
        SESSION_INVALID,
        USER_NOT_FOUND,
        ERROR
    }

    private final FirestoreRepository firestoreRepository;
    private final OtpRepository otpRepository;
    private final FirebaseAuthRepository firebaseAuthRepository;

    public PasswordResetRepository() {
        this(new FirestoreRepository(), new OtpRepository(), new FirebaseAuthRepository());
    }

    public PasswordResetRepository(
            FirestoreRepository firestoreRepository,
            OtpRepository otpRepository,
            FirebaseAuthRepository firebaseAuthRepository
    ) {
        this.firestoreRepository = firestoreRepository;
        this.otpRepository = otpRepository;
        this.firebaseAuthRepository = firebaseAuthRepository;
    }

    public interface SendOtpCallback {
        void onResult(SendOtpResult result, @NonNull String otpForDebug);
    }

    public interface VerifyOtpCallback {
        void onResult(VerifyOtpResult result);
    }

    public interface ResetPasswordCallback {
        void onResult(ResetPasswordResult result, @NonNull String errorMessage);
    }

    public void sendOtp(@NonNull String phone, @NonNull SendOtpCallback callback) {
        firestoreRepository.findUserByPhone(phone)
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onResult(SendOtpResult.PHONE_NOT_REGISTERED, "");
                        return;
                    }

                    String otp = otpRepository.generateOtp();
                    otpRepository.savePasswordResetOtp(phone, otp)
                            .addOnSuccessListener(unused ->
                                    callback.onResult(SendOtpResult.SUCCESS, otp))
                            .addOnFailureListener(e ->
                                    callback.onResult(SendOtpResult.ERROR, ""));
                })
                .addOnFailureListener(e -> callback.onResult(SendOtpResult.ERROR, ""));
    }

    public void resendOtp(@NonNull String phone, @NonNull SendOtpCallback callback) {
        sendOtp(phone, callback);
    }

    public void verifyOtp(@NonNull String phone, @NonNull String inputOtp, @NonNull VerifyOtpCallback callback) {
        otpRepository.getPasswordResetDoc(phone)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(VerifyOtpResult.EXPIRED);
                        return;
                    }
                    if (otpRepository.isOtpExpired(doc)) {
                        callback.onResult(VerifyOtpResult.EXPIRED);
                        return;
                    }
                    if (!otpRepository.matchesOtp(doc, inputOtp)) {
                        callback.onResult(VerifyOtpResult.WRONG_OTP);
                        return;
                    }

                    otpRepository.markVerified(phone)
                            .addOnSuccessListener(unused -> callback.onResult(VerifyOtpResult.SUCCESS))
                            .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
                })
                .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
    }

    public void completePasswordReset(
            @NonNull String phone,
            @NonNull String localPhoneE164,
            @NonNull String newPassword,
            @NonNull Activity activity,
            @NonNull ResetPasswordCallback callback
    ) {
        otpRepository.getPasswordResetDoc(phone)
                .addOnSuccessListener(resetDoc -> {
                    if (!otpRepository.isResetSessionValid(resetDoc)) {
                        callback.onResult(ResetPasswordResult.SESSION_INVALID, "");
                        return;
                    }

                    firestoreRepository.findUserByPhone(phone)
                            .addOnSuccessListener(userQuery -> {
                                if (userQuery.isEmpty()) {
                                    callback.onResult(ResetPasswordResult.USER_NOT_FOUND, "");
                                    return;
                                }

                                QueryDocumentSnapshot userDoc =
                                        (QueryDocumentSnapshot) userQuery.getDocuments().get(0);
                                String email = userDoc.getString("email");
                                if (TextUtils.isEmpty(email)) {
                                    callback.onResult(ResetPasswordResult.ERROR, "user_not_found");
                                    return;
                                }

                                firebaseAuthRepository.updatePasswordForPhone(
                                        phone,
                                        localPhoneE164,
                                        newPassword,
                                        activity,
                                        new FirebaseAuthRepository.PasswordUpdateCallback() {
                                            @Override
                                            public void onSuccess() {
                                                otpRepository.deletePasswordResetDoc(phone);
                                                callback.onResult(ResetPasswordResult.SUCCESS, "");
                                            }

                                            @Override
                                            public void onError(String errorCode) {
                                                callback.onResult(ResetPasswordResult.ERROR, errorCode);
                                            }
                                        }
                                );
                            })
                            .addOnFailureListener(e ->
                                    callback.onResult(
                                            ResetPasswordResult.ERROR,
                                            FirebaseAuthErrorMapper.map(e)));
                })
                .addOnFailureListener(e ->
                        callback.onResult(
                                ResetPasswordResult.ERROR,
                                FirebaseAuthErrorMapper.map(e)));
    }

    public void getDebugOtp(@NonNull String phone, @NonNull DebugOtpCallback callback) {
        otpRepository.getPasswordResetDoc(phone)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String otp = doc.getString("otp");
                        callback.onOtp(otp != null ? otp : "");
                    } else {
                        callback.onOtp("");
                    }
                })
                .addOnFailureListener(e -> callback.onOtp(""));
    }

    public interface DebugOtpCallback {
        void onOtp(@NonNull String otp);
    }
}
