package com.example.healthup;

import android.net.Uri;
import android.util.Log;
import com.example.healthup.admin.AdminRepository;
import com.example.healthup.util.StockManager;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.Product;
import com.example.models.Review;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final FirebaseStorage storage;

    private FirebaseManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public String getCurrentUserId() {
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return null;
    }

    // --- PRODUCTS ---
    public Task<QuerySnapshot> getProducts() {
        return db.collection("products").get();
    }

    // --- VOUCHERS ---
    public Task<QuerySnapshot> getVouchers() {
        return db.collection("promoCodes").get();
    }

    public void seedVouchersIfEmpty() {
        db.collection("promoCodes").limit(1).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                // Chỉ seed nếu bộ sưu tập promoCodes hoàn toàn trống
                List<Map<String, Object>> list = new ArrayList<>();
                
                Map<String, Object> v1 = new HashMap<>();
                v1.put("code", "HEALTHUP5");
                v1.put("description", "Giảm 5% cho mọi đơn hàng từ 0đ");
                v1.put("discountPercent", 5);
                v1.put("minOrderValue", 0);
                v1.put("isActive", true);
                v1.put("expiryDate", Timestamp.now());
                list.add(v1);

                for (Map<String, Object> v : list) {
                    db.collection("promoCodes").document(String.valueOf(v.get("code"))).set(v);
                }
            }
        });
    }

    public void seedProductsIfEmpty() {
        db.collection("products").limit(1).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                List<Product> dummyProducts = Product.getDummyProducts();
                for (Product p : dummyProducts) {
                    db.collection("products").document(p.getId()).set(p);
                }
            }
        });
    }

    /**
     * SCRIPT NÂNG CẤP VÀ SỬA LỖI DỮ LIỆU:
     * 1. Chuyển đổi phân loại từ List sang Map.
     * 2. Sửa lỗi "Stringified Keys" (các key bị dính định dạng {price=..., label=...}).
     */
    public void upgradeAllProductsDataStructure() {
        db.collection("products").get().addOnSuccessListener(queryDocumentSnapshots -> {
            com.google.firebase.firestore.WriteBatch batch = db.batch();
            int count = 0;

            for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                Map<String, Object> updates = new HashMap<>();
                double basePrice = doc.getDouble("price") != null ? doc.getDouble("price") : 0;

                updates.putAll(processCategoryField(doc, "weights", basePrice));
                updates.putAll(processCategoryField(doc, "flavors", basePrice));
                updates.putAll(processCategoryField(doc, "packagingTypes", basePrice));

                if (!updates.isEmpty()) {
                    batch.update(doc.getReference(), updates);
                    count++;
                }
            }

            if (count > 0) {
                int finalCount = count;
                batch.commit().addOnSuccessListener(v -> Log.d("Migration", "Đã sửa và nâng cấp cấu trúc cho " + finalCount + " sản phẩm."));
            }
        });
    }

    private Map<String, Object> processCategoryField(DocumentSnapshot doc, String fieldName, double basePrice) {
        Map<String, Object> update = new HashMap<>();
        Object raw = doc.get(fieldName);
        if (raw == null) return update;

        Map<String, Double> cleanMap = new HashMap<>();
        boolean changed = false;

        if (raw instanceof List) {
            // Trường hợp 1: Đang là List, cần chuyển sang Map
            for (Object item : (List<?>) raw) {
                processSingleItem(item, cleanMap, basePrice);
            }
            changed = true;
        } else if (raw instanceof Map) {
            // Trường hợp 2: Đã là Map nhưng có thể bị lỗi "Stringified Keys"
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("{") && key.contains("label=")) {
                    // Đây là key bị lỗi định dạng, cần trích xuất lại
                    processSingleItem(key, cleanMap, basePrice);
                    changed = true;
                } else {
                    // Key đã chuẩn, giữ nguyên
                    Object val = entry.getValue();
                    double price = (val instanceof Number) ? ((Number) val).doubleValue() : basePrice;
                    cleanMap.put(key, price);
                }
            }
        }

        if (changed) {
            update.put(fieldName, cleanMap);
        }
        return update;
    }

    private void processSingleItem(Object item, Map<String, Double> targetMap, double basePrice) {
        String raw = String.valueOf(item);
        if (raw.startsWith("{") && raw.endsWith("}")) {
            // Trích xuất label và price từ chuỗi định dạng "{price=100, label=ABC}"
            String label = extractVal(raw, "label");
            if (label == null) label = extractVal(raw, "name");
            
            String priceStr = extractVal(raw, "price");
            double price = basePrice;
            try {
                if (priceStr != null) price = Double.parseDouble(priceStr);
            } catch (Exception ignored) {}
            
            if (label != null) targetMap.put(label, price);
        } else {
            targetMap.put(raw, basePrice);
        }
    }

    private String extractVal(String s, String key) {
        String target = key + "=";
        if (!s.contains(target)) return null;
        int start = s.indexOf(target) + target.length();
        int end = s.indexOf(",", start);
        if (end == -1) end = s.indexOf("}", start);
        if (end != -1) {
            String val = s.substring(start, end).trim();
            return "null".equalsIgnoreCase(val) ? null : val;
        }
        return null;
    }

    // --- ORDERS ---
    public Task<QuerySnapshot> getOrders() {
        String uid = getCurrentUserId();
        if (uid == null) return Tasks.forException(new Exception("User not logged in"));
        
        return db.collection("orders")
                .whereEqualTo("userId", uid)
                .get();
    }

    public Task<Void> updateOrderStatus(String orderId, String status) {
        if (orderId == null || orderId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("OrderId is missing"));
        }

        return db.collection("orders").document(orderId).get().continueWithTask(task -> {
            DocumentSnapshot orderDoc = task.getResult();
            if (!orderDoc.exists()) throw new Exception("Order not found");

            String userId = orderDoc.getString("userId");
            String fromStatus = orderDoc.getString("status");
            Double total = orderDoc.getDouble("totalPrice");
            if (total == null) total = 0.0;

            WriteBatch batch = db.batch();

            // 1. Cập nhật trạng thái đơn hàng
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status);
            updates.put("updatedAt", new java.util.Date());
            if ("delivered".equals(status)) {
                updates.put("deliveredAt", new java.util.Date());
            }
            batch.update(db.collection("orders").document(orderId), updates);

            // 2. Cập nhật tích lũy và hạng thành viên
            if (userId != null) {
                if ("delivered".equals(status) && !"delivered".equals(fromStatus)) {
                    // Chuyển sang Delivered -> Tăng tích lũy
                    batch.update(db.collection("users").document(userId),
                            "spentAmount", com.google.firebase.firestore.FieldValue.increment(total));
                } else if (("returned".equals(status) || "cancelled".equals(status)) && "delivered".equals(fromStatus)) {
                    // Nếu đã từng Delivered mà giờ bị Trả hoặc Hủy -> Giảm tích lũy
                    batch.update(db.collection("users").document(userId),
                            "spentAmount", com.google.firebase.firestore.FieldValue.increment(-total));
                }
            }

            return batch.commit();
        });
    }

    public Task<Void> cancelOrder(String orderId, String reason, double amount) {
        return cancelOrder(orderId, reason, amount, null);
    }

    public Task<Void> cancelOrder(String orderId, String reason, double amount, String orderCode) {
        if (orderId == null || orderId.isEmpty()) {
            return Tasks.forException(new Exception("OrderId is missing"));
        }

        String uid = getCurrentUserId();
        if (uid == null) return Tasks.forException(new Exception("User not logged in"));

        return db.collection("orders").document(orderId).get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                return Tasks.forException(new Exception("Không tìm thấy đơn hàng"));
            }
            DocumentSnapshot doc = task.getResult();
            String status = doc.getString("status");
            if (status == null || !Order.STATUS_PENDING.equalsIgnoreCase(status.trim())) {
                return Tasks.forException(new Exception("Chỉ hủy được đơn đang chờ xác nhận"));
            }

            String userIdField = doc.getString("userId");
            String buyerIdField = doc.getString("buyerId");
            final String orderOwnerId = (userIdField != null && !userIdField.isEmpty())
                    ? userIdField
                    : ((buyerIdField != null && !buyerIdField.isEmpty()) ? buyerIdField : uid);

            String displayCode = (orderCode != null && !orderCode.isEmpty())
                    ? orderCode
                    : (doc.getString("orderCode") != null ? doc.getString("orderCode") : orderId);

            Order parsedOrder = AdminRepository.parseOrderDocument(doc);
            boolean restoreStock = parsedOrder != null
                    && parsedOrder.isStockDeducted()
                    && !parsedOrder.isStockRestored()
                    && parsedOrder.getItems() != null
                    && !parsedOrder.getItems().isEmpty();

            Task<Void> stockTask = restoreStock
                    ? StockManager.restoreStockTask(db, parsedOrder.getItems())
                    : Tasks.forResult(null);

            return stockTask.continueWithTask(stockResult -> {
                if (!stockResult.isSuccessful()) {
                    Exception error = stockResult.getException();
                    return Tasks.forException(error != null ? error : new Exception("Không thể hoàn kho"));
                }

                com.google.firebase.firestore.WriteBatch batch = db.batch();

                Map<String, Object> updates = new HashMap<>();
                updates.put("status", Order.STATUS_CANCELLED);
                updates.put("cancelRequested", false);
                updates.put("cancelReason", reason != null ? reason : "");
                updates.put("cancelSource", Order.CANCEL_SOURCE_CUSTOMER);
                updates.put("cancelledAt", Timestamp.now());
                updates.put("updatedAt", Timestamp.now());
                if (restoreStock) {
                    updates.put("stockRestored", true);
                }
                batch.update(db.collection("orders").document(orderId), updates);

            if (amount > 0) {
                batch.update(db.collection("users").document(orderOwnerId),
                        "spentAmount", com.google.firebase.firestore.FieldValue.increment(-amount));
            }

            Map<String, Object> history = new HashMap<>();
            history.put("event", "customer_cancelled");
            history.put("fromStatus", Order.STATUS_PENDING);
            history.put("toStatus", Order.STATUS_CANCELLED);
            history.put("note", "Khách hủy đơn");
            history.put("reason", reason != null ? reason : "");
            history.put("adminUid", uid);
            history.put("adminEmail", "");
            history.put("actorRole", "buyer");
            history.put("createdAt", Timestamp.now());
            batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

            Map<String, Object> buyerNotif = new HashMap<>();
            buyerNotif.put("type", "ORDER_CANCELLED");
            buyerNotif.put("title", "Đơn hàng đã hủy");
            buyerNotif.put("body", "Bạn đã hủy đơn #" + displayCode
                    + (reason != null && !reason.isEmpty() ? (". Lý do: " + reason) : "."));
            buyerNotif.put("message", buyerNotif.get("body"));
            buyerNotif.put("refId", orderId);
            buyerNotif.put("orderId", orderId);
            buyerNotif.put("orderCode", displayCode);
            buyerNotif.put("read", false);
            buyerNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(orderOwnerId).collection("notifications").document(), buyerNotif);
            if (!orderOwnerId.equals(uid)) {
                batch.set(db.collection("users").document(uid).collection("notifications").document(), buyerNotif);
            }

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "ORDER_CANCELLED");
            notification.put("title", "Đơn hàng đã hủy");
            notification.put("body", "Khách đã hủy đơn #" + displayCode
                    + (reason != null && !reason.isEmpty() ? (". Lý do: " + reason) : "."));
            notification.put("orderId", orderId);
            notification.put("orderCode", displayCode);
            notification.put("buyerId", orderOwnerId);
            notification.put("reason", reason != null ? reason : "");
            notification.put("read", false);
            notification.put("createdAt", Timestamp.now());
                batch.set(db.collection("admin_notifications").document(), notification);

                return batch.commit();
            });
        });
    }

    public Task<Void> confirmReceived(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return Tasks.forException(new Exception("OrderId is missing"));
        }
        String uid = getCurrentUserId();
        if (uid == null) return Tasks.forException(new Exception("User not logged in"));

        return db.collection("orders").document(orderId).get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                return Tasks.forException(new Exception("Không tìm thấy đơn hàng"));
            }
            DocumentSnapshot doc = task.getResult();
            String status = doc.getString("status");
            Boolean shopConfirmed = doc.getBoolean("shopConfirmedDelivery");
            if (!Order.STATUS_SHIPPING.equals(status) || !Boolean.TRUE.equals(shopConfirmed)) {
                return Tasks.forException(new Exception("Chỉ xác nhận khi shop đã giao hàng"));
            }

            String displayCode = doc.getString("orderCode") != null ? doc.getString("orderCode") : orderId;
            String buyerId = doc.getString("userId");
            if (buyerId == null) buyerId = doc.getString("buyerId");
            if (buyerId == null) buyerId = uid;

            Double total = doc.getDouble("totalPrice");
            if (total == null) total = 0.0;

            com.google.firebase.firestore.WriteBatch batch = db.batch();
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", Order.STATUS_DELIVERED);
            updates.put("updatedAt", Timestamp.now());
            updates.put("deliveredAt", Timestamp.now());
            batch.update(db.collection("orders").document(orderId), updates);

            // Cập nhật tích lũy và hạng thành viên
            if (buyerId != null) {
                batch.update(db.collection("users").document(buyerId),
                        "spentAmount", com.google.firebase.firestore.FieldValue.increment(total));
            }

            Map<String, Object> history = new HashMap<>();
            history.put("event", "customer_received");
            history.put("fromStatus", Order.STATUS_SHIPPING);
            history.put("toStatus", Order.STATUS_DELIVERED);
            history.put("note", "Khách xác nhận đã nhận hàng");
            history.put("reason", "");
            history.put("adminUid", uid);
            history.put("adminEmail", "");
            history.put("actorRole", "buyer");
            history.put("createdAt", Timestamp.now());
            batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

            Map<String, Object> buyerNotif = new HashMap<>();
            buyerNotif.put("type", "ORDER_DELIVERED");
            buyerNotif.put("title", "Đã nhận hàng");
            buyerNotif.put("body", "Bạn đã xác nhận nhận đơn #" + displayCode + ".");
            buyerNotif.put("message", buyerNotif.get("body"));
            buyerNotif.put("refId", orderId);
            buyerNotif.put("orderId", orderId);
            buyerNotif.put("orderCode", displayCode);
            buyerNotif.put("read", false);
            buyerNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(buyerId).collection("notifications").document(), buyerNotif);

            Map<String, Object> adminNotif = new HashMap<>();
            adminNotif.put("type", "ORDER_DELIVERED");
            adminNotif.put("title", "Khách đã nhận hàng");
            adminNotif.put("body", "Khách xác nhận đã nhận đơn #" + displayCode);
            adminNotif.put("orderId", orderId);
            adminNotif.put("orderCode", displayCode);
            adminNotif.put("buyerId", buyerId);
            adminNotif.put("read", false);
            adminNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("admin_notifications").document(), adminNotif);

            return batch.commit();
        });
    }

    public Task<Void> simulateShopConfirmedDelivery(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("OrderId is missing"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("shopConfirmedDelivery", true);
        updates.put("shopConfirmedAt", Timestamp.now());
        updates.put("updatedAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> submitReturnRequest(String orderId, String reason, String desc,
                                          List<String> mediaUrls, String handling) {
        return submitReturnRequest(orderId, reason, desc, mediaUrls, handling, null);
    }

    public Task<Void> submitReturnRequest(String orderId, String reason, String desc,
                                          List<String> mediaUrls, String handling,
                                          List<Map<String, Object>> returnItems) {
        if (orderId == null || orderId.isEmpty()) {
            return Tasks.forException(new Exception("OrderId is missing"));
        }
        String uid = getCurrentUserId();
        if (uid == null) return Tasks.forException(new Exception("User not logged in"));

        return db.collection("orders").document(orderId).get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                return Tasks.forException(new Exception("Không tìm thấy đơn hàng"));
            }
            DocumentSnapshot doc = task.getResult();
            String status = doc.getString("status");
            if (!Order.STATUS_DELIVERED.equals(status)) {
                return Tasks.forException(new Exception("Chỉ trả hàng khi đơn đã giao"));
            }
            String existingReturn = doc.getString("returnStatus");
            if (Order.RETURN_REQUESTED.equals(existingReturn)
                    || Order.RETURN_APPROVED.equals(existingReturn)
                    || Order.RETURN_COMPLETED.equals(existingReturn)) {
                return Tasks.forException(new Exception("Đơn đã có yêu cầu trả hàng"));
            }

            String displayCode = doc.getString("orderCode") != null ? doc.getString("orderCode") : orderId;
            String buyerId = doc.getString("userId");
            if (buyerId == null) buyerId = doc.getString("buyerId");
            if (buyerId == null) buyerId = uid;

            Double total = doc.getDouble("totalPrice");
            if (total == null) total = 0.0;

            double refundAmount = 0;
            if (returnItems != null) {
                for (Map<String, Object> row : returnItems) {
                    if (row == null) continue;
                    Object priceObj = row.get("price");
                    Object qtyObj = row.get("quantity");
                    double price = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0;
                    int qty = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 0;
                    if (price > 0 && qty > 0) {
                        refundAmount += price * qty;
                    }
                }
            }
            // Legacy / full-order return when no line items were selected.
            if (refundAmount <= 0) {
                refundAmount = total;
            }

            com.google.firebase.firestore.WriteBatch batch = db.batch();
            Map<String, Object> updates = new HashMap<>();
            // Keep delivered so order stays in Đã giao; returnStatus drives Trả hàng tab
            updates.put("status", Order.STATUS_DELIVERED);
            updates.put("returnStatus", Order.RETURN_REQUESTED);
            updates.put("returnReason", reason != null ? reason : "");
            updates.put("returnDescription", desc != null ? desc : "");
            updates.put("returnMediaUris", mediaUrls != null ? mediaUrls : new ArrayList<>());
            updates.put("returnHandling", handling != null ? handling : "");
            updates.put("returnStep", 0);
            updates.put("returnRequestedAt", Timestamp.now());
            updates.put("returnRejectReason", com.google.firebase.firestore.FieldValue.delete());
            updates.put("refundAmount", refundAmount);
            if (returnItems != null) {
                updates.put("returnItems", returnItems);
            }
            updates.put("updatedAt", Timestamp.now());
            batch.update(db.collection("orders").document(orderId), updates);

            // Only reverse loyalty points for the refunded portion (not the whole order).
            if (buyerId != null && refundAmount > 0) {
                batch.update(db.collection("users").document(buyerId),
                        "spentAmount", com.google.firebase.firestore.FieldValue.increment(-refundAmount));
            }

            Map<String, Object> history = new HashMap<>();
            history.put("event", "return_requested");
            history.put("fromStatus", Order.STATUS_DELIVERED);
            history.put("toStatus", Order.STATUS_DELIVERED);
            history.put("note", "Khách yêu cầu trả hàng: " + (reason != null ? reason : ""));
            history.put("reason", reason != null ? reason : "");
            history.put("adminUid", uid);
            history.put("adminEmail", "");
            history.put("actorRole", "buyer");
            history.put("createdAt", Timestamp.now());
            batch.set(db.collection("orders").document(orderId).collection("history").document(), history);

            Map<String, Object> buyerNotif = new HashMap<>();
            buyerNotif.put("type", "ORDER_RETURN_REQUESTED");
            buyerNotif.put("title", "Đã gửi yêu cầu trả hàng");
            buyerNotif.put("body", "Yêu cầu trả hàng đơn #" + displayCode + " đã được gửi. Vui lòng chờ shop xử lý.");
            buyerNotif.put("message", buyerNotif.get("body"));
            buyerNotif.put("refId", orderId);
            buyerNotif.put("orderId", orderId);
            buyerNotif.put("returnId", orderId);
            buyerNotif.put("returnRequestId", orderId);
            buyerNotif.put("orderCode", displayCode);
            buyerNotif.put("read", false);
            buyerNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("users").document(buyerId).collection("notifications").document(), buyerNotif);

            Map<String, Object> adminNotif = new HashMap<>();
            adminNotif.put("type", "ORDER_RETURN_REQUESTED");
            adminNotif.put("title", "Yêu cầu trả hàng mới");
            adminNotif.put("body", "Khách yêu cầu trả hàng đơn #" + displayCode
                    + (reason != null && !reason.isEmpty() ? (". Lý do: " + reason) : "."));
            adminNotif.put("orderId", orderId);
            adminNotif.put("refId", orderId);
            adminNotif.put("returnId", orderId);
            adminNotif.put("returnRequestId", orderId);
            adminNotif.put("orderCode", displayCode);
            adminNotif.put("buyerId", buyerId);
            adminNotif.put("read", false);
            adminNotif.put("createdAt", Timestamp.now());
            batch.set(db.collection("admin_notifications").document(), adminNotif);

            return batch.commit();
        });
    }

    public Task<Void> advanceReturnStep(String orderId, int nextStep, boolean isFinal) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("returnStep", nextStep);
        updates.put("updatedAt", Timestamp.now());
        if (isFinal) {
            updates.put("status", Order.STATUS_COMPLETED);
            updates.put("returnStatus", Order.RETURN_COMPLETED);
        }
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> updateItemReview(String orderId, int itemIndex, Review review, List<OrderItem> allItems) {
        if (itemIndex < 0 || itemIndex >= allItems.size()) return Tasks.forException(new Exception("Invalid index"));
        
        // 1. Cập nhật review vào Order (để hiện trạng thái "Đã đánh giá" trong đơn hàng)
        allItems.get(itemIndex).setReview(review);
        Map<String, Object> updates = new HashMap<>();
        updates.put("items", allItems);
        updates.put("reviewed", true);
        updates.put("updatedAt", Timestamp.now());
        Task<Void> orderTask = db.collection("orders").document(orderId).update(updates);

        // 2. Đồng thời đẩy review này vào danh sách review của sản phẩm
        String productId = allItems.get(itemIndex).getProductId();
        
        // Fallback: Nếu đơn hàng cũ không có productId, ta có thể thử lấy từ một chỗ khác hoặc bỏ qua
        // Nhưng tốt nhất nên log để debug
        if (productId != null && !productId.isEmpty()) {
            DocumentReference productRef = db.collection("products").document(productId);
            // Lưu vào sub-collection
            productRef.collection("reviews").add(review)
                .addOnFailureListener(e -> android.util.Log.e("FirebaseManager", "Lỗi lưu review vào SP: " + e.getMessage()));
            
            // Cập nhật thống kê sơ bộ (tăng count)
            productRef.update("reviewCount", com.google.firebase.firestore.FieldValue.increment(1));
        } else {
            android.util.Log.w("FirebaseManager", "Không thể lưu review vào SP vì productId bị thiếu (đơn hàng cũ)");
        }
        
        return orderTask;
    }

    public Task<Void> rebuyOrder(List<OrderItem> rebuyItems) {
        String userId = getCurrentUserId();
        if (userId == null) return Tasks.forException(new Exception("User not logged in"));

        com.google.firebase.firestore.CollectionReference cartRef = db.collection("users").document(userId).collection("cart");

        return cartRef.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                if (task.getException() != null) throw task.getException();
                throw new Exception("Cannot access cart");
            }

            com.google.firebase.firestore.WriteBatch batch = db.batch();
            List<DocumentSnapshot> existingCartDocs = task.getResult().getDocuments();

            // 1. Bỏ chọn tất cả các sản phẩm đang có trong giỏ
            for (DocumentSnapshot doc : existingCartDocs) {
                batch.update(doc.getReference(), "selected", false);
            }

            // 2. Xử lý các sản phẩm mua lại
            for (OrderItem item : rebuyItems) {
                String pId = item.getProductId();
                String vId = item.getVariantId();
                String name = item.getName();
                String variantLabel = item.getVariantLabel();

                // Tìm sản phẩm trùng khớp trong giỏ hàng hiện tại để cập nhật thay vì tạo mới
                DocumentSnapshot existingDoc = null;
                for (DocumentSnapshot doc : existingCartDocs) {
                    String docPId = doc.getString("productId");
                    String docVId = doc.getString("variantId");
                    String docName = doc.getString("name");
                    String docVLabel = doc.getString("variantName");

                    // Khớp theo ID (ưu tiên) hoặc khớp theo Tên + Biến thể (dành cho dữ liệu cũ)
                    boolean matchId = (pId != null && pId.equals(docPId)) && 
                                     ((vId == null && docVId == null) || (vId != null && vId.equals(docVId)));
                    
                    boolean matchName = (pId == null && name != null && name.equals(docName)) &&
                                       ((variantLabel == null && docVLabel == null) || (variantLabel != null && variantLabel.equals(docVLabel)));

                    if (matchId || matchName) {
                        existingDoc = doc;
                        break;
                    }
                }

                com.google.firebase.firestore.DocumentReference docRef = (existingDoc != null) 
                        ? existingDoc.getReference() 
                        : cartRef.document(); // Nếu hoàn toàn mới thì tạo ID ngẫu nhiên

                Map<String, Object> cartData = new HashMap<>();
                if (pId != null) cartData.put("productId", pId);
                if (vId != null) cartData.put("variantId", vId);
                cartData.put("name", name);
                cartData.put("variantName", variantLabel);
                cartData.put("price", item.getPrice());
                cartData.put("originalPrice", item.getOriginalPrice() > 0 ? item.getOriginalPrice() : item.getPrice());
                cartData.put("quantity", item.getQuantity()); // Ghi đè số lượng
                cartData.put("imageUrl", item.getImageUrl());
                cartData.put("selected", true); // Chỉ món mua lại mới được tick
                cartData.put("userId", userId);
                
                batch.set(docRef, cartData, com.google.firebase.firestore.SetOptions.merge());
            }

            return batch.commit();
        });
    }


    // --- IMAGE PROCESSING (Alternative for blocked Storage) ---
    public Task<Uri> uploadImage(Uri fileUri) {
        if (fileUri == null) return com.google.android.gms.tasks.Tasks.forException(new Exception("File URI is null"));

        com.google.android.gms.tasks.TaskCompletionSource<Uri> tcs = new com.google.android.gms.tasks.TaskCompletionSource<Uri>();
        
        // Chạy xử lý ảnh trong một thread riêng để không làm lag giao diện
        new Thread(() -> {
            try {
                android.content.Context context = com.google.firebase.FirebaseApp.getInstance().getApplicationContext();
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.getContentResolver(), fileUri);
                
                // Nén ảnh thật nhỏ để không vượt giới hạn 1MB của Firestore
                // Scale ảnh về chiều rộng tối đa 800px
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float bitmapRatio = (float)width / (float) height;
                if (width > 800) {
                    width = 800;
                    height = (int) (width / bitmapRatio);
                }
                android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true);
                
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos); // Nén chất lượng 60%
                byte[] b = baos.toByteArray();
                String encodedImage = android.util.Base64.encodeToString(b, android.util.Base64.DEFAULT);
                
                // Trả về một "Data URI" để Glide vẫn có thể hiển thị được
                String dataUriString = "data:image/jpeg;base64," + encodedImage;
                tcs.setResult(Uri.parse(dataUriString));
            } catch (Exception e) {
                tcs.setException(e);
            }
        }).start();
            
        return tcs.getTask();
    }

    public Task<List<Uri>> uploadMultipleImages(List<Uri> uris) {
        List<Task<Uri>> tasks = new ArrayList<>();
        for (Uri uri : uris) {
            tasks.add(uploadImage(uri));
        }
        return Tasks.whenAllSuccess(tasks);
    }
}
