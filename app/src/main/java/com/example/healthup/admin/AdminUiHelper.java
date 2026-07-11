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
            case Order.STATUS_RETURNED: return "Đổi trả";
            case Order.STATUS_COMPLETED: return "Hoàn thành trả hàng";
            default: return status;
        }
    }

    public static String orderStatusLabel(@Nullable Order order) {
        if (order == null) return "Không rõ";
        if (order.isShopConfirmedDelivery()
                && Order.STATUS_SHIPPING.equalsIgnoreCase(order.getStatus())) {
            return "Đã giao — chờ khách xác nhận";
        }
        if (order.isNeedsRedelivery()
                && Order.STATUS_SHIPPING.equalsIgnoreCase(order.getStatus())) {
            return "Giao thất bại — chờ giao lại lần " + (order.getDeliveryAttempts() + 1);
        }
        if (Order.RETURN_REQUESTED.equals(order.getReturnStatus())
                || Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
            return "Yêu cầu trả hàng";
        }
        if (Order.RETURN_APPROVED.equals(order.getReturnStatus())) {
            return "Đang xử lý trả hàng";
        }
        if (order.isReturnRejected()) {
            return "Trả hàng không thành công";
        }
        if (order.isReturnCompleted()) {
            return "Hoàn thành trả hàng";
        }
        return statusLabel(order.getStatus());
    }

    public static String historyLabel(@Nullable AdminRepository.OrderHistoryEntry entry) {
        return historyEventLabel(entry);
    }

    /** Short Vietnamese event title for timeline cards. */
    public static String historyEventLabel(@Nullable AdminRepository.OrderHistoryEntry entry) {
        if (entry == null) return "—";
        if (entry.event != null && !entry.event.isEmpty()) {
            switch (entry.event) {
                case "order_confirmed": return "Xác nhận đơn";
                case "shipping_started": return "Bắt đầu giao hàng";
                case "shop_confirmed_delivery": return "Shop xác nhận đã giao";
                case "delivery_failed": return "Giao thất bại";
                case "delivery_failed_cancelled": return "Hủy do giao thất bại";
                case "redelivery_scheduled": return "Giao lại";
                case "customer_received": return "Khách xác nhận đã nhận";
                case "customer_cancelled": return "Khách hủy đơn";
                case "return_requested": return "Khách yêu cầu trả hàng";
                case "return_approved": return "Duyệt yêu cầu trả hàng";
                case "return_progress": return "Cập nhật tiến trình trả hàng";
                case "return_rejected": return "Từ chối trả hàng";
                case "return_completed": return "Hoàn tất trả hàng";
                case "order_created": return "Tạo đơn hàng";
                case "status_changed": return "Cập nhật trạng thái";
                default: break;
            }
        }
        if (entry.fromStatus != null || entry.toStatus != null) {
            return statusLabel(entry.fromStatus) + " → " + statusLabel(entry.toStatus);
        }
        if (entry.note != null && !entry.note.isEmpty()) {
            return entry.note;
        }
        return "Cập nhật đơn hàng";
    }

    public static String historyActorLabel(@Nullable AdminRepository.OrderHistoryEntry entry) {
        if (entry == null) return "";
        String role;
        if ("admin".equalsIgnoreCase(entry.actorRole)) {
            role = "Admin";
        } else if ("buyer".equalsIgnoreCase(entry.actorRole)
                || "customer".equalsIgnoreCase(entry.actorRole)) {
            role = "Khách hàng";
        } else if (entry.actorRole != null && !entry.actorRole.isEmpty()) {
            role = entry.actorRole;
        } else if (entry.adminEmail != null && !entry.adminEmail.isEmpty()) {
            role = "Admin";
        } else {
            role = "";
        }
        if (entry.adminEmail != null && !entry.adminEmail.isEmpty()) {
            return role.isEmpty() ? entry.adminEmail : (role + " • " + entry.adminEmail);
        }
        return role;
    }

    public static String historyNoteLabel(@Nullable AdminRepository.OrderHistoryEntry entry) {
        if (entry == null) return "";
        if (entry.note != null && !entry.note.isEmpty()) {
            return entry.note;
        }
        if (entry.reason != null && !entry.reason.isEmpty()) {
            return "Lý do: " + entry.reason;
        }
        return "";
    }
}
