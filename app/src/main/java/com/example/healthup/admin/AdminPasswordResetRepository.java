package com.example.healthup.admin;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.util.AdminEmailLookup;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Map;

/**
 * Admin forgot password on Spark: Firestore email OTP → update {@code passwordHash} on admin profile.
 */
public class AdminPasswordResetRepository {

    public enum SendOtpResult {
        SUCCESS,
        EMAIL_NOT_REGISTERED,
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
        AUTH_SYNC_FAILED,
        ERROR
    }

    private final OtpRepository otpRepository;
    private final FirebaseFirestore firestore;

    public AdminPasswordResetRepository() {
        this(new OtpRepository(), FirebaseFirestore.getInstance());
    }

    public AdminPasswordResetRepository(OtpRepository otpRepository, FirebaseFirestore firestore) {
        this.otpRepository = otpRepository;
        this.firestore = firestore;
    }

    public interface SendOtpCallback {
        void onResult(SendOtpResult result, @NonNull String otpForDebug, @NonNull String adminUid);
    }

    public interface VerifyOtpCallback {
        void onResult(VerifyOtpResult result);
    }

    public interface ResetPasswordCallback {
        void onResult(ResetPasswordResult result, @NonNull String errorMessage);
    }

    public void sendOtp(@NonNull String email, @NonNull SendOtpCallback callback) {
        String normalizedEmail = AdminEmailLookup.normalize(email);
        if (!AdminEmailLookup.isValidEmail(normalizedEmail)) {
            callback.onResult(SendOtpResult.ERROR, "", "");
            return;
        }

        AdminEmailLookup.findAdminByEmail(normalizedEmail)
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc == null) {
                        callback.onResult(SendOtpResult.EMAIL_NOT_REGISTERED, "", "");
                        return;
                    }
                    String otp = otpRepository.generateOtp();
                    String authEmail = AdminEmailLookup.resolveAuthEmail(adminDoc, normalizedEmail);
                    otpRepository.saveAdminPasswordResetOtp(adminDoc.getId(), authEmail, otp)
                            .addOnSuccessListener(unused ->
                                    callback.onResult(SendOtpResult.SUCCESS, otp, adminDoc.getId()))
                            .addOnFailureListener(e ->
                                    callback.onResult(SendOtpResult.ERROR, "", ""));
                })
                .addOnFailureListener(e -> callback.onResult(SendOtpResult.ERROR, "", ""));
    }

    public void resendOtp(@NonNull String email, @NonNull SendOtpCallback callback) {
        sendOtp(email, callback);
    }

    public void verifyOtp(@NonNull String adminUid, @NonNull String inputOtp, @NonNull VerifyOtpCallback callback) {
        otpRepository.getAdminPasswordResetDoc(adminUid)
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
                    otpRepository.markAdminVerified(adminUid)
                            .addOnSuccessListener(unused -> callback.onResult(VerifyOtpResult.SUCCESS))
                            .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
                })
                .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
    }

    public void completePasswordReset(
            @NonNull String adminUid,
            @NonNull String newPassword,
            @NonNull ResetPasswordCallback callback
    ) {
        otpRepository.getAdminPasswordResetDoc(adminUid)
                .addOnSuccessListener(resetDoc -> {
                    if (!otpRepository.isResetSessionValid(resetDoc)) {
                        callback.onResult(ResetPasswordResult.SESSION_INVALID, "");
                        return;
                    }

                    firestore.collection("users").document(adminUid)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (!userDoc.exists()
                                        || !com.example.healthup.util.StaffRoleHelper.isAdmin(
                                                com.example.healthup.util.StaffRoleHelper.resolveRole(userDoc))) {
                                    callback.onResult(ResetPasswordResult.USER_NOT_FOUND, "");
                                    return;
                                }

                                Map<String, Object> updates =
                                        AppPasswordHelper.passwordFieldsForNewPassword(newPassword);
                                firestore.collection("users").document(adminUid)
                                        .update(updates)
                                        .addOnSuccessListener(unused -> {
                                            AdminAuthSyncHelper.syncAfterPasswordReset(adminUid);
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

    public void getDebugOtp(@NonNull String adminUid, @NonNull DebugOtpCallback callback) {
        otpRepository.getAdminPasswordResetDoc(adminUid)
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
