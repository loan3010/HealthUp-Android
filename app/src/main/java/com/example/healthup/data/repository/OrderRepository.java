package com.example.healthup.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reads buyer orders for the chatbot "order status lookup" feature.
 *
 * <p>Uses a single {@code whereEqualTo("buyerId", ...)} query (covered by the
 * automatic single-field index, so no composite index is required) and sorts by
 * createdAt on the client. Degrades gracefully to an empty list when the
 * {@code orders} collection does not exist or the query fails.</p>
 */
public class OrderRepository {

    private static final int MAX_ORDERS = 10;

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
        firestore.collection("orders")
                .whereEqualTo("buyerId", buyerId)
                .limit(MAX_ORDERS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> orders = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        orders.add(mapOrder(doc));
                    }
                    Collections.sort(orders, (a, b) -> {
                        Timestamp ta = a.getCreatedAt();
                        Timestamp tb = b.getCreatedAt();
                        long aMillis = ta != null ? ta.toDate().getTime() : 0L;
                        long bMillis = tb != null ? tb.toDate().getTime() : 0L;
                        return Long.compare(bMillis, aMillis);
                    });
                    callback.onResult(orders);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
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

        Double total = doc.getDouble("totalAmount");
        if (total == null) {
            total = doc.getDouble("totalPrice");
        }
        if (total == null) {
            total = doc.getDouble("total");
        }
        order.setTotalAmount(total != null ? total : 0d);

        Long itemCount = doc.getLong("itemCount");
        if (itemCount == null && order.getItems() != null) {
            itemCount = (long) order.getItems().size();
        }
        order.setItemCount(itemCount != null ? itemCount.intValue() : 0);

        Timestamp createdAt = doc.getTimestamp("createdAt");
        if (createdAt == null && doc.getDate("createdAt") != null) {
            createdAt = new Timestamp(doc.getDate("createdAt"));
        }
        order.setCreatedAt(createdAt);
        return order;
    }
}
