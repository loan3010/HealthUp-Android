package com.example.healthup.admin;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;

import java.util.Locale;
import java.util.Map;

public final class AdminOrderSearchHelper {

    public static final String FILTER_ALL = "all";
    public static final String FILTER_CANCEL_REQUESTED = "cancel_requested";
    public static final String FILTER_RETURNED = "returned";
    public static final String FILTER_OVERDUE = "overdue";

    private AdminOrderSearchHelper() {
    }

    public static boolean matches(@NonNull Order order,
                                  @Nullable String query,
                                  @Nullable String statusFilter,
                                  @Nullable Map<String, AdminRepository.AdminCustomer> customerLookup) {
        if (!matchesStatus(order, statusFilter)) {
            return false;
        }
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);

        if (contains(order.getOrderCode(), q)) return true;
        if (contains(order.getId(), q)) return true;

        String userId = order.getUserId();
        if (!TextUtils.isEmpty(userId)) {
            if (contains(userId, q)) return true;
            if (customerLookup != null) {
                AdminRepository.AdminCustomer customer = customerLookup.get(userId);
                if (customer != null) {
                    if (contains(customer.fullName, q)) return true;
                    if (contains(customer.username, q)) return true;
                    if (contains(customer.phone, q)) return true;
                    if (contains(customer.email, q)) return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesStatus(@NonNull Order order, @Nullable String statusFilter) {
        if (TextUtils.isEmpty(statusFilter) || FILTER_ALL.equals(statusFilter)) {
            return true;
        }
        String status = order.getStatus();
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase(Locale.ROOT).trim();

        if (FILTER_CANCEL_REQUESTED.equals(statusFilter)) {
            return order.isCancelRequested()
                    && Order.STATUS_PENDING.equals(normalized);
        }
        if (FILTER_RETURNED.equals(statusFilter)) {
            return "returned".equals(normalized)
                    || "refunded".equals(normalized)
                    || "reshipped".equals(normalized)
                    || ("completed".equals(normalized) && order.getReturnHandling() != null);
        }
        if (FILTER_OVERDUE.equals(statusFilter)) {
            return AdminOrderListHelper.isOverdue(order, AdminOrderListHelper.hoursToMillis(24));
        }
        if (Order.STATUS_PENDING.equals(statusFilter)) {
            return Order.STATUS_PENDING.equals(normalized) && !order.isCancelRequested();
        }
        return statusFilter.equals(normalized);
    }

    private static boolean contains(@Nullable String value, @NonNull String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
