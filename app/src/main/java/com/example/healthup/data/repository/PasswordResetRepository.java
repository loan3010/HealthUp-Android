package com.example.healthup.data.repository;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.FirebaseAuthErrorMapper;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * Forgot password (product flow):
 * <ol>
 *   <li>Firestore OTP proves phone ownership (email not required)</li>
 *   <li>User enters new password</li>
 *   <li>Callable {@code resetPassword} updates Firebase Auth via Admin SDK</li>
 * </ol>
 * Email is optional on accounts — reset must not require it.
 */
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

    private final OtpRepository otpRepository;
    private final FirebaseFunctions functions;

    public PasswordResetRepository() {
        this(new OtpRepository(), FirebaseFunctions.getInstance());
    }

    public PasswordResetRepository(OtpRepository otpRepository, FirebaseFunctions functions) {
        this.otpRepository = otpRepository;
        this.functions = functions;
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
                                String email = userDoc.getString("email");
                                callResetPasswordFunction(
                                        normalizedPhone, uid, email, newPassword, callback);
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

    private void callResetPasswordFunction(
            @NonNull String phone,
            @NonNull String uid,
            @Nullable String email,
            @NonNull String newPassword,
            @NonNull ResetPasswordCallback callback
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("newPassword", newPassword);
        data.put("uid", uid);
        if (!TextUtils.isEmpty(email)) {
            data.put("email", email);
        }

        functions.getHttpsCallable("resetPassword")
                .call(data)
                .addOnSuccessListener(unused -> {
                    otpRepository.deletePasswordResetDoc(phone);
                    callback.onResult(ResetPasswordResult.SUCCESS, "");
                })
                .addOnFailureListener(e ->
                        callback.onResult(
                                ResetPasswordResult.ERROR,
                                FirebaseAuthErrorMapper.map(e)));
    }

    /** Kept for deep-link / legacy email-reset handlers; no-op in phone-OTP flow. */
    public void confirmPendingResetWithOobCode(
            @NonNull Context context,
            @NonNull String oobCode,
            @NonNull ResetPasswordCallback callback
    ) {
        callback.onResult(ResetPasswordResult.ERROR, "session_invalid");
    }

    public void submitResetSmsCode(@NonNull String smsCode) {
        // unused — product flow uses Firestore OTP only
    }

    public void cancelResetVerification() {
        // unused
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
