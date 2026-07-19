package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AdminOrderListHelper {

    /** Pin actionable orders (cancel/return requests) on top, then latest activity first. */
    public static final String SORT_PRIORITY = "priority";
    /** Pure by order creation time, newest placed first (no pinning). */
    public static final String SORT_NEWEST = "newest";
    /** Pure by order creation time, oldest first (no pinning). */
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

    /** Order creation time (falls back to latest activity when createdAt is missing). */
    public static long getCreatedTime(@NonNull Order order) {
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt().getTime();
        }
        return getSortTime(order);
    }

    public static void sort(@NonNull List<Order> orders, @Nullable String sortKey) {
        if (SORT_NEWEST.equals(sortKey)) {
            // Newest placed order first — no priority pinning, pure createdAt.
            orders.sort((a, b) -> Long.compare(getCreatedTime(b), getCreatedTime(a)));
            return;
        }
        if (SORT_OLDEST.equals(sortKey)) {
            orders.sort((a, b) -> Long.compare(getCreatedTime(a), getCreatedTime(b)));
            return;
        }
        // Default "priority": pin actionable return/cancel requests on top, then
        // within each priority group show the latest activity first. This keeps a
        // freshly-created return/cancel request at the top of its group (and, inside
        // the "Trả hàng" tab, the newest return request naturally floats up).
        orders.sort((a, b) -> {
            int pa = actionPriority(a);
            int pb = actionPriority(b);
            if (pa != pb) {
                return Integer.compare(pa, pb); // lower = higher priority (0 first)
            }
            return Long.compare(getSortTime(b), getSortTime(a));
        });
    }

    /**
     * 0 = needs staff now (return requested / cancel requested),
     * 1 = in progress return,
     * 2 = everything else.
     */
    private static int actionPriority(@NonNull Order order) {
        if (order.isCancelRequested()
                || Order.RETURN_REQUESTED.equals(order.getReturnStatus())
                || Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
            return 0;
        }
        if (Order.RETURN_APPROVED.equals(order.getReturnStatus())) {
            return 1;
        }
        return 2;
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

    /**
     * An order needs staff action (and can become overdue) when it is:
     * - awaiting fulfillment (pending/confirmed), OR
     * - a return/refund request still waiting for approval, OR
     * - a cancel request not yet resolved.
     */
    private static boolean needsAction(@NonNull Order order) {
        return isFulfillmentPending(order)
                || isReturnRequestPending(order)
                || isCancelRequestPending(order);
    }

    private static boolean isFulfillmentPending(@NonNull Order order) {
        String status = order.getStatus();
        if (status == null) return false;
        return ACTION_STATUSES.contains(status.toLowerCase(Locale.ROOT).trim());
    }

    /** Return/refund request submitted by the buyer, admin hasn't approved/rejected yet. */
    private static boolean isReturnRequestPending(@NonNull Order order) {
        return Order.RETURN_REQUESTED.equals(order.getReturnStatus());
    }

    /** Cancel request submitted by the buyer, order not yet cancelled. */
    private static boolean isCancelRequestPending(@NonNull Order order) {
        if (!order.isCancelRequested()) return false;
        String status = order.getStatus();
        return status == null || !Order.STATUS_CANCELLED.equalsIgnoreCase(status.trim());
    }

    private static long getOverdueReferenceTime(@NonNull Order order) {
        // Return/cancel requests are timed from when the buyer submitted the request,
        // so an unhandled request becomes overdue 24h after it was raised.
        if (isReturnRequestPending(order) && order.getReturnRequestedAt() != null) {
            return order.getReturnRequestedAt().getTime();
        }
        if (isCancelRequestPending(order) && order.getCancelRequestedAt() != null) {
            return order.getCancelRequestedAt().getTime();
        }
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
