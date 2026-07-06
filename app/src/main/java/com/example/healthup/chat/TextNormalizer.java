package com.example.healthup.chat;

import android.text.TextUtils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizes Vietnamese free text for diacritic-insensitive keyword matching.
 *
 * <p>Example: "Kiểm tra ĐƠN Hàng" -> "kiem tra don hang". This lets the bot
 * match user input regardless of accents, casing or the Vietnamese "đ".</p>
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String lower = input.toLowerCase(Locale.getDefault());
        // Handle "đ"/"Đ" explicitly since NFD does not decompose them.
        lower = lower.replace('\u0111', 'd').replace('\u0110', 'd');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        // Strip combining diacritical marks.
        String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Collapse whitespace.
        return stripped.replaceAll("\\s+", " ").trim();
    }

    /** Returns true when {@code haystack} (already normalized) contains any keyword. */
    public static boolean containsAny(String normalizedHaystack, String... normalizedKeywords) {
        if (TextUtils.isEmpty(normalizedHaystack)) {
            return false;
        }
        for (String keyword : normalizedKeywords) {
            if (!TextUtils.isEmpty(keyword) && normalizedHaystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
