package com.group.healthup;

import android.net.Uri;
import com.group.models.Address;
import com.group.models.Order;
import com.group.models.OrderItem;
import com.group.models.Product;
import com.group.models.Review;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
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
        return "test_user_id"; // ID mặc định để bạn test khi chưa làm phần Login
    }

    // --- PRODUCTS ---
    public Task<QuerySnapshot> getProducts() {
        return db.collection("products").get();
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

    // --- ORDERS ---
    public Task<QuerySnapshot> getOrders() {
        // Lấy tất cả đơn hàng của User và sắp xếp theo thời gian cập nhật mới nhất
        return db.collection("orders")
                .whereEqualTo("userId", getCurrentUserId())
                .get(); 
        // Lưu ý: Nếu muốn dùng .orderBy() kèm .whereEqualTo(), bạn phải nhấn vào link lỗi trong Logcat để tạo Index trên Firebase.
    }

    public Task<Void> updateOrderStatus(String orderId, String status) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> confirmReceived(String orderId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "delivered");
        updates.put("updatedAt", Timestamp.now());
        updates.put("deliveredAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> submitReturnRequest(String orderId, String reason, String desc, List<String> mediaUrls, String handling) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "returned");
        updates.put("returnReason", reason);
        updates.put("returnDescription", desc);
        updates.put("returnMediaUris", mediaUrls);
        updates.put("returnHandling", handling);
        updates.put("updatedAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> updateItemReview(String orderId, int itemIndex, Review review, List<OrderItem> allItems) {
        if (itemIndex < 0 || itemIndex >= allItems.size()) return Tasks.forException(new Exception("Invalid index"));
        allItems.get(itemIndex).setReview(review);
        Map<String, Object> updates = new HashMap<>();
        updates.put("items", allItems);
        updates.put("reviewed", true);
        updates.put("updatedAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public void seedOrdersIfEmpty() {
        db.collection("orders").limit(1).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                List<Order> dummyOrders = createDummyOrdersForFirebase();
                for (Order o : dummyOrders) {
                    DocumentReference ref = db.collection("orders").document();
                    o.setId(ref.getId());
                    ref.set(o);
                }
            }
        });
    }

    private List<Order> createDummyOrdersForFirebase() {
        List<Order> list = new ArrayList<>();
        String uid = getCurrentUserId();
        Address addr1 = new Address("Xuân Mai", "0912345678", "Số 45, Nguyễn Văn Linh, Phường Tân Phong, Quận 7, TP. Hồ Chí Minh", true);
        Address addr2 = new Address("Trần Văn An", "0988777666", "123 Đường 3/2, Phường 12, Quận 10, TP. Hồ Chí Minh", false);
        Address addr3 = new Address("Lê Thị B", "0909123456", "Ký túc xá Khu B ĐHQG, Phường Đông Hòa, Dĩ An, Bình Dương", false);
        
        long nowMs = 1719810000000L; // Mốc tháng 7/2026
        long day = 86400000L;
        long hour = 3600000L;

        List<OrderItem> items1 = new ArrayList<>();
        items1.add(new OrderItem("Hạt mix dinh dưỡng hạnh nhân óc chó", "Hũ 500g", 75000, 2, "https://via.placeholder.com/150"));
        items1.add(new OrderItem("Trái cây sấy lạnh thập cẩm", "Gói 200g", 52000, 1, "https://via.placeholder.com/150"));

        List<OrderItem> items2 = new ArrayList<>();
        items2.add(new OrderItem("Trái cây sấy lạnh thập cẩm xoài...", "Gói 100g", 52000, 1, "https://via.placeholder.com/150"));

        List<OrderItem> items3 = new ArrayList<>();
        items3.add(new OrderItem("Bánh gạo lứt rong biển không đường", "Hộp 200g", 29000, 3, "https://via.placeholder.com/150"));
        items3.add(new OrderItem("Hạt điều vị tỏi ớt", "Hũ 300g", 69000, 1, "https://via.placeholder.com/150"));

        // Tạo 10 đơn hàng mẫu rải rác các trạng thái
        list.add(createOrder("ORD001", items1, "delivered", "paid", 233000, new Timestamp(new java.util.Date(nowMs - day * 2)), "Ví MoMo", addr1, uid));
        list.add(createOrder("ORD002", items2, "pending", "unpaid", 52000, new Timestamp(new java.util.Date(nowMs - hour * 5)), "Thanh toán khi nhận hàng", addr2, uid));
        list.add(createOrder("ORD003", items3, "confirmed", "paid", 156000, new Timestamp(new java.util.Date(nowMs - day)), "VNPAY-QR", addr3, uid));
        
        Order o4 = createOrder("ORD004", items1, "shipping", "paid", 233000, new Timestamp(new java.util.Date(nowMs - day * 1)), "Ví ZaloPay", addr1, uid);
        o4.setShopConfirmedDelivery(true);
        o4.setDeliveredAt(new Timestamp(new java.util.Date(nowMs - hour * 2)));
        list.add(o4);

        list.add(createOrder("ORD005", items2, "shipping", "paid", 52000, new Timestamp(new java.util.Date(nowMs - hour * 10)), "Thẻ ATM", addr2, uid));
        list.add(createOrder("ORD006", items3, "cancelled", "unpaid", 156000, new Timestamp(new java.util.Date(nowMs - day * 15)), "COD", addr3, uid));
        list.add(createOrder("ORD007", items1, "pending", "paid", 233000, new Timestamp(new java.util.Date(nowMs - hour)), "Ví MoMo", addr1, uid));
        list.add(createOrder("ORD008", items2, "delivered", "paid", 52000, new Timestamp(new java.util.Date(nowMs - day * 5)), "VNPAY-QR", addr2, uid));
        list.add(createOrder("ORD009", items3, "pending", "unpaid", 156000, new Timestamp(new java.util.Date(nowMs - hour * 2)), "COD", addr3, uid));
        list.add(createOrder("ORD010", items1, "confirmed", "paid", 233000, new Timestamp(new java.util.Date(nowMs - day * 3)), "Thẻ Tín dụng", addr1, uid));

        return list;
    }

    private Order createOrder(String code, List<OrderItem> items, String status, String pStatus, double price, Timestamp time, String method, Address addr, String uid) {
        Order o = new Order(code, items, status, pStatus, price, time, method, addr);
        o.setUserId(uid);
        o.setUpdatedAt(time);
        return o;
    }

    // --- STORAGE ---
    public Task<Uri> uploadImage(Uri fileUri) {
        StorageReference ref = storage.getReference().child("evidence/" + UUID.randomUUID().toString());
        return ref.putFile(fileUri).continueWithTask(task -> {
            if (!task.isSuccessful() && task.getException() != null) {
                throw task.getException();
            }
            return ref.getDownloadUrl();
        });
    }

    public Task<List<Uri>> uploadMultipleImages(List<Uri> uris) {
        List<Task<Uri>> tasks = new ArrayList<>();
        for (Uri uri : uris) {
            tasks.add(uploadImage(uri));
        }
        return Tasks.whenAllSuccess(tasks);
    }
}
