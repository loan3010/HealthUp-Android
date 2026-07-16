package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AdminOrderListHelper {

    public static final String SORT_NEWEST = "newest";
    public static final String SORT_OLDEST = "oldest";

    /** Statuses that need admin action and can become overdue. */
    private static final List<String> ACTION_STATUSES = List.of(
            Order.STATUS_PENDING,
            Order.STATUS_CONFIRMED
    );

    private AdminOrderListHelper() {
    }

    /**
     * Sort key for admin order list: prefer latest status/activity time
     * ({@code updatedAt}, cancel/return request), not only order creation.
     */
    public static long getSortTime(@NonNull Order order) {
        long best = 0L;
        if (order.getUpdatedAt() != null) {
            best = Math.max(best, order.getUpdatedAt().getTime());
        }
        if (order.getCancelRequestedAt() != null) {
            best = Math.max(best, order.getCancelRequestedAt().getTime());
        }
        if (order.getReturnRequestedAt() != null) {
            best = Math.max(best, order.getReturnRequestedAt().getTime());
        }
        if (order.getCreatedAt() != null) {
            best = Math.max(best, order.getCreatedAt().getTime());
        }
        return best;
    }

    public static void sort(@NonNull List<Order> orders, @Nullable String sortKey) {
        Comparator<Order> comparator;
        if (SORT_OLDEST.equals(sortKey)) {
            comparator = Comparator.comparingLong(AdminOrderListHelper::getSortTime);
        } else {
            comparator = (a, b) -> Long.compare(getSortTime(b), getSortTime(a));
        }
        orders.sort(comparator);
    }

    public static int countForFilter(@NonNull List<Order> orders,
                                     @Nullable String filter,
                                     @Nullable String searchQuery,
                                     @Nullable Map<String, AdminRepository.AdminCustomer> lookup) {
        int count = 0;
        for (Order order : orders) {
            if (AdminOrderSearchHelper.matches(order, searchQuery, filter, lookup)) {
                count++;
            }
        }
        return count;
    }

    public static int countOverdue(@NonNull List<Order> orders, long thresholdMs) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Order order : orders) {
            if (!needsAction(order)) continue;
            long reference = getOverdueReferenceTime(order);
            if (reference > 0 && now - reference >= thresholdMs) {
                count++;
            }
        }
        return count;
    }

    public static int countOverdueForFilter(@NonNull List<Order> orders,
                                            @Nullable String filter,
                                            long thresholdMs) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Order order : orders) {
            if (!AdminOrderSearchHelper.matches(order, "", filter, null)) continue;
            if (!needsAction(order)) continue;
            long reference = getOverdueReferenceTime(order);
            if (reference > 0 && now - reference >= thresholdMs) {
                count++;
            }
        }
        return count;
    }

    public static boolean isOverdue(@NonNull Order order, long thresholdMs) {
        if (!needsAction(order)) return false;
        long reference = getOverdueReferenceTime(order);
        if (reference <= 0) return false;
        return System.currentTimeMillis() - reference >= thresholdMs;
    }

    private static boolean needsAction(@NonNull Order order) {
        String status = order.getStatus();
        if (status == null) return false;
        return ACTION_STATUSES.contains(status.toLowerCase(Locale.ROOT).trim());
    }

    private static long getOverdueReferenceTime(@NonNull Order order) {
        if (order.getUpdatedAt() != null) {
            return order.getUpdatedAt().getTime();
        }
        return getSortTime(order);
    }

    public static Map<String, Integer> buildFilterCounts(@NonNull List<Order> orders,
                                                         @Nullable String searchQuery,
                                                         @Nullable Map<String, AdminRepository.AdminCustomer> lookup) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> filters = new ArrayList<>();
        filters.add(AdminOrderSearchHelper.FILTER_ALL);
        filters.add(Order.STATUS_PENDING);
        filters.add(AdminOrderSearchHelper.FILTER_OVERDUE);
        filters.add(Order.STATUS_CONFIRMED);
        filters.add(Order.STATUS_SHIPPING);
        filters.add(Order.STATUS_DELIVERED);
        filters.add(AdminOrderSearchHelper.FILTER_RETURNED);
        filters.add(Order.STATUS_CANCELLED);
        for (String filter : filters) {
            counts.put(filter, countForFilter(orders, filter, searchQuery, lookup));
        }
        return counts;
    }

    public static long hoursToMillis(int hours) {
        return TimeUnit.HOURS.toMillis(hours);
    }

    public static String formatChipLabel(@NonNull String baseLabel, int count) {
        return baseLabel + " (" + count + ")";
    }
}
