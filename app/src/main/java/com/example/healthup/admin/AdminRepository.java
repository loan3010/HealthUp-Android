package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.DeliveryFailure;
import com.example.models.Order;
import com.example.models.Product;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.text.TextUtils;
import android.util.Log;
import com.example.healthup.util.StockManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
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
        public int cancelledOrders;
        public int lowStockProducts;
        public double revenue;
    }

    public static class DashboardData {
        public int productCount;
        public int lowStockProducts;
        public int pendingOrders;
        public int cancelledOrders;
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
        public String event;
        public String fromStatus;
        public String toStatus;
        public String note;
        public String reason;
        public int attempt;
        public String adminUid;
        public String adminEmail;
        public String actorRole;
        public Timestamp createdAt;
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @SuppressWarnings("unused")
    public void loadDashboard(@NonNull DashboardCallback callback) {
        loadDashboardData(new DashboardDataCallback() {
            @Override
            public void onSuccess(@NonNull DashboardData data) {
                DashboardStats stats = new DashboardStats();
                stats.productCount = data.productCount;
                stats.orderCount = data.orders.size();
                stats.pendingOrders = data.pendingOrders;
                stats.cancelledOrders = data.cancelledOrders;
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
                                        data.pendingOrders++;
                                    }
                                    if (Order.STATUS_CANCELLED.equalsIgnoreCase(status)) {
                                        data.cancelledOrders++;
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
        if (!TextUtils.isEmpty(product.getProductCode())) {
            data.put("productCode", product.getProductCode());
        }
        data.put("price", product.getPrice());
        data.put("originalPrice", product.getOriginalPrice() > 0 ? product.getOriginalPrice() : product.getPrice());
        data.put("oldPrice", product.getOriginalPrice() > 0 ? product.getOriginalPrice() : product.getPrice());
        data.put("stock", product.getStock());
        data.put("stockCount", product.getStock());
        data.put("cat", product.getCategory());
        data.put("shortDesc", product.getShortDesc());
        data.put("description", product.getDescription());
        data.put("ingredients", product.getIngredients() != null ? product.getIngredients() : "");
        data.put("usage", product.getUsage() != null ? product.getUsage() : "");
        data.put("origin", product.getOrigin() != null ? product.getOrigin() : "");
        data.put("nutritionText", product.getNutritionText() != null ? product.getNutritionText() : "");
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            data.put("images", product.getImages());
        } else {
            data.put("images", new ArrayList<String>());
        }
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
        if (!variantMaps.isEmpty()) {
            data.put("variants", variantMaps);
            data.put("hasVariants", true);
            int totalStock = 0;
            for (Map<String, Object> variantData : variantMaps) {
                Object stockVal = variantData.get("stock");
                if (stockVal instanceof Number) {
                    totalStock += ((Number) stockVal).intValue();
                }
            }
            data.put("stock", totalStock);
            data.put("stockCount", totalStock);
            if (product.getFlavors() != null) {
                data.put("flavors", product.getFlavors());
            }
            if (product.getWeights() != null) {
                data.put("weights", product.getWeights());
            }
        } else if (isNew) {
            data.put("variants", variantMaps);
            data.put("hasVariants", product.isHasVariants());
            data.put("flavors", new ArrayList<>());
            data.put("weights", new ArrayList<>());
        } else {
            data.put("variants", new ArrayList<>());
            data.put("hasVariants", false);
            data.put("flavors", new ArrayList<>());
            data.put("weights", new ArrayList<>());
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
            db.collection("products").document(product.getId()).set(data, SetOptions.merge())
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(errorMessage(e)));
        }
    }

    public void deleteProduct(@NonNull String productId, @NonNull SimpleCallback callback) {
        db.collection("products").document(productId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    /** Gán mã HEALTHUP-xxxx cho mọi sản phẩm chưa có productCode. */
    public void backfillMissingProductCodes(@NonNull SimpleCallback callback) {
        db.collection("products").get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> missing = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String code = doc.getString("productCode");
                        if (code == null || code.trim().isEmpty()) {
                            missing.add(doc);
                        }
                    }
                    if (missing.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }
                    backfillProductCodeAt(missing, 0, callback);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    private void backfillProductCodeAt(@NonNull List<DocumentSnapshot> docs,
                                       int index,
                                       @NonNull SimpleCallback callback) {
        if (index >= docs.size()) {
            callback.onSuccess();
            return;
        }
        DocumentSnapshot doc = docs.get(index);
        AdminProductCodeHelper.assignNextCode(db)
                .addOnSuccessListener(code -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("productCode", code);
                    db.collection("products").document(doc.getId())
                            .set(update, SetOptions.merge())
                            .addOnSuccessListener(unused -> backfillProductCodeAt(docs, index + 1, callback))
                            .addOnFailureListener(e -> callback.onError(errorMessage(e)));
                })
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
                    list.sort(Comparator.comparingLong(AdminRepository::getOrderSortTime).reversed());
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    @Nullable
    public static Order parseOrderDocument(@NonNull DocumentSnapshot doc) {
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
            if (TextUtils.isEmpty(order.getCancelReason())) {
                String cancelReason = doc.getString("cancelReason");
                if (cancelReason != null) {
                    order.setCancelReason(cancelReason);
                }
            }
            if (TextUtils.isEmpty(order.getCancelSource())) {
                String cancelSource = doc.getString("cancelSource");
                if (cancelSource != null) {
                    order.setCancelSource(cancelSource);
                }
            }
            if (order.getCancelRequestedAt() == null) {
                Timestamp cancelRequestedAt = doc.getTimestamp("cancelRequestedAt");
                if (cancelRequestedAt != null) {
                    order.setCancelRequestedAt(cancelRequestedAt.toDate());
                }
            }
            if (order.getCancelledAt() == null) {
                Timestamp cancelledAt = doc.getTimestamp("cancelledAt");
                if (cancelledAt != null) {
                    order.setCancelledAt(cancelledAt.toDate());
                }
            }
            Boolean shopConfirmed = doc.getBoolean("shopConfirmedDelivery");
            if (shopConfirmed != null) {
                order.setShopConfirmedDelivery(shopConfirmed);
            }
            if (order.getShopConfirmedAt() == null) {
                Timestamp shopConfirmedAt = doc.getTimestamp("shopConfirmedAt");
                if (shopConfirmedAt != null) {
                    order.setShopConfirmedAt(shopConfirmedAt.toDate());
                }
            }
            Long attempts = doc.getLong("deliveryAttempts");
            if (attempts != null) {
                order.setDeliveryAttempts(attempts.intValue());
            }
            Boolean needsRedelivery = doc.getBoolean("needsRedelivery");
            if (needsRedelivery != null) {
                order.setNeedsRedelivery(needsRedelivery);
            }
            Object failuresRaw = doc.get("deliveryFailures");
            if (failuresRaw instanceof List) {
                List<DeliveryFailure> failures = new ArrayList<>();
                for (Object item : (List<?>) failuresRaw) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        DeliveryFailure failure = DeliveryFailure.fromMap((Map<String, Object>) item);
                        if (failure != null) failures.add(failure);
                    }
                }
                order.setDeliveryFailures(failures);
            }
            if (TextUtils.isEmpty(order.getReturnStatus())) {
                String returnStatus = doc.getString("returnStatus");
                if (!TextUtils.isEmpty(returnStatus)) {
                    order.setReturnStatus(returnStatus);
                } else if (order.getReturnHandling() != null || Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
                    if (Order.STATUS_COMPLETED.equalsIgnoreCase(order.getStatus())) {
                        order.setReturnStatus(Order.RETURN_COMPLETED);
                    } else {
                        order.setReturnStatus(Order.RETURN_REQUESTED);
                    }
                } else {
                    order.setReturnStatus(Order.RETURN_NONE);
                }
            }
            if (order.getReturnRejectReason() == null) {
                order.setReturnRejectReason(doc.getString("returnRejectReason"));
            }
            Object returnItemsRaw = doc.get("returnItems");
            if (returnItemsRaw instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> returnItems = (List<Map<String, Object>>) returnItemsRaw;
                order.setReturnItems(returnItems);
            }
            Boolean stockDeducted = doc.getBoolean("stockDeducted");
            if (stockDeducted != null) {
                order.setStockDeducted(stockDeducted);
            }
            Boolean stockRestored = doc.getBoolean("stockRestored");
            if (stockRestored != null) {
                order.setStockRestored(stockRestored);
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

    @SuppressWarnings("unused")
    public void updateOrderStatus(@NonNull String orderId,
                                  @Nullable String fromStatus,
                                  @NonNull String toStatus,
                                  @NonNull SimpleCallback callback) {
        advanceOrderLifecycle(orderId, fromStatus, toStatus, null, null, callback);
    }

    /** pending → confirmed */
    public void confirmOrder(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            callback.onError("Chỉ xác nhận đơn đang chờ xác nhận");
            return;
        }
        advanceOrderLifecycle(order.getId(), order.getStatus(), Order.STATUS_CONFIRMED,
                "order_confirmed", "Admin đã xác nhận đơn hàng", callback);
    }

    /** Duyệt yêu cầu hủy đơn hàng (pending -> cancelled) */
    public void approveCancelRequest(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("cancelRequested", false);
        advanceOrderLifecycle(order.getId(), order.getStatus(), Order.STATUS_CANCELLED,
                "cancel_approved", "Duyệt yêu cầu hủy đơn hàng", extra, callback);
    }

    /** confirmed → shipping */
    public void startShipping(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.STATUS_CONFIRMED.equals(order.getStatus())) {
            callback.onError("Chỉ chuyển giao khi đơn đã xác nhận");
            return;
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("needsRedelivery", false);
        extra.put("shopConfirmedDelivery", false);
        advanceOrderLifecycle(order.getId(), order.getStatus(), Order.STATUS_SHIPPING,
                "shipping_started", "Đơn đã bắt đầu giao hàng", extra, callback);
    }

    /** shipping → shopConfirmedDelivery=true (status stays shipping) */
    public void confirmShopDelivery(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.STATUS_SHIPPING.equals(order.getStatus())) {
            callback.onError("Chỉ xác nhận giao khi đơn đang giao");
            return;
        }
        if (order.isNeedsRedelivery()) {
            callback.onError("Vui lòng bấm Giao lại trước khi xác nhận giao thành công");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("shopConfirmedDelivery", true);
        updates.put("shopConfirmedAt", Timestamp.now());
        updates.put("needsRedelivery", false);
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user, "shop_confirmed_delivery",
                order.getStatus(), order.getStatus(),
                "Shop xác nhận đã giao hàng thành công", null, order.getDeliveryAttempts());
        notifyBuyerAndAdmin(batch, order, "ORDER_DELIVERY_CONFIRMED",
                "Đơn hàng đã giao tới",
                "Shop xác nhận đơn #" + displayCode(order) + " đã giao thành công. Vui lòng bấm Đã nhận được hàng.",
                "Shop đã giao đơn #" + displayCode(order));

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    /**
     * Record a failed delivery attempt.
     * Refused → cancelled. 3rd failure → cancelled. Else stay shipping + needsRedelivery.
     */
    public void recordDeliveryFailure(@NonNull Order order,
                                      @NonNull String reason,
                                      @Nullable String note,
                                      @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.STATUS_SHIPPING.equals(order.getStatus())) {
            callback.onError("Chỉ ghi nhận thất bại khi đơn đang giao");
            return;
        }
        if (order.isNeedsRedelivery()) {
            callback.onError("Vui lòng bấm Giao lại trước khi ghi nhận lần giao tiếp theo");
            return;
        }
        if (order.isShopConfirmedDelivery()) {
            callback.onError("Đơn đã được shop xác nhận giao thành công");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        int nextAttempt = order.getDeliveryAttempts() + 1;
        boolean refused = Order.FAIL_REFUSED.equals(reason);
        boolean maxReached = nextAttempt >= Order.MAX_DELIVERY_ATTEMPTS;
        boolean cancel = refused || maxReached;

        DeliveryFailure failure = new DeliveryFailure(
                nextAttempt, reason, note, new Date(),
                user.getUid(), user.getEmail() != null ? user.getEmail() : "");

        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryAttempts", nextAttempt);
        updates.put("deliveryFailures", FieldValue.arrayUnion(failure.toMap()));
        updates.put("updatedAt", Timestamp.now());
        updates.put("shopConfirmedDelivery", false);

        String toStatus = order.getStatus();
        String event;
        String historyNote;
        String buyerTitle;
        String buyerBody;
        String adminBody;

        if (cancel) {
            toStatus = Order.STATUS_CANCELLED;
            updates.put("status", Order.STATUS_CANCELLED);
            if (refused) {
                updates.put("cancelReason", Order.CANCEL_REASON_REFUSED);
                updates.put("cancelSource", Order.CANCEL_SOURCE_DELIVERY_REFUSED);
            } else {
                updates.put("cancelReason", Order.CANCEL_REASON_MAX_ATTEMPTS);
                updates.put("cancelSource", Order.CANCEL_SOURCE_DELIVERY_MAX);
            }
            updates.put("cancelledAt", Timestamp.now());
            updates.put("needsRedelivery", false);
            event = "delivery_failed_cancelled";
            historyNote = refused
                    ? "Hủy do khách từ chối nhận (lần " + nextAttempt + ")"
                    : "Hủy sau " + nextAttempt + " lần giao thất bại";
            buyerTitle = "Đơn hàng đã hủy";
            buyerBody = "Đơn #" + displayCode(order) + " đã bị hủy. Lý do: "
                    + (refused ? Order.CANCEL_REASON_REFUSED : Order.CANCEL_REASON_MAX_ATTEMPTS);
            adminBody = "Đơn #" + displayCode(order) + " hủy sau giao thất bại: " + reason;
        } else {
            updates.put("needsRedelivery", true);
            event = "delivery_failed";
            historyNote = "Giao thất bại lần " + nextAttempt + ": " + reason
                    + (TextUtils.isEmpty(note) ? "" : (" — " + note));
            buyerTitle = "Giao hàng không thành công";
            buyerBody = "Đơn #" + displayCode(order) + " giao lần " + nextAttempt
                    + " không thành công. Lý do: " + reason
                    + ". Shop sẽ giao lại lần " + (nextAttempt + 1) + ".";
            adminBody = "Đơn #" + displayCode(order) + " giao thất bại lần " + nextAttempt + ": " + reason;
        }

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user, event, order.getStatus(), toStatus,
                historyNote, reason, nextAttempt);
        notifyBuyerAndAdmin(batch, order, cancel ? "ORDER_CANCELLED" : "ORDER_DELIVERY_FAILED",
                buyerTitle, buyerBody, adminBody);

        final boolean restoreStock = cancel && shouldRestoreStock(order, Order.STATUS_CANCELLED);
        Runnable commitBatch = () -> {
            if (restoreStock) {
                batch.update(db.collection("orders").document(order.getId()), "stockRestored", true);
            }
            batch.commit()
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError(errorMessage(e)));
        };

        if (restoreStock) {
            StockManager.restoreStock(db, order.getItems(), new StockManager.StockCallback() {
                @Override
                public void onSuccess() {
                    commitBatch.run();
                }

                @Override
                public void onInsufficientStock(@NonNull String productName, int available) {
                    callback.onError("Không thể hoàn kho cho " + productName);
                }

                @Override
                public void onError(@NonNull String message) {
                    callback.onError(message);
                }
            });
        } else {
            commitBatch.run();
        }
    }

    /** After a failure: clear needsRedelivery and log "Giao lại lần N". */
    public void scheduleRedelivery(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.STATUS_SHIPPING.equals(order.getStatus()) || !order.isNeedsRedelivery()) {
            callback.onError("Không có lần giao lại đang chờ");
            return;
        }
        int nextAttempt = order.getDeliveryAttempts() + 1;
        if (nextAttempt > Order.MAX_DELIVERY_ATTEMPTS) {
            callback.onError("Đã hết số lần giao cho phép");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("needsRedelivery", false);
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user, "redelivery_scheduled",
                order.getStatus(), order.getStatus(),
                "Giao lại lần " + nextAttempt, null, nextAttempt);
        notifyBuyerAndAdmin(batch, order, "ORDER_REDELIVERY",
                "Đơn hàng sẽ được giao lại",
                "Shop sẽ giao lại đơn #" + displayCode(order) + " (lần " + nextAttempt + ").",
                "Đã lên lịch giao lại lần " + nextAttempt + " cho đơn #" + displayCode(order));

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void approveReturn(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.RETURN_REQUESTED.equals(order.getReturnStatus())
                && !Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
            callback.onError("Không có yêu cầu trả hàng đang chờ");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        // Approve only — client timeline shows "HealthUp đã duyệt"; further steps via advanceReturnProgress.
        Map<String, Object> updates = new HashMap<>();
        updates.put("returnStatus", Order.RETURN_APPROVED);
        updates.put("returnStep", 1);
        updates.put("status", Order.STATUS_DELIVERED);
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user, "return_approved",
                order.getStatus(), Order.STATUS_DELIVERED,
                "Admin đã duyệt yêu cầu trả hàng", null, 0);
        notifyBuyerAndAdmin(batch, order, "ORDER_RETURN_APPROVED",
                "Yêu cầu trả hàng được duyệt",
                "Yêu cầu trả hàng đơn #" + displayCode(order) + " đã được duyệt. Shop đang xử lý.",
                "Đã duyệt trả hàng đơn #" + displayCode(order));

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    /**
     * Advances return/refund/reship progress by one step to match client "Tiến trình xử lý".
     * Final step marks return completed + refunded/reshipped as appropriate.
     */
    public void advanceReturnProgress(@NonNull Order order, @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.RETURN_APPROVED.equals(order.getReturnStatus())) {
            callback.onError("Cần duyệt yêu cầu trước khi cập nhật tiến trình");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        int max = com.example.healthup.util.ReturnProgressHelper.maxStep(order);
        int current = Math.max(order.getReturnStep(), 1);
        if (current >= max) {
            callback.onError("Yêu cầu đã hoàn tất");
            return;
        }
        int next = current + 1;
        boolean isFinal = next >= max;
        String handling = com.example.healthup.util.ReturnProgressHelper.normalizeHandling(order.getReturnHandling());
        String stepTitle = com.example.healthup.util.ReturnProgressHelper.stepTitle(handling, next);

        Map<String, Object> updates = new HashMap<>();
        updates.put("returnStep", next);
        updates.put("updatedAt", Timestamp.now());
        if (isFinal) {
            updates.put("returnStatus", Order.RETURN_COMPLETED);
            updates.put("status", Order.STATUS_COMPLETED);
            if (com.example.healthup.util.ReturnProgressHelper.isReship(handling)) {
                updates.put("paymentStatus", "reshipped");
            } else {
                updates.put("paymentStatus", "refunded");
            }
        }

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user,
                isFinal ? "return_completed" : "return_progress",
                order.getStatus(),
                isFinal ? Order.STATUS_COMPLETED : order.getStatus(),
                stepTitle, null, 0);
        if (isFinal) {
            notifyBuyerAndAdmin(batch, order, "ORDER_RETURN_APPROVED",
                    "Yêu cầu trả hàng hoàn tất",
                    "Yêu cầu trả hàng đơn #" + displayCode(order) + " đã hoàn tất: " + stepTitle,
                    "Hoàn tất trả hàng đơn #" + displayCode(order));
        }

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    public void rejectReturn(@NonNull Order order,
                             @NonNull String rejectReason,
                             @NonNull SimpleCallback callback) {
        if (TextUtils.isEmpty(order.getId())) {
            callback.onError("Thiếu đơn hàng");
            return;
        }
        if (!Order.RETURN_REQUESTED.equals(order.getReturnStatus())
                && !Order.RETURN_APPROVED.equals(order.getReturnStatus())
                && !Order.STATUS_RETURNED.equalsIgnoreCase(order.getStatus())) {
            callback.onError("Không có yêu cầu trả hàng đang chờ");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Order.STATUS_DELIVERED);
        updates.put("returnStatus", Order.RETURN_REJECTED);
        updates.put("returnRejectReason", rejectReason);
        // Clear progress so client never shows "Hoàn tiền thành công" after reject.
        updates.put("returnStep", 0);
        updates.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.update(db.collection("orders").document(order.getId()), updates);
        appendHistory(batch, order.getId(), user, "return_rejected",
                order.getStatus(), Order.STATUS_DELIVERED,
                "Từ chối trả hàng: " + rejectReason, rejectReason, 0);
        notifyBuyerAndAdmin(batch, order, "ORDER_RETURN_REJECTED",
                "Yêu cầu trả hàng bị từ chối",
                "Yêu cầu trả hàng đơn #" + displayCode(order) + " không được duyệt. Lý do: " + rejectReason,
                "Từ chối trả hàng đơn #" + displayCode(order) + ": " + rejectReason);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    private void advanceOrderLifecycle(@NonNull String orderId,
                                       @Nullable String fromStatus,
                                       @NonNull String toStatus,
                                       @Nullable String event,
                                       @Nullable String note,
                                       @NonNull SimpleCallback callback) {
        advanceOrderLifecycle(orderId, fromStatus, toStatus, event, note, null, callback);
    }

    private void advanceOrderLifecycle(@NonNull String orderId,
                                       @Nullable String fromStatus,
                                       @NonNull String toStatus,
                                       @Nullable String event,
                                       @Nullable String note,
                                       @Nullable Map<String, Object> extraUpdates,
                                       @NonNull SimpleCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Chưa đăng nhập");
            return;
        }

        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    Order order = parseOrderDocument(doc);
                    if (order == null) {
                        callback.onError("Không tìm thấy đơn hàng");
                        return;
                    }

                    WriteBatch batch = db.batch();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", toStatus);
                    updates.put("updatedAt", Timestamp.now());
                    if (Order.STATUS_DELIVERED.equals(toStatus)) {
                        updates.put("deliveredAt", Timestamp.now());
                    }
                    if (extraUpdates != null) {
                        updates.putAll(extraUpdates);
                    }
                    batch.update(db.collection("orders").document(orderId), updates);

                    // Xử lý hạng thành viên (Loyalty)
                    String buyerId = order.getUserId();
                    double total = order.getTotalPrice();
                    String currentStatus = order.getStatus();

                    if (!TextUtils.isEmpty(buyerId)) {
                        if (Order.STATUS_DELIVERED.equalsIgnoreCase(toStatus) && !Order.STATUS_DELIVERED.equalsIgnoreCase(currentStatus)) {
                            // Chuyển sang Giao thành công -> Tăng tích lũy
                            batch.update(db.collection("users").document(buyerId),
                                    "spentAmount", FieldValue.increment(total));
                        } else if ((Order.STATUS_RETURNED.equalsIgnoreCase(toStatus) || Order.STATUS_CANCELLED.equalsIgnoreCase(toStatus))
                                && Order.STATUS_DELIVERED.equalsIgnoreCase(currentStatus)) {
                            // Nếu đã từng Delivered mà giờ bị Trả hoặc Hủy -> Giảm tích lũy
                            batch.update(db.collection("users").document(buyerId),
                                    "spentAmount", FieldValue.increment(-total));
                        }
                    }

                    String resolvedEvent = event != null ? event : "status_changed";
                    String buyerTitle;
                    String buyerBody;
                    String notifType;
                    switch (toStatus) {
                        case Order.STATUS_CONFIRMED:
                            buyerTitle = "Đơn hàng đã được xác nhận";
                            buyerBody = "Đơn #" + displayCode(order) + " đã được xác nhận. Shop đang chuẩn bị hàng.";
                            notifType = "ORDER_CONFIRMED";
                            break;
                        case Order.STATUS_SHIPPING:
                            buyerTitle = "Đơn hàng đang giao";
                            buyerBody = "Đơn #" + displayCode(order) + " đã bắt đầu giao hàng.";
                            notifType = "ORDER_SHIPPING";
                            break;
                        default:
                            buyerTitle = "Cập nhật đơn hàng";
                            buyerBody = "Đơn #" + displayCode(order) + " đã cập nhật trạng thái.";
                            notifType = "ORDER_UPDATE";
                            break;
                    }

                    appendHistory(batch, orderId, user, resolvedEvent,
                            fromStatus != null ? fromStatus : order.getStatus(), toStatus,
                            note, null, order.getDeliveryAttempts());
                    notifyBuyerAndAdmin(batch, order, notifType, buyerTitle, buyerBody,
                            buyerTitle + " — #" + displayCode(order));

                    final boolean restoreStock = shouldRestoreStock(order, toStatus);
                    Runnable commitBatch = () -> {
                        if (restoreStock) {
                            batch.update(db.collection("orders").document(orderId), "stockRestored", true);
                        }
                        batch.commit()
                                .addOnSuccessListener(unused -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
                    };

                    if (restoreStock) {
                        StockManager.restoreStock(db, order.getItems(), new StockManager.StockCallback() {
                            @Override
                            public void onSuccess() {
                                commitBatch.run();
                            }

                            @Override
                            public void onInsufficientStock(@NonNull String productName, int available) {
                                callback.onError("Không thể hoàn kho cho " + productName);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                callback.onError(message);
                            }
                        });
                    } else {
                        commitBatch.run();
                    }
                })
                .addOnFailureListener(e -> callback.onError(errorMessage(e)));
    }

    private boolean shouldRestoreStock(@NonNull Order order, @NonNull String toStatus) {
        return Order.STATUS_CANCELLED.equalsIgnoreCase(toStatus)
                && !Order.STATUS_DELIVERED.equalsIgnoreCase(order.getStatus())
                && order.isStockDeducted()
                && !order.isStockRestored()
                && order.getItems() != null
                && !order.getItems().isEmpty();
    }

    private void appendHistory(@NonNull WriteBatch batch,
                               @NonNull String orderId,
                               @NonNull FirebaseUser user,
                               @NonNull String event,
                               @Nullable String fromStatus,
                               @Nullable String toStatus,
                               @Nullable String note,
                               @Nullable String reason,
                               int attempt) {
        Map<String, Object> history = new HashMap<>();
        history.put("event", event);
        history.put("fromStatus", fromStatus != null ? fromStatus : "");
        history.put("toStatus", toStatus != null ? toStatus : "");
        history.put("note", note != null ? note : "");
        history.put("reason", reason != null ? reason : "");
        history.put("attempt", attempt);
        history.put("adminUid", user.getUid());
        history.put("adminEmail", user.getEmail() != null ? user.getEmail() : "");
        history.put("actorRole", "admin");
        history.put("createdAt", Timestamp.now());
        batch.set(db.collection("orders").document(orderId).collection("history").document(), history);
    }

    private void notifyBuyerAndAdmin(@NonNull WriteBatch batch,
                                     @NonNull Order order,
                                     @NonNull String type,
                                     @NonNull String buyerTitle,
                                     @NonNull String buyerBody,
                                     @NonNull String adminBody) {
        String buyerId = order.getUserId();
        String code = displayCode(order);
        boolean isReturn = type.toUpperCase(Locale.US).contains("RETURN");
        if (!TextUtils.isEmpty(buyerId)) {
            Map<String, Object> buyerNotif = new HashMap<>();
            buyerNotif.put("type", type);
            buyerNotif.put("title", buyerTitle);
            buyerNotif.put("body", buyerBody);
            buyerNotif.put("message", buyerBody);
            buyerNotif.put("refId", order.getId());
            buyerNotif.put("orderId", order.getId());
            if (isReturn) {
                buyerNotif.put("returnId", order.getId());
                buyerNotif.put("returnRequestId", order.getId());
            }
            buyerNotif.put("orderCode", code);
            buyerNotif.put("read", false);
            buyerNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(buyerId).collection("notifications").document(), buyerNotif);
        }

        Map<String, Object> adminNotif = new HashMap<>();
        adminNotif.put("type", type);
        adminNotif.put("title", buyerTitle);
        adminNotif.put("body", adminBody);
        adminNotif.put("message", adminBody);
        adminNotif.put("orderId", order.getId());
        adminNotif.put("refId", order.getId());
        if (isReturn) {
            adminNotif.put("returnId", order.getId());
            adminNotif.put("returnRequestId", order.getId());
        }
        adminNotif.put("orderCode", code);
        adminNotif.put("buyerId", buyerId != null ? buyerId : "");
        adminNotif.put("read", false);
        adminNotif.put("createdAt", Timestamp.now());
        batch.set(db.collection("admin_notifications").document(), adminNotif);
    }

    @NonNull
    private static String displayCode(@NonNull Order order) {
        return !TextUtils.isEmpty(order.getOrderCode()) ? order.getOrderCode() : order.getId();
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
                        entry.event = doc.getString("event");
                        entry.fromStatus = doc.getString("fromStatus");
                        entry.toStatus = doc.getString("toStatus");
                        entry.note = doc.getString("note");
                        entry.reason = doc.getString("reason");
                        Long attempt = doc.getLong("attempt");
                        entry.attempt = attempt != null ? attempt.intValue() : 0;
                        entry.adminUid = doc.getString("adminUid");
                        entry.adminEmail = doc.getString("adminEmail");
                        entry.actorRole = doc.getString("actorRole");
                        entry.createdAt = doc.getTimestamp("createdAt");
                        list.add(entry);
                    }
                    list.sort(Comparator.comparingLong(AdminRepository::getHistorySortTime));
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
                    list.sort(Comparator.comparingLong(AdminRepository::getOrderSortTime).reversed());
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

    @NonNull
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
