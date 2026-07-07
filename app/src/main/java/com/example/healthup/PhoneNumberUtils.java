package com.example.healthup;

import android.text.TextUtils;

import com.example.healthup.util.PhoneNormalizer;

public final class PhoneNumberUtils {

    private PhoneNumberUtils() {
    }

    /** Converts local VN phone (0912345678) to E.164 (+84912345678). */
    public static String toE164(String localPhone) {
        String normalized = PhoneNormalizer.normalize(localPhone);
        if (TextUtils.isEmpty(normalized)) {
            return "";
        }
        if (normalized.startsWith("+")) {
            return normalized;
        }
        if (normalized.startsWith("0") && normalized.length() == 10) {
            return "+84" + normalized.substring(1);
        }
        return "+84" + normalized;
    }

    public static String maskPhone(String localPhone) {
        if (localPhone == null || localPhone.length() < 4) {
            return localPhone;
        }
        return "+84 *** *** " + localPhone.substring(localPhone.length() - 3);
    }
}
