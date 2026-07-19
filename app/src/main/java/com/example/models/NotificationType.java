package com.example.models;

public enum NotificationType {
    ORDER_SHIPPING,
    ORDER_CONFIRMED,
    ORDER_DELIVERY_CONFIRMED,
    ORDER_DELIVERY_FAILED,
    ORDER_REDELIVERY,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    ORDER_RETURN_REQUESTED,
    ORDER_RETURN_APPROVED,
    ORDER_RETURN_REJECTED,
    ORDER_UPDATE,
    CHAT_STAFF_REPLY,
    PROMO,
    PAYMENT,
    WISHLIST_SALE,
    REVIEW_REMINDER,
    GENERAL;

    public static NotificationType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return GENERAL;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
