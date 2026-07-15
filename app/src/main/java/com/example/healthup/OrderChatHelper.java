package com.example.healthup;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;
import com.example.models.OrderItem;

/** Opens buyer chat from order screens with product/order context. */
public final class OrderChatHelper {

    private OrderChatHelper() {
    }

    public static void openOrderChat(@NonNull Context context,
                                     @Nullable Order order,
                                     @Nullable String orderCodeFallback) {
        String orderCode = resolveOrderCode(order, orderCodeFallback);
        String orderId = order != null ? order.getId() : null;
        if (!TextUtils.isEmpty(orderCode)) {
            context.startActivity(ChatActivity.buyerIntentForOrder(context, orderCode, orderId));
        } else {
            context.startActivity(ChatActivity.buyerIntent(context));
        }
    }

    public static void openProductChat(@NonNull Context context,
                                       @Nullable Order order,
                                       @Nullable String orderCodeFallback,
                                       @NonNull OrderItem item) {
        String orderCode = resolveOrderCode(order, orderCodeFallback);
        String orderId = order != null ? order.getId() : null;
        String productId = item.getProductId();
        String productName = item.getName() != null ? item.getName() : "sản phẩm";
        String variant = item.getVariantLabel();
        if (!TextUtils.isEmpty(orderCode)) {
            context.startActivity(ChatActivity.buyerIntentForProduct(
                    context, orderCode, productName, variant, orderId, productId));
        } else {
            context.startActivity(ChatActivity.buyerIntent(context));
        }
    }

    @Nullable
    public static String resolveOrderCode(@Nullable Order order, @Nullable String orderCodeFallback) {
        if (order != null && !TextUtils.isEmpty(order.getOrderCode())) {
            return order.getOrderCode();
        }
        if (!TextUtils.isEmpty(orderCodeFallback)) {
            return orderCodeFallback;
        }
        if (order != null && !TextUtils.isEmpty(order.getId())) {
            return order.getId();
        }
        return null;
    }
}
