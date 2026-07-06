package com.example.healthup;

import android.text.TextUtils;
import android.util.Patterns;

final class LoginValidator {

    private LoginValidator() {
    }

    static String validateIdentifier(String identifier) {
        if (TextUtils.isEmpty(identifier)) {
            return "required";
        }

        if (identifier.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                return "email_invalid";
            }
            return null;
        }

        if (!TextUtils.isDigitsOnly(identifier)) {
            return "phone_invalid";
        }

        if (identifier.length() != 10) {
            return "phone_length";
        }

        return null;
    }

    static String validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return "required";
        }

        if (password.length() < 8) {
            return "too_short";
        }

        return null;
    }

    static boolean isFormValid(String identifier, String password) {
        return validateIdentifier(identifier) == null && validatePassword(password) == null;
    }

    static boolean isEmailIdentifier(String identifier) {
        return identifier.contains("@") && Patterns.EMAIL_ADDRESS.matcher(identifier).matches();
    }

    static boolean isPhoneIdentifier(String identifier) {
        return TextUtils.isDigitsOnly(identifier) && identifier.length() == 10;
    }
}
