package com.example.healthup.util;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes Vietnamese phone numbers to local 10-digit format (0xxxxxxxxx).
 * Accepts +84, 84, and 0xxx input styles.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }

        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }

        if (digits.startsWith("84")) {
            digits = digits.substring(2);
        }

        if (digits.length() == 9) {
            return "0" + digits;
        }

        if (digits.length() == 10 && digits.startsWith("0")) {
            return digits;
        }

        if (digits.length() > 10 && digits.startsWith("0")) {
            return digits.substring(0, 10);
        }

        return digits;
    }

    public static List<String> getLookupVariants(String input) {
        Set<String> variants = new LinkedHashSet<>();
        String normalized = normalize(input);
        if (TextUtils.isEmpty(normalized)) {
            return new ArrayList<>();
        }

        variants.add(normalized);
        if (normalized.length() == 10 && normalized.startsWith("0")) {
            String withoutLeadingZero = normalized.substring(1);
            variants.add(withoutLeadingZero);
            variants.add("+84" + withoutLeadingZero);
            variants.add("84" + withoutLeadingZero);
        }
        return new ArrayList<>(variants);
    }

    public static boolean isValidLocalPhone(String input) {
        String normalized = normalize(input);
        return normalized.length() == 10
                && normalized.startsWith("0")
                && TextUtils.isDigitsOnly(normalized);
    }
}
