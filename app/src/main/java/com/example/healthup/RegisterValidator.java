package com.example.healthup;

import android.text.TextUtils;
import android.util.Patterns;

public final class RegisterValidator {

    private RegisterValidator() {
    }

    public static String validateFullName(String fullName) {
        if (TextUtils.isEmpty(fullName)) {
            return "required";
        }
        return null;
    }

    public static String validatePhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "required";
        }
        if (!TextUtils.isDigitsOnly(phone)) {
            return "invalid";
        }
        if (phone.length() != 10) {
            return "length";
        }
        return null;
    }

    public static String validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return null;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "invalid";
        }
        return null;
    }

    public static String validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return "required";
        }
        if (password.length() < 8) {
            return "too_short";
        }
        if (!password.matches(".*[a-z].*")) {
            return "missing_lowercase";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "missing_uppercase";
        }
        if (!password.matches(".*\\d.*")) {
            return "missing_number";
        }
        return null;
    }

    public static String validateConfirmPassword(String password, String confirmPassword) {
        if (TextUtils.isEmpty(confirmPassword)) {
            return "required";
        }
        if (!TextUtils.equals(password, confirmPassword)) {
            return "mismatch";
        }
        return null;
    }

    public static boolean isFormValid(
            String fullName,
            String phone,
            String email,
            String password,
            String confirmPassword
    ) {
        return validateFullName(fullName) == null
                && validatePhone(phone) == null
                && validateEmail(email) == null
                && validatePassword(password) == null
                && validateConfirmPassword(password, confirmPassword) == null;
    }

    public static String buildAuthEmail(String phone, String email) {
        if (!TextUtils.isEmpty(email)) {
            return email;
        }
        return phone + "@healthup.app";
    }
}
