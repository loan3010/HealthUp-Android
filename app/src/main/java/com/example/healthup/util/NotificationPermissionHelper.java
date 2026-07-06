package com.example.healthup.util;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public final class NotificationPermissionHelper {

    private static final String PREFS_NAME = "healthup_prefs";
    private static final String KEY_DECLINED = "notify_permission_declined";

    private NotificationPermissionHelper() {
    }

    public static boolean shouldShowPrompt(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        if (hasPermission(context)) {
            return false;
        }
        return !isDeclined(context);
    }

    public static boolean hasPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void request(
            @NonNull ActivityResultLauncher<String> launcher
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    public static void markDeclined(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_DECLINED, true).apply();
    }

    public static void clearDeclined(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_DECLINED, false).apply();
    }

    private static boolean isDeclined(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_DECLINED, false);
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
