package com.example.healthup;

import android.net.Uri;
import com.example.models.Address;
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
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> cancelOrder(String orderId, String reason, double amount) {
        if (orderId == null || orderId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("OrderId is missing"));
        }
        
        String uid = getCurrentUserId();
        if (uid == null) return Tasks.forException(new Exception("User not logged in"));

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        
        // 1. Cập nhật trạng thái đơn hàng
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "cancelled");
        updates.put("returnReason", reason);
        updates.put("updatedAt", Timestamp.now());
        batch.update(db.collection("orders").document(orderId), updates);
        
        // 2. Hoàn lại số tiền đã chi trong tích lũy
        DocumentReference userRef = db.collection("users").document(uid);
        batch.update(userRef, "spentAmount", com.google.firebase.firestore.FieldValue.increment(-amount));
        
        return batch.commit();
    }

    public Task<Void> confirmReceived(String orderId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "delivered");
        updates.put("updatedAt", Timestamp.now());
        updates.put("deliveredAt", Timestamp.now());
        return db.collection("orders").document(orderId).update(updates);
    }

    public Task<Void> simulateShopConfirmedDelivery(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("OrderId is missing"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("shopConfirmedDelivery", true);
        updates.put("updatedAt", Timestamp.now());
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


    // --- STORAGE ---
    public Task<Uri> uploadImage(Uri fileUri) {
        if (fileUri == null) return com.google.android.gms.tasks.Tasks.forException(new Exception("File URI is null"));
        
        com.google.android.gms.tasks.TaskCompletionSource<Uri> tcs = new com.google.android.gms.tasks.TaskCompletionSource<>();
        String fileName = UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child("evidence/" + fileName);
        
        ref.putFile(fileUri)
            .addOnSuccessListener(taskSnapshot -> {
                // Sau khi upload thành công mới lấy URL
                ref.getDownloadUrl()
                    .addOnSuccessListener(tcs::setResult)
                    .addOnFailureListener(tcs::setException);
            })
            .addOnFailureListener(tcs::setException);
            
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
