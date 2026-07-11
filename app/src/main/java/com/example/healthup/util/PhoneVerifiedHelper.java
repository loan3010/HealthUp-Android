package com.example.healthup.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Checkout requires an OTP-verified phone ({@code phoneVerified = true}).
 */
public final class PhoneVerifiedHelper {

    public interface Callback {
        void onVerified();

        /** Signed out, or profile missing phone / phoneVerified. */
        void onNeedPhoneVerification();

        void onError(@NonNull String message);
    }

    private PhoneVerifiedHelper() {
    }

    public static void requireForCheckout(@NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onNeedPhoneVerification();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onNeedPhoneVerification();
                        return;
                    }
                    String phone = doc.getString("phone");
                    Boolean verified = doc.getBoolean("phoneVerified");
                    // Explicit false → block. Null + has phone → legacy account, allow.
                    boolean ok = Boolean.TRUE.equals(verified)
                            || (verified == null && !TextUtils.isEmpty(phone));
                    if (ok && !TextUtils.isEmpty(phone)) {
                        callback.onVerified();
                    } else {
                        callback.onNeedPhoneVerification();
                    }
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "error"));
    }
}
