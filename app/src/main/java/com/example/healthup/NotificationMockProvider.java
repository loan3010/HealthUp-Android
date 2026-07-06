package com.example.healthup;

import com.example.models.NotificationItem;
import com.example.models.NotificationType;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Sample notifications for UI preview when Firestore has no data yet. */
public final class NotificationMockProvider {

    private NotificationMockProvider() {
    }

    public static List<NotificationItem> getSamples() {
        List<NotificationItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();

        items.add(mockItem(
                "mock_shipping",
                NotificationType.ORDER_SHIPPING,
                "0621XMHU3845",
                "Đơn hàng đang giao",
                "Đơn #0621XMHU3845 đang trên đường giao đến bạn. Dự kiến giao trong hôm nay.",
                now - TimeUnit.HOURS.toMillis(2)));
        items.add(mockItem(
                "mock_promo",
                NotificationType.PROMO,
                "HEALTHUP20",
                "Khuyến mãi cuối tuần",
                "Giảm 20% granola & snack lành mạnh. Mã: HEALTHUP20. Áp dụng đến Chủ nhật.",
                now - TimeUnit.HOURS.toMillis(8)));
        items.add(mockItem(
                "mock_payment",
                NotificationType.PAYMENT,
                "0621XMHU3845",
                "Thanh toán thành công",
                "Bạn đã thanh toán 385.000đ cho đơn hàng. Cảm ơn bạn đã mua sắm tại HealthUp!",
                now - TimeUnit.DAYS.toMillis(1)));
        items.add(mockItem(
                "mock_wishlist",
                NotificationType.WISHLIST_SALE,
                null,
                "Sản phẩm yêu thích giảm giá",
                "Granola siêu ngon trong wishlist của bạn vừa giảm 15%. Mua ngay kẻo hết!",
                now - TimeUnit.DAYS.toMillis(2)));
        items.add(mockItem(
                "mock_review",
                NotificationType.REVIEW_REMINDER,
                "0621XMHU3845",
                "Nhắc đánh giá sản phẩm",
                "Bạn đã nhận hàng 3 ngày. Hãy chia sẻ trải nghiệm để nhận ưu đãi nhé.",
                now - TimeUnit.DAYS.toMillis(3)));
        return items;
    }

    private static NotificationItem mockItem(
            String id,
            NotificationType type,
            String refId,
            String title,
            String body,
            long timeMs) {
        NotificationItem item = new NotificationItem();
        item.setId(id);
        item.setType(type.name());
        item.setRefId(refId);
        item.setTitle(title);
        item.setBody(body);
        item.setCreatedAt(new Timestamp(new Date(timeMs)));
        item.setRead(false);
        item.setMock(true);
        return item;
    }
}
