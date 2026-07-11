package com.example.healthup.account;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.healthup.util.PhoneNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists saved account cards and optional passwords on device (Spark / mock — no server sync).
 */
public final class SavedAccountStore {

    public static final int MAX_ACCOUNTS = 3;

    private static final String PREFS_ACCOUNTS = "saved_accounts_v1";
    private static final String PREFS_PASSWORDS = "saved_account_passwords_v1";
    private static final String KEY_LIST = "accounts_json";

    private SavedAccountStore() {
    }

    @NonNull
    public static List<SavedAccount> getAll(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LIST, "[]");
        List<SavedAccount> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                SavedAccount account = SavedAccount.fromJson(array.getJSONObject(i));
                if (account != null) {
                    result.add(account);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public static boolean isFull(@NonNull Context context) {
        return getAll(context).size() >= MAX_ACCOUNTS;
    }

    public static boolean contains(@NonNull Context context, @NonNull String uid) {
        for (SavedAccount account : getAll(context)) {
            if (uid.equals(account.uid)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canStore(@NonNull Context context, @NonNull String uid) {
        return contains(context, uid) || !isFull(context);
    }

    @Nullable
    public static SavedAccount findByIdentifier(@NonNull Context context, @NonNull String identifier) {
        if (TextUtils.isEmpty(identifier)) {
            return null;
        }
        String trimmed = identifier.trim();
        String normalizedPhone = PhoneNormalizer.normalize(trimmed);
        String lowerEmail = trimmed.toLowerCase(Locale.ROOT);
        for (SavedAccount account : getAll(context)) {
            if (!TextUtils.isEmpty(account.phone)
                    && PhoneNormalizer.normalize(account.phone).equals(normalizedPhone)) {
                return account;
            }
            if (!TextUtils.isEmpty(account.email)
                    && account.email.trim().toLowerCase(Locale.ROOT).equals(lowerEmail)) {
                return account;
            }
        }
        return null;
    }

    public static boolean isBlockedNewLogin(@NonNull Context context, @NonNull String identifier) {
        if (!isFull(context)) {
            return false;
        }
        return findByIdentifier(context, identifier) == null;
    }

    public static boolean upsert(@NonNull Context context, @NonNull SavedAccount account) {
        if (!canStore(context, account.uid)) {
            return false;
        }
        Map<String, SavedAccount> map = new LinkedHashMap<>();
        for (SavedAccount existing : getAll(context)) {
            map.put(existing.uid, existing);
        }
        map.put(account.uid, account);
        persistList(context, new ArrayList<>(map.values()));
        return true;
    }

    public static void remove(@NonNull Context context, @NonNull String uid) {
        List<SavedAccount> next = new ArrayList<>();
        for (SavedAccount account : getAll(context)) {
            if (!uid.equals(account.uid)) {
                next.add(account);
            }
        }
        persistList(context, next);
        clearPassword(context, uid);
    }

    @Nullable
    public static String getPassword(@NonNull Context context, @NonNull String uid) {
        return context.getSharedPreferences(PREFS_PASSWORDS, Context.MODE_PRIVATE)
                .getString(uid, null);
    }

    public static void savePassword(
            @NonNull Context context,
            @NonNull String uid,
            @Nullable String rawPassword
    ) {
        if (TextUtils.isEmpty(rawPassword)) {
            return;
        }
        context.getSharedPreferences(PREFS_PASSWORDS, Context.MODE_PRIVATE)
                .edit()
                .putString(uid, rawPassword)
                .apply();
    }

    private static void clearPassword(@NonNull Context context, @NonNull String uid) {
        context.getSharedPreferences(PREFS_PASSWORDS, Context.MODE_PRIVATE)
                .edit()
                .remove(uid)
                .apply();
    }

    private static void persistList(@NonNull Context context, @NonNull List<SavedAccount> accounts) {
        JSONArray array = new JSONArray();
        for (SavedAccount account : accounts) {
            try {
                array.put(account.toJson());
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFS_ACCOUNTS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LIST, array.toString())
                .apply();
    }
}
