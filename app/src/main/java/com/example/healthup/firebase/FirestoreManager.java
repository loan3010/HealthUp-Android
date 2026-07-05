package com.example.healthup.firebase;

import com.example.models.Blog;
import com.example.models.Category;
import com.example.models.FAQ;
import com.example.models.Product;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class FirestoreManager {
    private static FirestoreManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }

    private String getUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public void getCategories(OnCompleteListener<QuerySnapshot> listener) {
        db.collection("categories").get().addOnCompleteListener(listener);
    }

    public void getNewProducts(int limit, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("products")
                .limit(limit)
                .get()
                .addOnCompleteListener(listener);
    }

    public void getProductsByCategory(String categoryName, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("products")
                .whereEqualTo("cat", categoryName)
                .get()
                .addOnCompleteListener(listener);
    }

    public void getProductDetail(String productId, OnCompleteListener<DocumentSnapshot> listener) {
        db.collection("products").document(productId).get().addOnCompleteListener(listener);
    }

    // --- Wishlist ---
    public void getWishlist(OnCompleteListener<QuerySnapshot> listener) {
        String uid = getUserId();
        if (uid == null) {
            return;
        }
        db.collection("wishlist")
                .whereEqualTo("userId", uid)
                .get()
                .addOnCompleteListener(listener);
    }

    public Task<Void> addToWishlist(Product product) {
        String uid = getUserId();
        if (uid == null) return null;
        
        Map<String, Object> item = new HashMap<>();
        item.put("userId", uid);
        item.put("productId", product.getId());
        item.put("timestamp", FieldValue.serverTimestamp());
        item.put("productName", product.getName());
        item.put("productPrice", product.getPrice());
        item.put("productImage", product.getImageUrl());
        
        return db.collection("wishlist").document(uid + "_" + product.getId()).set(item);
    }

    public Task<Void> removeFromWishlist(String productId) {
        String uid = getUserId();
        if (uid == null) return null;
        return db.collection("wishlist").document(uid + "_" + productId).delete();
    }

    public void checkWishlistStatus(String productId, OnCompleteListener<DocumentSnapshot> listener) {
        String uid = getUserId();
        if (uid == null) return;
        db.collection("wishlist").document(uid + "_" + productId).get().addOnCompleteListener(listener);
    }

    public void getFlashSaleProducts(OnCompleteListener<QuerySnapshot> listener) {
        db.collection("products")
                .whereEqualTo("isFlashSale", true)
                .get()
                .addOnCompleteListener(listener);
    }

    // --- FAQs ---
    public void getFAQs(OnCompleteListener<QuerySnapshot> listener) {
        db.collection("faqs").get().addOnCompleteListener(listener);
    }

    // --- Blogs ---
    public void getBlogs(int limit, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("blogs")
                .limit(limit)
                .get()
                .addOnCompleteListener(listener);
    }

    public void getBlogs(OnCompleteListener<QuerySnapshot> listener) {
        db.collection("blogs")
                .get()
                .addOnCompleteListener(listener);
    }
}
