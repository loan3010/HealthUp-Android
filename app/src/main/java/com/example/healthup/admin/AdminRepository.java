package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;
import com.example.models.Product;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminRepository {

    private static final String TAG = "AdminRepository";

    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    public interface ProductsCallback {
        void onSuccess(@NonNull List<Product> products);
        void onError(@NonNull String message);
    }

    public interface OrdersCallback {
        void onSuccess(@NonNull List<Order> orders);
        void onError(@NonNull String message);
    }

    public interface CustomersCallback {
        void onSuccess(@NonNull List<AdminCustomer> customers);
        void onError(@NonNull String message);
    }

    public interface DashboardCallback {
        void onSuccess(@NonNull DashboardStats stats);
        void onError(@NonNull String message);
    }

    public static class DashboardStats {
        public int productCount;
        public int orderCount;
        public int pendingOrders;
        public int lowStockProducts;
        public double revenue;
    }

    public static class AdminCustomer {
        public String uid;
        public String fullName;
        public String phone;
        public String email;
        public String role;
        public boolean disabled;
        public long spentAmount;
    }

    public static class OrderHistoryEntry {
        public String id;
        public String fromStatus;
        public String toStatus;
        public String adminUid;
        public String adminEmail;
        public Timestamp createdAt;
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadDashboard(@NonNull DashboardCallback callback) {
        DashboardStats stats = new DashboardStats();
        db.collection("products").get()
                .addOnSuccessListener(productsSnap -> {
                    stats.productCount = productsSnap.size();
                    for (DocumentSnapshot doc : productsSnap) {
                        Long stock = doc.getLong("stock");
                        if (stock == null) {
                            stock = doc.getLong("stockCount");
                        }
                        if (stock != null && stock < 10) {
                            stats.lowStockProducts++;
                        }
                    }
                    db.collection("orders").get()
                            .addOnSuccessListener(ordersSnap -> {
                                stats.orderCount = ordersSnap.size();
                                for (DocumentSnapshot doc : ordersSnap) {
                                    String status = doc.getString("status");
                                    if ("pending".equals(status)) {
                                        stats.pendingOrders++;
                                    }
                                    if ("delivered".equals(status)) {
                                        Double total = doc.getDouble("totalPrice");
                                        if (total != null) {
                                            stats.revenue += total;
                                        }
                                    }
                                }
                                callback.onSuccess(stats);
                            })
                            .addOnFailureListener(e -> callback.onError(errorMessage(e)));
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadProducts(@NonNull ProductsCallback callback) {
        db.collection("products").get()
                .addOnSuccessListener(snap -> {
                    List<Product> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        Product product = Product.fromDocument(doc);
                        if (product != null) {
                            list.add(product);
                        }
                    }
                    list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void saveProduct(@NonNull Product product, boolean isNew, @NonNull SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", product.getName());
        data.put("price", product.getPrice());
        data.put("originalPrice", product.getOriginalPrice() > 0 ? product.getOriginalPrice() : product.getPrice());
        data.put("oldPrice", product.getOriginalPrice() > 0 ? product.getOriginalPrice() : product.getPrice());
        data.put("stock", product.getStock());
        data.put("stockCount", product.getStock());
        data.put("cat", product.getCategory());
        data.put("shortDesc", product.getShortDesc());
        data.put("description", product.getDescription());
        data.put("images", product.getImages() != null ? product.getImages() : new ArrayList<String>());
        data.put("updatedAt", Timestamp.now());
        if (isNew) {
            data.put("createdAt", Timestamp.now());
            data.put("sold", 0);
            data.put("reviewCount", 0);
            data.put("isNew", true);
        }

        if (isNew || product.getId() == null || product.getId().isEmpty()) {
            db.collection("products").add(data)
                    .addOnSuccessListener(ref -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(errorMessage(e)));
        } else {
            db.collection("products").document(product.getId()).set(data)
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(errorMessage(e)));
        }
    }

    public void deleteProduct(@NonNull String productId, @NonNull SimpleCallback callback) {
        db.collection("products").document(productId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadOrders(@NonNull OrdersCallback callback) {
        db.collection("orders").get()
                .addOnSuccessListener(snap -> {
                    List<Order> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Order order = parseOrderDocument(doc);
                        if (order != null) {
                            list.add(order);
                        }
                    }
                    list.sort((a, b) -> Long.compare(getOrderSortTime(b), getOrderSortTime(a)));
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    @Nullable
    static Order parseOrderDocument(@NonNull DocumentSnapshot doc) {
        try {
            Order order = doc.toObject(Order.class);
            if (order == null) {
                return null;
            }
            order.setId(doc.getId());

            if (order.getCreatedAt() == null) {
                order.setCreatedAt(doc.getTimestamp("createdAt"));
            }
            if (order.getUpdatedAt() == null) {
                order.setUpdatedAt(doc.getTimestamp("updatedAt"));
            }
            if (order.getUserId() == null) {
                String userId = doc.getString("userId");
                if (userId == null) {
                    userId = doc.getString("buyerId");
                }
                order.setUserId(userId);
            }
            if (order.getStatus() == null) {
                order.setStatus(doc.getString("status"));
            }
            if (order.getOrderCode() == null) {
                order.setOrderCode(doc.getString("orderCode"));
            }

            Double totalPrice = doc.getDouble("totalPrice");
            if (totalPrice == null) {
                totalPrice = doc.getDouble("totalAmount");
            }
            if (totalPrice != null) {
                order.setTotalPrice(totalPrice);
            }
            return order;
        } catch (Exception e) {
            Log.e(TAG, "Skip invalid order document: " + doc.getId(), e);
            return null;
        }
    }

    private static long getOrderSortTime(@NonNull Order order) {
        if (order.getUpdatedAt() != null) {
            return order.getUpdatedAt().getSeconds();
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt().getSeconds();
        }
        return 0L;
    }

    private static long getHistorySortTime(@NonNull OrderHistoryEntry entry) {
        return entry.createdAt != null ? entry.createdAt.getSeconds() : 0L;
    }

    public void updateOrderStatus(@NonNull String orderId,
                                  @Nullable String fromStatus,
                                  @NonNull String toStatus,
                                  @NonNull SimpleCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", toStatus);
        updates.put("updatedAt", Timestamp.now());
        if ("delivered".equals(toStatus)) {
            updates.put("deliveredAt", Timestamp.now());
        }

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(orderId), updates);

        Map<String, Object> history = new HashMap<>();
        history.put("fromStatus", fromStatus != null ? fromStatus : "");
        history.put("toStatus", toStatus);
        history.put("adminUid", user.getUid());
        history.put("adminEmail", user.getEmail() != null ? user.getEmail() : "");
        history.put("createdAt", Timestamp.now());
        batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadOrderHistory(@NonNull String orderId,
                                 @NonNull com.example.healthup.admin.AdminRepository.OrderHistoryCallback callback) {
        db.collection("orders").document(orderId).collection("history")
                .get()
                .addOnSuccessListener(snap -> {
                    List<OrderHistoryEntry> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        OrderHistoryEntry entry = new OrderHistoryEntry();
                        entry.id = doc.getId();
                        entry.fromStatus = doc.getString("fromStatus");
                        entry.toStatus = doc.getString("toStatus");
                        entry.adminUid = doc.getString("adminUid");
                        entry.adminEmail = doc.getString("adminEmail");
                        entry.createdAt = doc.getTimestamp("createdAt");
                        list.add(entry);
                    }
                    list.sort((a, b) -> Long.compare(getHistorySortTime(a), getHistorySortTime(b)));
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public interface OrderHistoryCallback {
        void onSuccess(@NonNull List<OrderHistoryEntry> entries);
        void onError(@NonNull String message);
    }

    public void loadCustomers(@NonNull CustomersCallback callback) {
        db.collection("users").get()
                .addOnSuccessListener(snap -> {
                    List<AdminCustomer> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        String role = doc.getString("role");
                        if (role == null) {
                            role = doc.getString("userRole");
                        }
                        if (role != null && "admin".equals(role.toLowerCase(Locale.ROOT))) {
                            continue;
                        }
                        AdminCustomer customer = new AdminCustomer();
                        customer.uid = doc.getId();
                        customer.fullName = firstNonEmpty(doc.getString("fullName"), doc.getString("name"), doc.getString("displayName"));
                        customer.phone = doc.getString("phone");
                        customer.email = firstNonEmpty(doc.getString("displayEmail"), doc.getString("email"));
                        customer.role = role != null ? role : "buyer";
                        Boolean disabled = doc.getBoolean("disabled");
                        customer.disabled = disabled != null && disabled;
                        Long spent = doc.getLong("spentAmount");
                        customer.spentAmount = spent != null ? spent : 0;
                        list.add(customer);
                    }
                    list.sort((a, b) -> {
                        String nameA = a.fullName != null ? a.fullName : "";
                        String nameB = b.fullName != null ? b.fullName : "";
                        return nameA.compareToIgnoreCase(nameB);
                    });
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void setCustomerDisabled(@NonNull String uid, boolean disabled, @NonNull SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("disabled", disabled);
        updates.put("updatedAt", Timestamp.now());
        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    @NonNull
    private static String errorMessage(@NonNull Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Có lỗi xảy ra";
    }
}
