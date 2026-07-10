package com.example.healthup.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

public class RegistrationRepository {

    public enum SendOtpResult {
        SUCCESS,
        ERROR
    }

    public enum VerifyOtpResult {
        SUCCESS,
        WRONG_OTP,
        EXPIRED,
        ERROR
    }

    private final OtpRepository otpRepository;

    public RegistrationRepository() {
        this(new OtpRepository());
    }

    public RegistrationRepository(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    public interface SendOtpCallback {
        void onResult(SendOtpResult result, @NonNull String otpForDebug);
    }

    public interface VerifyOtpCallback {
        void onResult(VerifyOtpResult result);
    }

    public interface DebugOtpCallback {
        void onOtp(@NonNull String otp);
    }

    public void sendOtp(@NonNull String phone, @NonNull SendOtpCallback callback) {
        String otp = otpRepository.generateOtp();
        otpRepository.saveRegistrationOtp(phone, otp)
                .addOnSuccessListener(unused -> callback.onResult(SendOtpResult.SUCCESS, otp))
                .addOnFailureListener(e -> callback.onResult(SendOtpResult.ERROR, e.getMessage() != null ? e.getMessage() : "Unknown error"));
    }

    public void resendOtp(@NonNull String phone, @NonNull SendOtpCallback callback) {
        sendOtp(phone, callback);
    }

    public void verifyOtp(@NonNull String phone, @NonNull String inputOtp, @NonNull VerifyOtpCallback callback) {
        otpRepository.getRegistrationDoc(phone)
                .addOnSuccessListener(doc -> callback.onResult(evaluateOtp(doc, inputOtp)))
                .addOnFailureListener(e -> callback.onResult(VerifyOtpResult.ERROR));
    }

    public void getDebugOtp(@NonNull String phone, @NonNull DebugOtpCallback callback) {
        otpRepository.getRegistrationDoc(phone)
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

    public void deleteOtpDoc(@NonNull String phone) {
        otpRepository.deleteRegistrationDoc(phone);
    }

    private VerifyOtpResult evaluateOtp(DocumentSnapshot doc, @NonNull String inputOtp) {
        if (!doc.exists()) {
            return VerifyOtpResult.EXPIRED;
        }
        if (otpRepository.isOtpExpired(doc)) {
            return VerifyOtpResult.EXPIRED;
        }
        if (!otpRepository.matchesOtp(doc, inputOtp)) {
            return VerifyOtpResult.WRONG_OTP;
        }
        return VerifyOtpResult.SUCCESS;
    }
}
