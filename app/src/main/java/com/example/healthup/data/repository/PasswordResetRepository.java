package com.example.healthup.data.repository;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Map;

/**
 * Forgot password on Spark (no Cloud Functions):
 * Firestore OTP → write {@code passwordHash} on the user profile.
 * Auth password stays the derived phone secret (unchanged).
 */
public class PasswordResetRepository {

    public enum SendOtpResult {
        SUCCESS,
        PHONE_NOT_REGISTERED,
        /** Phone belongs to a Google-linked account — reset via Google login instead. */
        GOOGLE_LINKED,
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

    private final OtpRepository otpRepository;
    private final FirebaseFirestore firestore;

    public PasswordResetRepository() {
        this(new OtpRepository(), FirebaseFirestore.getInstance());
    }

    public PasswordResetRepository(OtpRepository otpRepository, FirebaseFirestore firestore) {
        this.otpRepository = otpRepository;
        this.firestore = firestore;
    }

    public interface SendOtpCallback {
        void onResult(SendOtpResult result, @NonNull String otpForDebug);
    }

    public interface VerifyOtpCallback {
        void onResult(VerifyOtpResult result);
    }

    public interface ResetPasswordCallback {
        void onResult(ResetPasswordResult result, @NonNull String errorMessage);

        default void onSmsCodeRequired() {
        }
    }

    public void sendOtp(@NonNull String phone, @NonNull SendOtpCallback callback) {
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        if (normalizedPhone.isEmpty()) {
            callback.onResult(SendOtpResult.ERROR, "");
            return;
        }

        UserPhoneLookup.queryUsers(normalizedPhone)
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onResult(SendOtpResult.PHONE_NOT_REGISTERED, "");
                        return;
                    }

                    QueryDocumentSnapshot userDoc =
                            (QueryDocumentSnapshot) query.getDocuments().get(0);
                    Boolean googleLinked = userDoc.getBoolean("googleLinked");
                    if (googleLinked != null && googleLinked) {
                        callback.onResult(SendOtpResult.GOOGLE_LINKED, "");
                        return;
                    }

                    String otp = otpRepository.generateOtp();
                    otpRepository.savePasswordResetOtp(normalizedPhone, otp)
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
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        otpRepository.getPasswordResetDoc(normalizedPhone)
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

                    otpRepository.markVerified(normalizedPhone)
                            .addOnSuccessListener(unused -> callback.onResult(VerifyOtpResult.SUCCESS))
                            .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
                })
                .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
    }

    public void completePasswordReset(
            @NonNull Context context,
            @NonNull String phone,
            @NonNull String localPhoneE164,
            @NonNull String newPassword,
            @Nullable Activity activity,
            @NonNull ResetPasswordCallback callback
    ) {
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        otpRepository.getPasswordResetDoc(normalizedPhone)
                .addOnSuccessListener(resetDoc -> {
                    if (!otpRepository.isResetSessionValid(resetDoc)) {
                        callback.onResult(ResetPasswordResult.SESSION_INVALID, "");
                        return;
                    }

                    UserPhoneLookup.queryUsers(normalizedPhone)
                            .addOnSuccessListener(userQuery -> {
                                if (userQuery.isEmpty()) {
                                    callback.onResult(ResetPasswordResult.USER_NOT_FOUND, "");
                                    return;
                                }

                                QueryDocumentSnapshot userDoc =
                                        (QueryDocumentSnapshot) userQuery.getDocuments().get(0);
                                String uid = userDoc.getId();
                                Map<String, Object> updates =
                                        AppPasswordHelper.passwordFieldsForNewPassword(newPassword);

                                firestore.collection("users").document(uid)
                                        .update(updates)
                                        .addOnSuccessListener(unused -> {
                                            otpRepository.deletePasswordResetDoc(normalizedPhone);
                                            callback.onResult(ResetPasswordResult.SUCCESS, "");
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
                })
                .addOnFailureListener(e ->
                        callback.onResult(
                                ResetPasswordResult.ERROR,
                                FirebaseAuthErrorMapper.map(e)));
    }

    public void confirmPendingResetWithOobCode(
            @NonNull Context context,
            @NonNull String oobCode,
            @NonNull ResetPasswordCallback callback
    ) {
        callback.onResult(ResetPasswordResult.ERROR, "session_invalid");
    }

    public void submitResetSmsCode(@NonNull String smsCode) {
    }

    public void cancelResetVerification() {
    }

    public void getDebugOtp(@NonNull String phone, @NonNull DebugOtpCallback callback) {
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        otpRepository.getPasswordResetDoc(normalizedPhone)
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
