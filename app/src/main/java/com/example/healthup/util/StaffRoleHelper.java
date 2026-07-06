package com.example.healthup.util;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

public final class StaffRoleHelper {

    private StaffRoleHelper() {
    }

    @Nullable
    public static String resolveRole(@Nullable DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return null;
        }
        String role = document.getString("role");
        if (role == null || role.trim().isEmpty()) {
            role = document.getString("userRole");
        }
        return role;
    }

    public static boolean isStaff(@Nullable String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "seller".equals(normalized)
                || "admin".equals(normalized)
                || "staff".equals(normalized);
    }

    public static boolean isStaff(@Nullable DocumentSnapshot document) {
        return isStaff(resolveRole(document));
    }

    public static boolean isAdmin(@Nullable String role) {
        if (role == null) {
            return false;
        }
        return "admin".equals(role.trim().toLowerCase(Locale.ROOT));
    }
}
