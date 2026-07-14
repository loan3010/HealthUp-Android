package com.example.healthup.chat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.healthup.R;
import com.example.models.ChatMessage;

import java.util.List;

/**
 * Messenger-style bubble grouping and backgrounds for the buyer chat thread.
 */
public final class ChatBubbleHelper {

    public enum GroupPosition {
        SINGLE, FIRST, MIDDLE, LAST
    }

    private ChatBubbleHelper() {
    }

    public static boolean isGroupable(@NonNull ChatMessage message) {
        if (ChatMessage.TYPE_SUGGESTION.equals(message.getType())
                || ChatMessage.TYPE_ORDER_CARD.equals(message.getType())
                || ChatMessage.TYPE_PRODUCT_CARD.equals(message.getType())
                || ChatMessage.TYPE_LOGIN_ACTION.equals(message.getType())
                || ChatMessage.TYPE_IMAGE.equals(message.getType())) {
            return false;
        }
        if (ChatMessage.TYPE_SYSTEM.equals(message.getType())
                || ChatMessage.SENDER_SYSTEM.equals(message.getSenderType())) {
            return false;
        }
        return ChatMessage.TYPE_TEXT.equals(message.getType())
                || message.getType() == null
                || message.getType().isEmpty();
    }

    @NonNull
    public static GroupPosition resolveGroupPosition(@NonNull List<ChatMessage> items, int position) {
        if (position < 0 || position >= items.size()) {
            return GroupPosition.SINGLE;
        }
        ChatMessage current = items.get(position);
        if (!isGroupable(current)) {
            return GroupPosition.SINGLE;
        }
        String key = groupKey(current);
        boolean hasPrev = position > 0
                && isGroupable(items.get(position - 1))
                && groupKey(items.get(position - 1)).equals(key);
        boolean hasNext = position + 1 < items.size()
                && isGroupable(items.get(position + 1))
                && groupKey(items.get(position + 1)).equals(key);
        if (hasPrev && hasNext) {
            return GroupPosition.MIDDLE;
        }
        if (hasPrev) {
            return GroupPosition.LAST;
        }
        if (hasNext) {
            return GroupPosition.FIRST;
        }
        return GroupPosition.SINGLE;
    }

    public static boolean shouldShowAvatar(@NonNull List<ChatMessage> items, int position) {
        GroupPosition pos = resolveGroupPosition(items, position);
        return pos == GroupPosition.SINGLE || pos == GroupPosition.FIRST;
    }

    public static boolean shouldShowTimestamp(@NonNull List<ChatMessage> items, int position) {
        GroupPosition pos = resolveGroupPosition(items, position);
        return pos == GroupPosition.SINGLE || pos == GroupPosition.LAST;
    }

    public static int verticalPaddingTopDp(@NonNull GroupPosition position) {
        switch (position) {
            case FIRST:
            case SINGLE:
                return 4;
            case MIDDLE:
            case LAST:
            default:
                return 1;
        }
    }

    public static int verticalPaddingBottomDp(@NonNull GroupPosition position) {
        switch (position) {
            case LAST:
            case SINGLE:
                return 4;
            case FIRST:
            case MIDDLE:
            default:
                return 1;
        }
    }

    @DrawableRes
    public static int userBubbleBackground(@NonNull GroupPosition position) {
        switch (position) {
            case FIRST:
                return R.drawable.bg_chat_bubble_user_first;
            case MIDDLE:
                return R.drawable.bg_chat_bubble_user_middle;
            case LAST:
                return R.drawable.bg_chat_bubble_user_last;
            case SINGLE:
            default:
                return R.drawable.bg_chat_bubble_user;
        }
    }

    @DrawableRes
    public static int incomingBubbleBackground(@NonNull GroupPosition position) {
        switch (position) {
            case FIRST:
                return R.drawable.bg_chat_bubble_incoming_first;
            case MIDDLE:
                return R.drawable.bg_chat_bubble_incoming_middle;
            case LAST:
                return R.drawable.bg_chat_bubble_incoming_last;
            case SINGLE:
            default:
                return R.drawable.bg_chat_bubble_incoming;
        }
    }

    @NonNull
    private static String groupKey(@NonNull ChatMessage message) {
        String senderType = message.getSenderType();
        if (senderType == null || senderType.isEmpty()) {
            return "incoming";
        }
        return senderType;
    }
}
