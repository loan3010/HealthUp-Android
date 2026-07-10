package com.example.healthup.admin;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Address;
import com.example.models.Order;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public final class AdminCustomerResolver {

    public interface Callback {
        void onResolved(@NonNull String displayLabel);
    }

    private AdminCustomerResolver() {
    }

    public static void resolve(@Nullable Order order, @NonNull Callback callback) {
        if (order == null) {
            callback.onResolved("Không rõ");
            return;
        }

        String fromAddress = nameFromAddress(order.getAddress());
        if (!TextUtils.isEmpty(fromAddress)) {
            String phone = order.getAddress() != null ? order.getAddress().getPhone() : null;
            callback.onResolved(formatLabel(fromAddress, phone, null));
            return;
        }

        String userId = order.getUserId();
        if (TextUtils.isEmpty(userId)) {
            callback.onResolved("Không rõ");
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(doc -> callback.onResolved(buildLabelFromUserDoc(doc, userId)))
                .addOnFailureListener(e -> callback.onResolved(shortUid(userId)));
    }

    @NonNull
    public static String buildLabelFromUserDoc(@Nullable DocumentSnapshot doc, @NonNull String fallbackUserId) {
        if (doc == null || !doc.exists()) {
            return shortUid(fallbackUserId);
        }
        String name = firstNonEmpty(
                doc.getString("fullName"),
                doc.getString("name"),
                doc.getString("displayName"),
                doc.getString("username")
        );
        String phone = doc.getString("phone");
        String email = firstNonEmpty(doc.getString("displayEmail"), doc.getString("email"));
        if (TextUtils.isEmpty(name)) {
            return formatLabel(shortUid(fallbackUserId), phone, email);
        }
        return formatLabel(name, phone, email);
    }

    @Nullable
    private static String nameFromAddress(@Nullable Address address) {
        if (address == null) {
            return null;
        }
        return firstNonEmpty(address.getRecipientName());
    }

    @NonNull
    private static String formatLabel(@NonNull String name, @Nullable String phone, @Nullable String email) {
        StringBuilder builder = new StringBuilder(name);
        if (!TextUtils.isEmpty(phone)) {
            builder.append(" • ").append(phone);
        } else if (!TextUtils.isEmpty(email) && !email.endsWith("@healthup.app")) {
            builder.append(" • ").append(email);
        }
        return builder.toString();
    }

    @NonNull
    private static String shortUid(@NonNull String userId) {
        if (userId.length() <= 10) {
            return userId;
        }
        return userId.substring(0, 6) + "…" + userId.substring(userId.length() - 4);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }
}
