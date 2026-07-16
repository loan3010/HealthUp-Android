package com.example.healthup.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class OrderSeenManager {
    private static final String PREF_NAME = "order_seen_prefs";
    private static final String KEY_SEEN_ORDERS = "seen_order_ids";

    public static void markAsSeen(Context context, String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> seenIds = new HashSet<>(prefs.getStringSet(KEY_SEEN_ORDERS, new HashSet<>()));
        if (seenIds.add(orderId)) {
            prefs.edit().putStringSet(KEY_SEEN_ORDERS, seenIds).apply();
        }
    }

    public static void markMultipleAsSeen(Context context, Set<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> seenIds = new HashSet<>(prefs.getStringSet(KEY_SEEN_ORDERS, new HashSet<>()));
        if (seenIds.addAll(orderIds)) {
            prefs.edit().putStringSet(KEY_SEEN_ORDERS, seenIds).apply();
        }
    }

    public static boolean isSeen(Context context, String orderId) {
        if (orderId == null || orderId.isEmpty()) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> seenIds = prefs.getStringSet(KEY_SEEN_ORDERS, new HashSet<>());
        return seenIds.contains(orderId);
    }
}
