package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;

/**
 * Human-friendly order status explanations for the HealthUp chatbot.
 */
public final class OrderStatusCopy {

    private OrderStatusCopy() {
    }

    @NonNull
    public static String statusLabel(@Nullable String status) {
        if (status == null) {
            return "Đang xử lý";
        }
        switch (status) {
            case Order.STATUS_PENDING:
                return "Chờ xác nhận";
            case Order.STATUS_CONFIRMED:
                return "Đã xác nhận";
            case Order.STATUS_SHIPPING:
                return "Đang giao";
            case Order.STATUS_DELIVERED:
                return "Đã giao";
            case Order.STATUS_CANCELLED:
                return "Đã hủy";
            case Order.STATUS_RETURNED:
                return "Đã trả hàng";
            case Order.STATUS_COMPLETED:
                return "Hoàn tất";
            default:
                return "Đang xử lý";
        }
    }

    /**
     * HTML snippet with the status phrase in {@code <b>} for bot bubbles.
     */
    @NonNull
    public static String explainStatusHtml(@Nullable String orderCode, @Nullable String status) {
        String code = orderCode != null ? orderCode : "";
        String label = statusLabel(status);
        String body;
        if (status == null) {
            body = "HealthUp đang xử lý đơn này. Bạn có thể mở chi tiết để xem cập nhật mới nhất.";
        } else {
            switch (status) {
                case Order.STATUS_PENDING:
                    body = "Shop sẽ sớm xác nhận đơn. Bạn vẫn có thể hủy trong Đơn hàng của tôi "
                            + "nếu chưa muốn giữ đơn này.";
                    break;
                case Order.STATUS_CONFIRMED:
                    body = "Shop đã xác nhận và đang chuẩn bị hàng. HealthUp sẽ cập nhật khi đơn "
                            + "chuyển sang giai giao.";
                    break;
                case Order.STATUS_SHIPPING:
                    body = "Đơn đang trên đường giao đến bạn. Nếu cần đổi địa chỉ hoặc thời gian, "
                            + "hãy chat với nhân viên HealthUp nhé.";
                    break;
                case Order.STATUS_DELIVERED:
                    body = "Bạn đã nhận hàng thành công. Nếu sản phẩm có vấn đề, bạn có thể yêu cầu "
                            + "đổi/trả trong thời gian cho phép trong chi tiết đơn.";
                    break;
                case Order.STATUS_CANCELLED:
                    body = "Đơn này đã được hủy. Nếu bạn vẫn muốn mua, vào mục Sản phẩm để đặt lại nhé.";
                    break;
                case Order.STATUS_RETURNED:
                    body = "Đơn đang / đã trong quy trình trả hàng. Xem chi tiết để theo dõi tiến độ.";
                    break;
                case Order.STATUS_COMPLETED:
                    body = "Đơn đã hoàn tất. Cảm ơn bạn đã mua sắm cùng HealthUp!";
                    break;
                default:
                    body = "HealthUp đang cập nhật trạng thái. Bạn mở chi tiết đơn để xem thêm.";
                    break;
            }
        }
        return "Đơn <b>#" + code + "</b> hiện ở trạng thái <b>" + label + "</b>. " + body;
    }

    @NonNull
    public static String explainCancelHtml(@Nullable String orderCode, @Nullable String status) {
        String code = orderCode != null ? orderCode : "";
        String label = statusLabel(status);
        if (Order.STATUS_PENDING.equals(status)) {
            return "Đơn <b>#" + code + "</b> đang <b>" + label + "</b>. Bạn có thể tự hủy tại "
                    + "<b>Đơn hàng của tôi → chọn đơn → Hủy đơn</b>. "
                    + "Nếu cần HealthUp hỗ trợ thêm, chọn Chat với người bán nhé.";
        }
        return "Đơn <b>#" + code + "</b> đang <b>" + label + "</b> nên không hủy trực tiếp trên app được. "
                + "Mình có thể kết nối bạn với nhân viên HealthUp để xin hỗ trợ hủy / đổi đơn.";
    }
}
