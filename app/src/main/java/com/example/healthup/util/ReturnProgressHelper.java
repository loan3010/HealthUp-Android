package com.example.healthup.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;

/**
 * Shared return/refund/reship progress steps for admin and client UIs.
 *
 * Client "Tiến trình xử lý" steps by handling type:
 * <ul>
 *   <li>Trả hàng &amp; Hoàn tiền — max 4: duyệt → chờ gửi trả → kiểm tra → hoàn tiền</li>
 *   <li>Hoàn tiền sản phẩm bị thiếu — max 3: duyệt → xử lý hoàn tiền → hoàn tiền xong</li>
 *   <li>Nhận bổ sung… — max 3: duyệt → chuẩn bị hàng bù → gửi bù xong</li>
 * </ul>
 *
 * {@code returnStep}: 0 = vừa gửi; 1 = đã duyệt; …; max = hoàn tất.
 */
public final class ReturnProgressHelper {

    public static final String HANDLING_RETURN_REFUND = "Trả hàng & Hoàn tiền";
    public static final String HANDLING_MISSING_REFUND = "Hoàn tiền sản phẩm bị thiếu";
    public static final String HANDLING_RESHIP = "Nhận bổ sung sản phẩm bị thiếu";

    private ReturnProgressHelper() {}

    @NonNull
    public static String normalizeHandling(@Nullable String handling) {
        if (handling == null || handling.trim().isEmpty()) {
            return HANDLING_RETURN_REFUND;
        }
        return handling.trim();
    }

    public static boolean isReship(@Nullable String handling) {
        String h = normalizeHandling(handling);
        return h.contains("bổ sung") || h.contains("Nhận bổ sung");
    }

    public static boolean isMissingRefund(@Nullable String handling) {
        return HANDLING_MISSING_REFUND.equals(normalizeHandling(handling));
    }

    public static boolean isReturnRefund(@Nullable String handling) {
        return HANDLING_RETURN_REFUND.equals(normalizeHandling(handling));
    }

    /** Final step number for this handling type (completed when returnStep >= maxStep). */
    public static int maxStep(@Nullable String handling) {
        return isReturnRefund(handling) ? 4 : 3;
    }

    public static int maxStep(@NonNull Order order) {
        return maxStep(order.getReturnHandling());
    }

    /**
     * Label for the admin "advance" button for the *next* step after current returnStep.
     * Null if no further advance (need approve first, or already done / rejected).
     */
    @Nullable
    public static String nextAdvanceLabel(@NonNull Order order) {
        String status = order.getReturnStatus();
        if (Order.RETURN_REJECTED.equals(status) || Order.RETURN_COMPLETED.equals(status)) {
            return null;
        }
        if (Order.RETURN_REQUESTED.equals(status)
                || Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
            return "Duyệt yêu cầu";
        }
        if (!Order.RETURN_APPROVED.equals(status)) {
            return null;
        }
        int step = Math.max(order.getReturnStep(), 1);
        int max = maxStep(order);
        if (step >= max) {
            return null;
        }
        int next = step + 1;
        String handling = normalizeHandling(order.getReturnHandling());
        if (isReturnRefund(handling)) {
            switch (next) {
                case 2: return "Xác nhận chờ khách gửi trả";
                case 3: return "Đang kiểm tra hàng trả";
                case 4: return "Hoàn tất hoàn tiền";
                default: return "Cập nhật tiến trình";
            }
        }
        if (isMissingRefund(handling)) {
            switch (next) {
                case 2: return "Đang xử lý hoàn tiền";
                case 3: return "Hoàn tất hoàn tiền";
                default: return "Cập nhật tiến trình";
            }
        }
        // Reship
        switch (next) {
            case 2: return "Đang chuẩn bị hàng gửi bù";
            case 3: return "Đã gửi hàng bổ sung";
            default: return "Cập nhật tiến trình";
        }
    }

    /** Title shown on client timeline for a completed/active step index (1..max). */
    @NonNull
    public static String stepTitle(@Nullable String handling, int step) {
        String h = normalizeHandling(handling);
        if (step <= 1) {
            return step == 0 ? "Yêu cầu đã được gửi" : "HealthUp đã duyệt yêu cầu";
        }
        if (isReturnRefund(h)) {
            switch (step) {
                case 2: return "Đang chờ khách hàng gửi trả hàng";
                case 3: return "Đang kiểm tra hàng trả";
                case 4: return "Hoàn tiền thành công";
                default: return "Đang xử lý";
            }
        }
        if (isMissingRefund(h)) {
            switch (step) {
                case 2: return "Đang xử lý hoàn tiền";
                case 3: return "Hoàn tiền thành công";
                default: return "Đang xử lý";
            }
        }
        switch (step) {
            case 2: return "Đang chuẩn bị hàng gửi bù";
            case 3: return "Đã gửi hàng bổ sung thành công";
            default: return "Đang xử lý";
        }
    }
}
