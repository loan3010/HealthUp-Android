package com.example.healthup.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;
import com.example.models.OrderItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Reads buyer orders for the chatbot "order status lookup" feature.
 *
 * <p>Loads by {@code buyerId} (falls back to {@code userId} when empty), sorts by
 * {@code createdAt} descending on the client, then keeps the most recent
 * {@link #MAX_ORDERS} only — so a buyer with 100 orders still sees at most 10
 * recent ones. Uses Number-safe total mapping because Checkout writes
 * {@code totalPrice}.</p>
 */
public class OrderRepository {

    public static final int MAX_ORDERS = 10;

    private final FirebaseFirestore firestore;

    public OrderRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public OrderRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public interface OrdersCallback {
        void onResult(@NonNull List<Order> orders);
    }

    public void getOrdersForBuyer(@Nullable String buyerId, @NonNull OrdersCallback callback) {
        if (buyerId == null || buyerId.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        // No server orderBy: avoids composite index. Fetch buyer docs, sort client-side,
        // then keep latest MAX_ORDERS (true "gần đây", not an arbitrary limit slice).
        firestore.collection("orders")
                .whereEqualTo("buyerId", buyerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = mapAndSort(snapshot.getDocuments());
                    if (!orders.isEmpty()) {
                        callback.onResult(orders);
                        return;
                    }
                    // Legacy docs may only have userId (before getBuyerId alias existed).
                    firestore.collection("orders")
                            .whereEqualTo("userId", buyerId)
                            .get()
                            .addOnSuccessListener(fallback ->
                                    callback.onResult(mapAndSort(fallback.getDocuments())))
                            .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    @NonNull
    private List<Order> mapAndSort(@NonNull List<? extends DocumentSnapshot> docs) {
        List<Order> orders = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            if (doc != null && doc.exists()) {
                orders.add(mapOrder(doc));
            }
        }
        Collections.sort(orders, (a, b) -> {
            Date ta = a.getCreatedAt();
            Date tb = b.getCreatedAt();
            long aMillis = ta != null ? ta.getTime() : 0L;
            long bMillis = tb != null ? tb.getTime() : 0L;
            return Long.compare(bMillis, aMillis);
        });
        if (orders.size() > MAX_ORDERS) {
            return new ArrayList<>(orders.subList(0, MAX_ORDERS));
        }
        return orders;
    }

    private Order mapOrder(DocumentSnapshot doc) {
        Order order = new Order();
        order.setId(doc.getId());
        order.setBuyerId(doc.getString("buyerId") != null
                ? doc.getString("buyerId")
                : doc.getString("userId"));
        String code = doc.getString("orderCode");
        order.setOrderCode(code != null ? code : doc.getId());
        order.setStatus(doc.getString("status"));

        // Canonical Checkout field is totalPrice; Number-safe (Long or Double).
        Double total = firstNumber(doc,
                "totalPrice", "totalAmount", "total",
                "grandTotal", "finalTotal", "totalPayment");
        order.setTotalAmount(total != null ? total : 0d);

        mapItems(doc, order);
        order.setCreatedAt(doc.getDate("createdAt"));
        return order;
    }

    @SuppressWarnings("unchecked")
    private void mapItems(@NonNull DocumentSnapshot doc, @NonNull Order order) {
        Object itemsObj = doc.get("items");
        if (itemsObj instanceof List) {
            List<OrderItem> items = new ArrayList<>();
            for (Object raw : (List<?>) itemsObj) {
                if (!(raw instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) raw;
                OrderItem item = new OrderItem();
                item.setName(asString(map.get("name")));
                item.setImageUrl(asString(map.get("imageUrl")));
                item.setVariantLabel(asString(map.get("variantLabel")));
                Object qty = map.get("quantity");
                if (qty instanceof Number) {
                    item.setQuantity(((Number) qty).intValue());
                }
                items.add(item);
            }
            order.setItems(items);
            order.setItemCount(items.size());
            return;
        }
        Double itemCountNum = readNumber(doc, "itemCount");
        order.setItemCount(itemCountNum != null ? itemCountNum.intValue() : 0);
    }

    @Nullable
    private static Double firstNumber(@NonNull DocumentSnapshot doc, @NonNull String... fields) {
        for (String field : fields) {
            Double value = readNumber(doc, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static Double readNumber(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Object val = doc.get(field);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return null;
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value instanceof String ? (String) value : null;
    }
}
