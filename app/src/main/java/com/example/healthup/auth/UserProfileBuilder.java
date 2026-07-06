package com.example.healthup.auth;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

public final class UserProfileBuilder {

    public static final String AUTH_PROVIDER_GOOGLE = "google";
    public static final String AUTH_PROVIDER_FACEBOOK = "facebook";
    public static final String AUTH_PROVIDER_PASSWORD = "password";

    private UserProfileBuilder() {
    }

    public static Map<String, Object> buildPasswordRegistration(
            String fullName,
            String phone,
            String authEmail,
            String displayEmail
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("phone", phone);
        userData.put("email", authEmail);
        userData.put("phoneVerified", true);
        userData.put("role", "buyer");
        userData.put("authProvider", AUTH_PROVIDER_PASSWORD);
        if (!TextUtils.isEmpty(displayEmail)) {
            userData.put("displayEmail", displayEmail);
        }
        return userData;
    }

    public static Map<String, Object> buildSocialRegistration(
            String fullName,
            String phone,
            String authEmail,
            String displayEmail,
            String authProvider
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("phone", phone);
        userData.put("email", authEmail);
        userData.put("phoneVerified", true);
        userData.put("role", "buyer");
        userData.put("authProvider", authProvider);
        if (!TextUtils.isEmpty(displayEmail)) {
            userData.put("displayEmail", displayEmail);
        }
        return userData;
    }
}
