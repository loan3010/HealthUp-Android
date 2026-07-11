package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Order;
import com.example.models.Product;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.text.TextUtils;
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

    public interface CustomerLookupCallback {
        void onSuccess(@NonNull Map<String, AdminCustomer> lookup);
        void onError(@NonNull String message);
    }

    public interface CustomerOrderStatsCallback {
        void onSuccess(int totalOrders, int pendingOrders);
        void onError(@NonNull String message);
    }

    public interface DashboardCallback {
        void onSuccess(@NonNull DashboardStats stats);
        void onError(@NonNull String message);
    }

    public interface DashboardDataCallback {
        void onSuccess(@NonNull DashboardData data);
        void onError(@NonNull String message);
    }

    public static class DashboardStats {
        public int productCount;
        public int orderCount;
        public int pendingOrders;
        public int cancelRequestedOrders;
        public int lowStockProducts;
        public double revenue;
    }

    public static class DashboardData {
        public int productCount;
        public int lowStockProducts;
        public int pendingOrders;
        public int cancelRequestedOrders;
        public int overdueOrders;
        public int returnRequests;
        public List<Order> orders = new ArrayList<>();
    }

    public static class AdminCustomer {
        public String uid;
        public String fullName;
        public String username;
        public String phone;
        public String email;
        public String role;
        public boolean disabled;
        public String disabledReason;
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
        loadDashboardData(new DashboardDataCallback() {
            @Override
            public void onSuccess(@NonNull DashboardData data) {
                DashboardStats stats = new DashboardStats();
                stats.productCount = data.productCount;
                stats.orderCount = data.orders.size();
                stats.pendingOrders = data.pendingOrders;
                stats.cancelRequestedOrders = data.cancelRequestedOrders;
                stats.lowStockProducts = data.lowStockProducts;
                for (Order order : data.orders) {
                    if (Order.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())) {
                        stats.revenue += order.getTotalPrice();
                    }
                }
                callback.onSuccess(stats);
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    public void loadDashboardData(@NonNull DashboardDataCallback callback) {
        DashboardData data = new DashboardData();
        long overdueThreshold = AdminOrderListHelper.hoursToMillis(24);
        db.collection("products").get()
                .addOnSuccessListener(productsSnap -> {
                    data.productCount = productsSnap.size();
                    for (DocumentSnapshot doc : productsSnap) {
                        Long stock = doc.getLong("stock");
                        if (stock == null) {
                            stock = doc.getLong("stockCount");
                        }
                        boolean hidden = Boolean.TRUE.equals(doc.getBoolean("hidden"));
                        boolean draft = Boolean.TRUE.equals(doc.getBoolean("draft"));
                        boolean active = !hidden && !draft;
                        if (active && stock != null && stock < 10) {
                            data.lowStockProducts++;
                        }
                    }
                    db.collection("orders").get()
                            .addOnSuccessListener(ordersSnap -> {
                                for (DocumentSnapshot doc : ordersSnap) {
                                    Order order = parseOrderDocument(doc);
                                    if (order == null) continue;
                                    data.orders.add(order);

                                    String status = order.getStatus();
                                    if (Order.STATUS_PENDING.equalsIgnoreCase(status)) {
                                        if (order.isCancelRequested()) {
                                            data.cancelRequestedOrders++;
                                        } else {
                                            data.pendingOrders++;
                                        }
                                    }
                                    if (AdminOrderSearchHelper.matches(order, "",
                                            AdminOrderSearchHelper.FILTER_RETURNED, null)) {
                                        data.returnRequests++;
                                    }
                                    if (AdminOrderListHelper.isOverdue(order, overdueThreshold)) {
                                        data.overdueOrders++;
                                    }
                                }
                                callback.onSuccess(data);
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
        data.put("hidden", product.isHidden());
        data.put("draft", product.isDraft());
        data.put("hasVariants", product.isHasVariants());

        List<Map<String, Object>> variantMaps = new ArrayList<>();
        if (product.getVariants() != null) {
            for (Product.ProductVariant variant : product.getVariants()) {
                if (variant == null || variant.getName() == null || variant.getName().isEmpty()) continue;
                Map<String, Object> variantData = new HashMap<>();
                variantData.put("id", variant.getId() != null ? variant.getId() : ("variant_" + variantMaps.size()));
                variantData.put("name", variant.getName());
                variantData.put("price", variant.getPrice());
                variantData.put("originalPrice", variant.getOriginalPrice() > 0 ? variant.getOriginalPrice() : variant.getPrice());
                variantData.put("stock", variant.getStock());
                if (!TextUtils.isEmpty(variant.getSku())) {
                    variantData.put("sku", variant.getSku());
                }
                if (!TextUtils.isEmpty(variant.getImageUrl())) {
                    variantData.put("image", variant.getImageUrl());
                }
                variantMaps.add(variantData);
            }
        }
        data.put("variants", variantMaps);
        if (!variantMaps.isEmpty()) {
            int totalStock = 0;
            for (Map<String, Object> variantData : variantMaps) {
                Object stockVal = variantData.get("stock");
                if (stockVal instanceof Number) {
                    totalStock += ((Number) stockVal).intValue();
                }
            }
            data.put("stock", totalStock);
            data.put("stockCount", totalStock);
        }

        data.put("updatedAt", Timestamp.now());
        if (isNew) {
            data.put("createdAt", Timestamp.now());
            data.put("sold", 0);
            data.put("reviewCount", 0);
            data.put("isNew", true);
            data.put("adminCreated", true);
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

    public void setProductHidden(@NonNull String productId, boolean hidden, @NonNull SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("hidden", hidden);
        updates.put("updatedAt", Timestamp.now());
        db.collection("products").document(productId).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadCustomerLookup(@NonNull CustomerLookupCallback callback) {
        db.collection("users").get()
                .addOnSuccessListener(snap -> {
                    Map<String, AdminCustomer> lookup = new HashMap<>();
                    for (DocumentSnapshot doc : snap) {
                        AdminCustomer customer = mapCustomer(doc);
                        if (customer != null) {
                            lookup.put(customer.uid, customer);
                        }
                    }
                    callback.onSuccess(lookup);
                })
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
                Timestamp createdAt = doc.getTimestamp("createdAt");
                if (createdAt != null) {
                    order.setCreatedAt(createdAt.toDate());
                }
            }
            if (order.getUpdatedAt() == null) {
                Timestamp updatedAt = doc.getTimestamp("updatedAt");
                if (updatedAt != null) {
                    order.setUpdatedAt(updatedAt.toDate());
                }
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
            if (order.getDeliveredAt() == null) {
                Timestamp deliveredAt = doc.getTimestamp("deliveredAt");
                if (deliveredAt != null) {
                    order.setDeliveredAt(deliveredAt.toDate());
                }
            }
            Boolean cancelRequested = doc.getBoolean("cancelRequested");
            if (cancelRequested != null) {
                order.setCancelRequested(cancelRequested);
            }
            if (order.getCancelRequestedAt() == null) {
                Timestamp cancelRequestedAt = doc.getTimestamp("cancelRequestedAt");
                if (cancelRequestedAt != null) {
                    order.setCancelRequestedAt(cancelRequestedAt.toDate());
                }
            }
            return order;
        } catch (Exception e) {
            Log.e(TAG, "Skip invalid order document: " + doc.getId(), e);
            return null;
        }
    }

    private static long getOrderSortTime(@NonNull Order order) {
        if (order.getUpdatedAt() != null) {
            return order.getUpdatedAt().getTime();
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt().getTime();
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

        // Fetch order first to get userId and total amount for loyalty update
        db.collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onError("Đơn hàng không tồn tại");
                return;
            }

            String userId = doc.getString("userId");
            Double total = doc.getDouble("totalPrice");
            if (total == null) total = 0.0;

            WriteBatch batch = db.batch();

            // 1. Cập nhật đơn hàng
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", toStatus);
            updates.put("updatedAt", Timestamp.now());
            if ("delivered".equals(toStatus)) {
                updates.put("deliveredAt", Timestamp.now());
            }
            batch.update(db.collection("orders").document(orderId), updates);

            // 2. Cập nhật lịch sử
            Map<String, Object> history = new HashMap<>();
            history.put("fromStatus", fromStatus != null ? fromStatus : "");
            history.put("toStatus", toStatus);
            history.put("adminUid", user.getUid());
            history.put("adminEmail", user.getEmail() != null ? user.getEmail() : "");
            history.put("createdAt", Timestamp.now());
            batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

            // 3. Xử lý hạng thành viên (Loyalty)
            // Logic: Tăng khi sang Delivered, Giảm khi sang Returned/Cancelled (nếu từ Delivered)
            if (userId != null) {
                if ("delivered".equals(toStatus) && !"delivered".equals(fromStatus)) {
                    // Chuyển sang Giao thành công -> Tăng tích lũy
                    batch.update(db.collection("users").document(userId),
                            "spentAmount", com.google.firebase.firestore.FieldValue.increment(total));
                } else if (("returned".equals(toStatus) || "cancelled".equals(toStatus)) && "delivered".equals(fromStatus)) {
                    // Nếu đã từng Delivered mà giờ bị Trả hoặc Hủy -> Giảm tích lũy
                    batch.update(db.collection("users").document(userId),
                            "spentAmount", com.google.firebase.firestore.FieldValue.increment(-total));
                }
            }

            batch.commit()
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(errorMessage(e)));

        }).addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void approveCancelRequest(@NonNull String orderId,
                                     @Nullable String userId,
                                     double amount,
                                     @NonNull SimpleCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Order.STATUS_CANCELLED);
        updates.put("cancelRequested", false);
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(orderId), updates);


        Map<String, Object> history = new HashMap<>();
        history.put("fromStatus", Order.STATUS_PENDING);
        history.put("toStatus", Order.STATUS_CANCELLED);
        history.put("adminUid", user.getUid());
        history.put("adminEmail", user.getEmail() != null ? user.getEmail() : "");
        history.put("createdAt", Timestamp.now());
        history.put("note", "Duyệt yêu cầu hủy");
        batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void rejectCancelRequest(@NonNull String orderId, @NonNull SimpleCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("cancelRequested", false);
        updates.put("cancelReason", com.google.firebase.firestore.FieldValue.delete());
        updates.put("cancelRequestedAt", com.google.firebase.firestore.FieldValue.delete());
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(orderId), updates);

        Map<String, Object> history = new HashMap<>();
        history.put("fromStatus", Order.STATUS_PENDING);
        history.put("toStatus", Order.STATUS_PENDING);
        history.put("adminUid", user.getUid());
        history.put("adminEmail", user.getEmail() != null ? user.getEmail() : "");
        history.put("createdAt", Timestamp.now());
        history.put("note", "Từ chối yêu cầu hủy");
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
                        AdminCustomer customer = mapCustomer(doc);
                        if (customer == null) {
                            continue;
                        }
                        if (customer.role != null && "admin".equals(customer.role.toLowerCase(Locale.ROOT))) {
                            continue;
                        }
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

    public void setCustomerDisabled(@NonNull String uid,
                                    boolean disabled,
                                    @Nullable String reason,
                                    @NonNull SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("disabled", disabled);
        updates.put("updatedAt", Timestamp.now());
        if (disabled) {
            updates.put("disabledReason", reason != null ? reason : "");
            updates.put("disabledAt", Timestamp.now());
            updates.put("unlockedReason", com.google.firebase.firestore.FieldValue.delete());
            updates.put("unlockedAt", com.google.firebase.firestore.FieldValue.delete());
        } else {
            updates.put("disabledReason", com.google.firebase.firestore.FieldValue.delete());
            updates.put("disabledAt", com.google.firebase.firestore.FieldValue.delete());
            if (!TextUtils.isEmpty(reason)) {
                updates.put("unlockedReason", reason);
                updates.put("unlockedAt", Timestamp.now());
            }
        }

        WriteBatch batch = db.batch();
        batch.update(db.collection("users").document(uid), updates);

        if (disabled && !TextUtils.isEmpty(reason)) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", "Tài khoản bị khóa");
            notification.put("body", reason);
            notification.put("message", reason);
            notification.put("type", "ACCOUNT_LOCKED");
            notification.put("read", false);
            notification.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(uid).collection("notifications").document(), notification);
        } else if (!disabled && !TextUtils.isEmpty(reason)) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", "Tài khoản đã được mở khóa");
            notification.put("body", reason);
            notification.put("message", reason);
            notification.put("type", "ACCOUNT_UNLOCKED");
            notification.put("read", false);
            notification.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(uid).collection("notifications").document(), notification);
        }

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadCustomerOrders(@NonNull String uid, @NonNull OrdersCallback callback) {
        db.collection("orders").get()
                .addOnSuccessListener(snap -> {
                    List<Order> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Order order = parseOrderDocument(doc);
                        if (order == null) continue;
                        String buyerId = doc.getString("buyerId");
                        String userId = doc.getString("userId");
                        if (uid.equals(buyerId) || uid.equals(userId) || uid.equals(order.getUserId())) {
                            list.add(order);
                        }
                    }
                    list.sort((a, b) -> Long.compare(getOrderSortTime(b), getOrderSortTime(a)));
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void loadCustomerOrderStats(@NonNull String uid, @NonNull CustomerOrderStatsCallback callback) {
        db.collection("orders").get()
                .addOnSuccessListener(snap -> {
                    int total = 0;
                    int pending = 0;
                    for (DocumentSnapshot doc : snap) {
                        String buyerId = doc.getString("buyerId");
                        String userId = doc.getString("userId");
                        if (!uid.equals(buyerId) && !uid.equals(userId)) {
                            continue;
                        }
                        total++;
                        if ("pending".equals(doc.getString("status"))) {
                            pending++;
                        }
                    }
                    callback.onSuccess(total, pending);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    @Nullable
    private static AdminCustomer mapCustomer(@NonNull DocumentSnapshot doc) {
        String role = doc.getString("role");
        if (role == null) {
            role = doc.getString("userRole");
        }
        AdminCustomer customer = new AdminCustomer();
        customer.uid = doc.getId();
        customer.fullName = firstNonEmpty(doc.getString("fullName"), doc.getString("name"), doc.getString("displayName"));
        customer.username = doc.getString("username");
        customer.phone = doc.getString("phone");
        customer.email = firstNonEmpty(doc.getString("displayEmail"), doc.getString("email"));
        customer.role = role != null ? role : "buyer";
        Boolean disabled = doc.getBoolean("disabled");
        customer.disabled = disabled != null && disabled;
        customer.disabledReason = doc.getString("disabledReason");
        Long spent = doc.getLong("spentAmount");
        customer.spentAmount = spent != null ? spent : 0;
        return customer;
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
