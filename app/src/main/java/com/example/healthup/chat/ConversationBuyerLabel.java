package com.example.healthup.chat;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Conversation;

/**
 * Resolve a seller-facing label for the buyer in a support conversation.
 * Prefer username → full name → phone → short UID (from buyerId or {@code support_<uid>}).
 */
public final class ConversationBuyerLabel {

    private static final String SUPPORT_PREFIX = "support_";

    private ConversationBuyerLabel() {
    }

    @Nullable
    public static String resolve(@Nullable Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        String username = conversation.getBuyerUsername();
        if (!TextUtils.isEmpty(username)) {
            String trimmed = username.trim();
            return trimmed.startsWith("@") ? trimmed : ("@" + trimmed);
        }
        String name = conversation.getBuyerName();
        if (!TextUtils.isEmpty(name) && !isGenericPlaceholder(name)) {
            return name.trim();
        }
        String phone = conversation.getBuyerPhone();
        if (!TextUtils.isEmpty(phone)) {
            return phone.trim();
        }
        return formatUidLabel(resolveBuyerUid(conversation));
    }

    /**
     * Prefer {@code buyerId} field; fall back to stripping {@code support_} from the
     * conversation document id (legacy / incomplete convos may omit buyerId).
     */
    @Nullable
    public static String resolveBuyerUid(@Nullable Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        String buyerId = conversation.getBuyerId();
        if (!TextUtils.isEmpty(buyerId)) {
            return buyerId.trim();
        }
        String convId = conversation.getId();
        if (!TextUtils.isEmpty(convId) && convId.startsWith(SUPPORT_PREFIX)
                && convId.length() > SUPPORT_PREFIX.length()) {
            return convId.substring(SUPPORT_PREFIX.length());
        }
        return null;
    }

    @Nullable
    private static String formatUidLabel(@Nullable String buyerUid) {
        if (TextUtils.isEmpty(buyerUid)) {
            return null;
        }
        String id = buyerUid.trim();
        if (id.length() <= 10) {
            return "UID · " + id;
        }
        // Show enough of the Auth uid for staff to match users/{uid} in Console.
        return "UID · " + id.substring(0, 8) + "…" + id.substring(id.length() - 4);
    }

    private static boolean isGenericPlaceholder(@Nullable String name) {
        if (TextUtils.isEmpty(name)) {
            return true;
        }
        String n = name.trim();
        return "Khách hàng".equalsIgnoreCase(n)
                || "Customer".equalsIgnoreCase(n)
                || "Người dùng".equalsIgnoreCase(n)
                || "User".equalsIgnoreCase(n);
    }

    @NonNull
    public static String resolveOrFallback(@Nullable Conversation conversation,
                                           @NonNull String fallback) {
        String label = resolve(conversation);
        return label != null ? label : fallback;
    }
}
