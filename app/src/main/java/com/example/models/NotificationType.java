package com.example.models;

public enum NotificationType {
    ORDER_SHIPPING,
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
