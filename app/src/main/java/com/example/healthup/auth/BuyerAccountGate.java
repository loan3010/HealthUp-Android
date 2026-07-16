package com.example.healthup.auth;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * A real buyer account exists only after phone OTP — {@code users/{uid}} with
 * {@code phoneVerified = true}. Google/Facebook Auth alone is not an account.
 */
public final class BuyerAccountGate {

    private BuyerAccountGate() {
    }

    public static boolean isPhoneVerifiedProfile(@Nullable DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return false;
        }
        Boolean verified = doc.getBoolean("phoneVerified");
        if (!Boolean.TRUE.equals(verified)) {
            return false;
        }
        String phone = doc.getString("phone");
        return !TextUtils.isEmpty(phone);
    }

    public static boolean isSocialAuthUser(@Nullable FirebaseUser user) {
        if (user == null) {
            return false;
        }
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            String provider = info.getProviderId();
            if (GoogleAuthProvider.PROVIDER_ID.equals(provider)
                    || FacebookAuthProvider.PROVIDER_ID.equals(provider)) {
                return true;
            }
        }
        return false;
    }
}
