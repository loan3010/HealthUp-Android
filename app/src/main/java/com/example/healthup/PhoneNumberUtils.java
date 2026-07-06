package com.example.healthup;

import android.text.TextUtils;

public final class PhoneNumberUtils {

    private PhoneNumberUtils() {
    }

    /** Converts local VN phone (0912345678) to E.164 (+84912345678). */
    public static String toE164(String localPhone) {
        if (TextUtils.isEmpty(localPhone)) {
            return "";
        }
        if (localPhone.startsWith("+")) {
            return localPhone;
        }
        if (localPhone.startsWith("0") && localPhone.length() == 10) {
            return "+84" + localPhone.substring(1);
        }
        return "+84" + localPhone;
    }

    public static String maskPhone(String localPhone) {
        if (localPhone == null || localPhone.length() < 4) {
            return localPhone;
        }
        return "+84 *** *** " + localPhone.substring(localPhone.length() - 3);
    }
}
