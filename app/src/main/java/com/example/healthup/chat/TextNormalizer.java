package com.example.healthup.chat;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalizes Vietnamese free text for robust keyword / synonym matching.
 *
 * <p>Pipeline: lowercase → đ→d → strip diacritics → drop punctuation → collapse spaces.
 * Example: {@code "Đơn tôi tới đâu rồi???"} → {@code "don toi toi dau roi"}.</p>
 *
 * <p>Also provides exact + fuzzy (Levenshtein) phrase matching so light typos still hit.</p>
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(@Nullable String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT);
        // "đ"/"Đ" are not decomposed by NFD.
        lower = lower.replace('\u0111', 'd').replace('\u0110', 'd');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Keep letters/digits/spaces only (drops ?, !, ., …).
        stripped = stripped.replaceAll("[^a-z0-9\\s]", " ");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    /** True when haystack (already normalized) contains any keyword exactly. */
    public static boolean containsAny(@Nullable String normalizedHaystack, String... normalizedKeywords) {
        return matchAny(normalizedHaystack, false, normalizedKeywords) >= 0;
    }

    /**
     * Exact match first, then fuzzy phrase match (typos).
     * Returns the index of the best matching keyword, or -1.
     */
    public static int matchAny(@Nullable String normalizedHaystack,
                               boolean allowFuzzy,
                               @NonNull String... normalizedKeywords) {
        if (TextUtils.isEmpty(normalizedHaystack) || normalizedKeywords.length == 0) {
            return -1;
        }
        String hay = normalizedHaystack.trim();
        for (int i = 0; i < normalizedKeywords.length; i++) {
            String keyword = normalize(normalizedKeywords[i]);
            if (TextUtils.isEmpty(keyword)) {
                continue;
            }
            if (hay.contains(keyword)) {
                return i;
            }
        }
        if (!allowFuzzy) {
            return -1;
        }
        int bestIndex = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < normalizedKeywords.length; i++) {
            String keyword = normalize(normalizedKeywords[i]);
            if (TextUtils.isEmpty(keyword) || keyword.length() < 4) {
                // Short tokens ("hi","huy") — exact only, too risky for fuzzy.
                continue;
            }
            int cost = fuzzyPhraseCost(hay, keyword);
            int max = maxEditDistance(keyword.length());
            if (cost >= 0 && cost <= max && cost < bestCost) {
                bestCost = cost;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public static boolean matchesAny(@Nullable String rawOrNormalized,
                                     boolean allowFuzzy,
                                     @NonNull String... keywords) {
        return matchAny(normalize(rawOrNormalized), allowFuzzy, keywords) >= 0;
    }

    /**
     * Total edit cost for a multi-word phrase: each keyword word must fuzzy-match
     * some haystack token (order preserved loosely by scanning forward).
     */
    private static int fuzzyPhraseCost(@NonNull String haystack, @NonNull String phrase) {
        String[] needleWords = phrase.split(" ");
        String[] hayWords = haystack.split(" ");
        if (needleWords.length == 0 || hayWords.length == 0) {
            return -1;
        }
        int total = 0;
        int hayIndex = 0;
        for (String needle : needleWords) {
            if (needle.isEmpty()) {
                continue;
            }
            int best = Integer.MAX_VALUE;
            int bestAt = -1;
            int max = maxEditDistance(needle.length());
            for (int i = hayIndex; i < hayWords.length; i++) {
                String token = hayWords[i];
                if (token.isEmpty()) {
                    continue;
                }
                // Length gate avoids matching "don" to "dongian" via huge windows.
                if (Math.abs(token.length() - needle.length()) > max) {
                    continue;
                }
                int d = levenshtein(needle, token);
                if (d < best) {
                    best = d;
                    bestAt = i;
                }
                if (d == 0) {
                    break;
                }
            }
            if (bestAt < 0 || best > max) {
                return -1;
            }
            total += best;
            hayIndex = bestAt + 1;
        }
        return total;
    }

    /** Allowed typos grow gently with keyword length. */
    static int maxEditDistance(int length) {
        if (length <= 3) {
            return 0;
        }
        if (length <= 5) {
            return 1;
        }
        if (length <= 8) {
            return 2;
        }
        return 3;
    }

    static int levenshtein(@NonNull String a, @NonNull String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(
                        Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    /** Splits normalized text into tokens (empty-safe). */
    @NonNull
    public static List<String> tokens(@Nullable String normalized) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(normalized)) {
            return out;
        }
        for (String part : normalized.split(" ")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
