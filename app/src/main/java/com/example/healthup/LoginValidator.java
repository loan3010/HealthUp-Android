package com.example.healthup;

import android.text.TextUtils;
import android.util.Patterns;

import com.example.healthup.util.PhoneNormalizer;

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

        if (!PhoneNormalizer.isValidLocalPhone(identifier)) {
            return "phone_invalid";
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
        return PhoneNormalizer.isValidLocalPhone(identifier);
    }
}
