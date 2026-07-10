package com.example.healthup.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.BuildConfig;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Firebase email verification / verify-before-update helpers.
 */
public final class EmailVerificationHelper {

    private static final String TAG = "EmailVerification";
    /** Continue / deep-link landing on the Firebase Hosting domain (authorized by default). */
    public static final String CONTINUE_URL = "https://healthup-f6eff.firebaseapp.com/emailAction";

    public interface Callback {
        void onSuccess();

        void onError(@NonNull String message);
    }

    public interface ApplyCallback {
        void onApplied(@NonNull String email);

        void onError(@NonNull String message);
    }

    private EmailVerificationHelper() {
    }

    /** Prefer opening the Android app so we can {@link FirebaseAuth#applyActionCode}. */
    @NonNull
    public static ActionCodeSettings inAppActionCodeSettings() {
        return ActionCodeSettings.newBuilder()
                .setUrl(CONTINUE_URL)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(BuildConfig.APPLICATION_ID, false, null)
                .build();
    }

    /** Web-only continue URL (no Android package) — fallback if in-app settings are rejected. */
    @NonNull
    public static ActionCodeSettings webContinueSettings() {
        return ActionCodeSettings.newBuilder()
                .setUrl(CONTINUE_URL)
                .setHandleCodeInApp(false)
                .build();
    }

    /** Registration / resend to current Auth email. */
    public static void sendToCurrentEmail(@NonNull FirebaseUser user, @NonNull Callback callback) {
        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            callback.onError("Tài khoản chưa có email trên Firebase Auth.");
            return;
        }
        if (UserProfileBuilder.isSyntheticAuthEmail(email)) {
            callback.onError("Email Auth đang là email ảo. Hãy thêm email thật trước.");
            return;
        }

        user.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser refreshed = FirebaseAuth.getInstance().getCurrentUser();
                    if (refreshed == null) {
                        callback.onError("Phiên đăng nhập không còn. Đăng nhập lại rồi thử.");
                        return;
                    }
                    refreshed.sendEmailVerification()
                            .addOnSuccessListener(v -> {
                                logDebug("sendEmailVerification (plain) OK for " + email);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                logDebug("plain failed, retry in-app settings", e);
                                refreshed.sendEmailVerification(inAppActionCodeSettings())
                                        .addOnSuccessListener(v -> callback.onSuccess())
                                        .addOnFailureListener(e2 ->
                                                callback.onError(readableError(e2)));
                            });
                })
                .addOnFailureListener(e -> callback.onError(readableError(e)));
    }

    /**
     * Change-email: verify NEW address then Auth email updates.
     * Prefer plain send (no custom continue URL) so users are not sent to empty
     * Firebase Hosting ("Site Not Found"). In-app deep link is optional fallback.
     */
    public static void sendVerifyBeforeUpdateEmail(
            @NonNull FirebaseUser user,
            @NonNull String newEmail,
            @NonNull Callback callback
    ) {
        String normalized = newEmail.trim().toLowerCase();
        String current = user.getEmail();
        if (current != null && normalized.equalsIgnoreCase(current)) {
            sendToCurrentEmail(user, callback);
            return;
        }

        // Plain first: Firebase Auth handler applies the code; no Hosting deploy required.
        user.verifyBeforeUpdateEmail(normalized)
                .addOnSuccessListener(unused -> {
                    logDebug("verifyBeforeUpdateEmail plain OK for " + normalized);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    logDebug("plain FAILED, try in-app settings", e);
                    user.verifyBeforeUpdateEmail(normalized, inAppActionCodeSettings())
                            .addOnSuccessListener(unused -> {
                                logDebug("verifyBeforeUpdateEmail in-app OK for " + normalized);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e2 ->
                                    callback.onError(readableError(e2)));
                });
    }

    /**
     * Apply oobCode from an email / deep link (verifyAndChangeEmail, verifyEmail, …).
     */
    public static void applyOobCode(@NonNull String oobCode, @NonNull ApplyCallback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.applyActionCode(oobCode)
                .addOnSuccessListener(unused -> {
                    logDebug("applyActionCode OK");
                    // Email is known from PendingEmailChange / Auth reload — not from ActionCodeResult
                    // (API differs across Firebase Auth versions).
                    callback.onApplied("");
                })
                .addOnFailureListener(e -> {
                    logDebug("applyActionCode FAILED", e);
                    callback.onError(readableApplyError(e));
                });
    }

    @NonNull
    public static String readableError(@Nullable Exception e) {
        if (e == null) {
            return "Không gửi được email xác thực.";
        }
        if (e instanceof com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            return "Cần xác nhận mật khẩu trước khi gửi email xác thực.";
        }
        String code = "";
        if (e instanceof com.google.firebase.auth.FirebaseAuthException) {
            code = ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode();
        }
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String lower = (code + " " + msg).toLowerCase();
        if (lower.contains("recent") || lower.contains("requires-recent-login")) {
            return "Cần xác nhận mật khẩu trước khi gửi email xác thực.";
        }
        if (lower.contains("already") || lower.contains("email-already-in-use")) {
            return "Email này đã được sử dụng bởi tài khoản khác.";
        }
        if (lower.contains("invalid") && lower.contains("email")) {
            return "Email không hợp lệ.";
        }
        if (lower.contains("network")) {
            return "Lỗi mạng. Kiểm tra kết nối rồi thử lại.";
        }
        if (lower.contains("too-many") || lower.contains("quota")) {
            return "Gửi email quá nhiều lần. Đợi vài phút rồi thử lại.";
        }
        return "Không gửi được email. Thử lại sau.";
    }

    @NonNull
    public static String readableApplyError(@Nullable Exception e) {
        if (e == null) {
            return "Không áp dụng được link xác thực.";
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("expired") || msg.contains("invalid") || msg.contains("used")) {
            return "Link đã dùng hoặc hết hạn. Hãy bấm Gửi lại trong app.";
        }
        return readableError(e);
    }

    private static void logDebug(@NonNull String message) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message);
        }
    }

    private static void logDebug(@NonNull String message, @Nullable Throwable t) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, t);
        }
    }
}
