package com.example.healthup.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class OtpRepository {

    public static final String COLLECTION_PASSWORD_RESET = "password_reset";
    public static final String COLLECTION_ADMIN_PASSWORD_RESET = "admin_password_reset";
    public static final String COLLECTION_REGISTRATION = "registration_otp";
    public static final String COLLECTION_EMAIL_VERIFICATION = "email_verification";
    public static final long OTP_EXPIRY_MS = 5 * 60 * 1000L;
    /** Admin forgot-password OTP validity (60s — matches UI countdown). */
    public static final long ADMIN_OTP_EXPIRY_MS = 60 * 1000L;
    public static final long RESET_SESSION_MS = 15 * 60 * 1000L;

    private final FirebaseFirestore firestore;
    private final SecureRandom random = new SecureRandom();

    public OtpRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public OtpRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public String generateOtp() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public Task<Void> savePasswordResetOtp(@NonNull String phone, @NonNull String otp) {
        return saveOtp(COLLECTION_PASSWORD_RESET, phone, otp);
    }

    public Task<Void> saveAdminPasswordResetOtp(
            @NonNull String adminUid,
            @NonNull String email,
            @NonNull String otp
    ) {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("uid", adminUid);
        data.put("email", email);
        data.put("otp", otp);
        data.put("createdAt", now);
        data.put("expiredAt", now + ADMIN_OTP_EXPIRY_MS);
        data.put("verified", false);
        return firestore.collection(COLLECTION_ADMIN_PASSWORD_RESET)
                .document(adminUid)
                .set(data);
    }

    public Task<Void> saveRegistrationOtp(@NonNull String phone, @NonNull String otp) {
        return saveOtp(COLLECTION_REGISTRATION, phone, otp);
    }

    public Task<Void> saveEmailVerificationOtp(
            @NonNull String uid,
            @NonNull String email,
            @NonNull String otp
    ) {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("otp", otp);
        data.put("createdAt", now);
        data.put("expiredAt", now + OTP_EXPIRY_MS);
        data.put("verified", false);
        return firestore.collection(COLLECTION_EMAIL_VERIFICATION)
                .document(uid)
                .set(data);
    }

    public Task<DocumentSnapshot> getEmailVerificationDoc(@NonNull String uid) {
        return firestore.collection(COLLECTION_EMAIL_VERIFICATION)
                .document(uid)
                .get();
    }

    public Task<Void> deleteEmailVerificationDoc(@NonNull String uid) {
        return firestore.collection(COLLECTION_EMAIL_VERIFICATION)
                .document(uid)
                .delete();
    }

    private Task<Void> saveOtp(@NonNull String collection, @NonNull String phone, @NonNull String otp) {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("otp", otp);
        data.put("createdAt", now);
        data.put("expiredAt", now + OTP_EXPIRY_MS);
        data.put("verified", false);

        return firestore.collection(collection)
                .document(phone)
                .set(data);
    }

    public Task<DocumentSnapshot> getPasswordResetDoc(@NonNull String phone) {
        return getOtpDoc(COLLECTION_PASSWORD_RESET, phone);
    }

    public Task<DocumentSnapshot> getAdminPasswordResetDoc(@NonNull String adminUid) {
        return firestore.collection(COLLECTION_ADMIN_PASSWORD_RESET)
                .document(adminUid)
                .get();
    }

    public Task<DocumentSnapshot> getRegistrationDoc(@NonNull String phone) {
        return getOtpDoc(COLLECTION_REGISTRATION, phone);
    }

    private Task<DocumentSnapshot> getOtpDoc(@NonNull String collection, @NonNull String phone) {
        return firestore.collection(collection)
                .document(phone)
                .get();
    }

    public Task<Void> markVerified(@NonNull String phone) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("verified", true);
        updates.put("verifiedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_PASSWORD_RESET)
                .document(phone)
                .update(updates);
    }

    public Task<Void> markAdminVerified(@NonNull String adminUid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("verified", true);
        updates.put("verifiedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_ADMIN_PASSWORD_RESET)
                .document(adminUid)
                .update(updates);
    }

    public Task<Void> deletePasswordResetDoc(@NonNull String phone) {
        return deleteOtpDoc(COLLECTION_PASSWORD_RESET, phone);
    }

    public Task<Void> deleteAdminPasswordResetDoc(@NonNull String adminUid) {
        return deleteOtpDoc(COLLECTION_ADMIN_PASSWORD_RESET, adminUid);
    }

    public Task<Void> deleteRegistrationDoc(@NonNull String phone) {
        return deleteOtpDoc(COLLECTION_REGISTRATION, phone);
    }

    private Task<Void> deleteOtpDoc(@NonNull String collection, @NonNull String phone) {
        return firestore.collection(collection)
                .document(phone)
                .delete();
    }

    public boolean isOtpExpired(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return true;
        }
        Long expiredAt = doc.getLong("expiredAt");
        return expiredAt == null || System.currentTimeMillis() > expiredAt;
    }

    public boolean isResetSessionValid(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return false;
        }
        Boolean verified = doc.getBoolean("verified");
        if (verified == null || !verified) {
            return false;
        }
        Long verifiedAt = doc.getLong("verifiedAt");
        if (verifiedAt == null) {
            Long createdAt = doc.getLong("createdAt");
            verifiedAt = createdAt;
        }
        return verifiedAt != null
                && System.currentTimeMillis() <= verifiedAt + RESET_SESSION_MS;
    }

    public boolean matchesOtp(DocumentSnapshot doc, @NonNull String inputOtp) {
        if (doc == null || !doc.exists()) {
            return false;
        }
        String normalizedInput = inputOtp.trim();
        String storedOtp = doc.getString("otp");
        if (storedOtp == null) {
            Long otpLong = doc.getLong("otp");
            if (otpLong != null) {
                storedOtp = String.valueOf(otpLong);
            }
        }
        return storedOtp != null && storedOtp.equals(normalizedInput);
    }
}
