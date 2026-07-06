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

    public Task<Void> cancelOrder(String orderId, String reason) {
        if (orderId == null || orderId.isEmpty()) {
            return com.google.android.gms.tasks.Tasks.forException(new Exception("OrderId is missing"));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "cancelled");
        updates.put("returnReason", reason);
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
