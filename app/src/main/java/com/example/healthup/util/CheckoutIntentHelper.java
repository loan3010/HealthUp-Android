package com.example.healthup.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.LoginActivity;
import com.example.healthup.MainActivity;
import com.example.healthup.RegisterActivity;
import com.example.models.CartItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class CheckoutIntentHelper {

    public static final String EXTRA_NAVIGATE_TO = "navigate_to";
    public static final String EXTRA_CHECKOUT_ITEMS = "checkout_items";
    public static final String EXTRA_PREFILL_PHONE = "prefill_phone";
    public static final String EXTRA_RETURN_TO_CHECKOUT = "return_to_checkout";
    public static final String EXTRA_FOCUS_PASSWORD = "focus_password";

    public static final String NAV_CHECKOUT = "checkout";
    public static final String NAV_PHONE_VERIFICATION = "phone_verification";
    public static final String NAV_HOME_TAB = "home_tab";

    private static final String PREFS_NAME = "pending_checkout_prefs";
    private static final String KEY_HAS_PENDING = "has_pending_checkout";

    private CheckoutIntentHelper() {
    }

    public static void savePendingCheckout(Context context, List<CartItem> items) {
        PendingCheckoutStore.save(context, items);
    }

    /**
     * Intent-safe cart lines for IPC: flat fields only, never nested {@link CartItem#getProduct()}.
     * Product embeds Firebase {@code Timestamp} which is not java.io.Serializable and crashes
     * {@code putExtra("checkout_items", ...)} / startActivity (Buy Now path).
     */
    @NonNull
    public static ArrayList<CartItem> toIntentSafeItems(@Nullable List<CartItem> items) {
        ArrayList<CartItem> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (CartItem src : items) {
            if (src == null) {
                continue;
            }
            CartItem copy = new CartItem();
            copy.setId(src.getId());
            copy.setProductId(src.getProductId());
            copy.setName(src.getName());
            copy.setImageUrl(src.getImageUrl());
            copy.setPrice(src.getPrice());
            copy.setOriginalPrice(src.getOriginalPrice());
            copy.setQuantity(src.getQuantity());
            copy.setStock(src.getStock());
            copy.setVariantId(src.getVariantId());
            copy.setVariantName(src.getVariantName());
            copy.setWeight(src.getWeight());
            copy.setFlavor(src.getFlavor());
            copy.setPackageType(src.getPackageType());
            copy.setSelected(src.isSelected());
            copy.setUserId(src.getUserId());
            copy.setProduct(null);
            out.add(copy);
        }
        return out;
    }

    public static boolean hasPendingCheckout(Context context) {
        return PendingCheckoutStore.hasPending(context);
    }

    public static List<CartItem> getPendingCheckout(Context context) {
        return PendingCheckoutStore.load(context);
    }

    public static void clearPendingCheckout(Context context) {
        PendingCheckoutStore.clear(context);
    }

    public static Intent buildLoginIntent(Context context, String phone, boolean returnToCheckout) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(EXTRA_PREFILL_PHONE, phone);
        intent.putExtra(EXTRA_RETURN_TO_CHECKOUT, returnToCheckout);
        intent.putExtra(EXTRA_FOCUS_PASSWORD, true);
        return intent;
    }

    public static Intent buildRegisterIntent(Context context, String phone, boolean returnToCheckout) {
        Intent intent = new Intent(context, RegisterActivity.class);
        intent.putExtra(RegisterActivity.EXTRA_PHONE, phone);
        intent.putExtra(EXTRA_RETURN_TO_CHECKOUT, returnToCheckout);
        return intent;
    }

    public static Intent buildPostAuthMainIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (hasPendingCheckout(context)) {
            List<CartItem> items = getPendingCheckout(context);
            intent.putExtra(EXTRA_NAVIGATE_TO, NAV_CHECKOUT);
            intent.putExtra(EXTRA_CHECKOUT_ITEMS, toIntentSafeItems(items));
            clearPendingCheckout(context);
        }

        return intent;
    }

    /** Opens Main on the home tab; keeps the current Firebase session (guest or logged-in). */
    public static Intent buildMainHomeIntent(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_NAVIGATE_TO, NAV_HOME_TAB);
        return intent;
    }

    public static void openMainHome(@NonNull android.app.Activity activity) {
        if (activity.isFinishing()) {
            return;
        }
        // CLEAR_TOP finishes Login/AccountManagement above Main; avoid racing finish() here.
        activity.startActivity(buildMainHomeIntent(activity));
    }

    public static boolean shouldReturnToCheckout(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_RETURN_TO_CHECKOUT, false);
    }

    static final class PendingCheckoutStore {

        private PendingCheckoutStore() {
        }

        static void save(Context context, List<CartItem> items) {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (items == null || items.isEmpty()) {
                clear(context);
                return;
            }
            prefs.edit()
                    .putBoolean(KEY_HAS_PENDING, true)
                    .putString("items_json", GuestCartManager.itemsToJson(items))
                    .commit();
        }

        static boolean hasPending(Context context) {
            return context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HAS_PENDING, false);
        }

        @SuppressWarnings("unchecked")
        static List<CartItem> load(Context context) {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(KEY_HAS_PENDING, false)) {
                return new ArrayList<>();
            }
            return GuestCartManager.itemsFromJson(prefs.getString("items_json", "[]"));
        }

        static void clear(Context context) {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_HAS_PENDING)
                    .remove("items_json")
                    .apply();
        }
    }
}
