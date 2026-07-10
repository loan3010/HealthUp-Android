package com.example.healthup.admin;

import androidx.annotation.Nullable;

import com.example.models.Order;

public final class AdminUiHelper {

    private AdminUiHelper() {
    }

    public static String statusLabel(@Nullable String status) {
        if (status == null) return "Không rõ";
        switch (status) {
            case Order.STATUS_PENDING: return "Chờ xác nhận";
            case Order.STATUS_CONFIRMED: return "Đã xác nhận";
            case Order.STATUS_SHIPPING: return "Đang giao";
            case Order.STATUS_DELIVERED: return "Đã giao";
            case Order.STATUS_CANCELLED: return "Đã hủy";
            case "returned": return "Đổi trả";
            default: return status;
        }
    }

    public static String orderStatusLabel(@Nullable Order order) {
        if (order == null) return "Không rõ";
        if (order.isCancelRequested() && Order.STATUS_PENDING.equalsIgnoreCase(order.getStatus())) {
            return "Yêu cầu hủy";
        }
        return statusLabel(order.getStatus());
    }
}
