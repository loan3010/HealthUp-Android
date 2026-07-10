package com.example.healthup.admin;

import androidx.annotation.NonNull;

import com.example.models.Order;

import java.util.List;
import java.util.Locale;

public final class AdminDashboardAnalytics {

    private AdminDashboardAnalytics() {
    }

    public static class PeriodMetrics {
        public double deliveredRevenue;
        public double placedGmv;
        public int ordersPlaced;
        public int ordersDelivered;
    }

    public static class ComparisonRow {
        public String label;
        public String primaryValue;
        public String compareValue;
        public String deltaText;
        public boolean positive;
    }

    @NonNull
    public static PeriodMetrics compute(@NonNull List<Order> orders,
                                        @NonNull AdminDashboardPeriodHelper.DateRange range) {
        PeriodMetrics metrics = new PeriodMetrics();
        for (Order order : orders) {
            if (order == null) continue;
            String status = order.getStatus() != null ? order.getStatus().toLowerCase(Locale.ROOT) : "";
            double total = order.getTotalPrice();

            long createdAt = order.getCreatedAt() != null ? order.getCreatedAt().getTime() : -1L;
            if (createdAt >= 0 && range.contains(createdAt) && !Order.STATUS_CANCELLED.equals(status)) {
                metrics.ordersPlaced++;
                metrics.placedGmv += total;
            }

            if (!Order.STATUS_DELIVERED.equals(status)) {
                continue;
            }
            long deliveredAt = resolveDeliveredTime(order);
            if (deliveredAt >= 0 && range.contains(deliveredAt)) {
                metrics.ordersDelivered++;
                metrics.deliveredRevenue += total;
            }
        }
        return metrics;
    }

    private static long resolveDeliveredTime(@NonNull Order order) {
        if (order.getDeliveredAt() != null) {
            return order.getDeliveredAt().getTime();
        }
        if (order.getUpdatedAt() != null) {
            return order.getUpdatedAt().getTime();
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt().getTime();
        }
        return -1L;
    }

    @NonNull
    public static String formatMoney(double amount, @NonNull java.text.NumberFormat format) {
        return format.format(amount) + " đ";
    }

    @NonNull
    public static String formatCountDelta(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? "mới" : "—";
        }
        double pct = ((double) (current - previous) / previous) * 100.0;
        return String.format(Locale.getDefault(), "%+.0f%%", pct);
    }

    @NonNull
    public static String formatMoneyDelta(double current, double previous) {
        if (previous <= 0) {
            return current > 0 ? "mới" : "—";
        }
        double pct = ((current - previous) / previous) * 100.0;
        return String.format(Locale.getDefault(), "%+.0f%%", pct);
    }

    public static boolean isPositiveDelta(double current, double previous) {
        return current >= previous;
    }

    public static boolean isPositiveCountDelta(int current, int previous) {
        return current >= previous;
    }
}
