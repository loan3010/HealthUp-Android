package com.example.healthup.firebase;

import com.example.models.Category;
import com.example.models.Product;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

public class FirestoreManager {
    private static FirestoreManager instance;
    private FirebaseFirestore db;

    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }

    public void getCategories(OnCompleteListener<QuerySnapshot> listener) {
        db.collection("categories").get().addOnCompleteListener(listener);
    }

    public void getFlashSaleProducts(OnCompleteListener<QuerySnapshot> listener) {
        // Thử cả 2 trường hợp đặt tên: isFlashSale và is_flash_sale
        db.collection("products")
                .whereEqualTo("isFlashSale", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().isEmpty()) {
                        // Nếu không có, thử query theo snake_case
                        db.collection("products")
                                .whereEqualTo("is_flash_sale", true)
                                .get()
                                .addOnCompleteListener(listener);
                    } else {
                        listener.onComplete(task);
                    }
                });
    }

    public void getNewProducts(int limit, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("products")
                .limit(limit)
                .get()
                .addOnCompleteListener(listener);
    }

    public void getBlogs(int limit, OnCompleteListener<QuerySnapshot> listener) {
        db.collection("blogs")
                .orderBy("publishedAt", Query.Direction.DESCENDING)
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

    public void searchProducts(String query, OnCompleteListener<QuerySnapshot> listener) {
        // Firestore search is basic, usually done by searching prefix or using external service.
        // For simple search, we fetch and filter locally or use whereGreaterThanOrEqualTo
        db.collection("products")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .get()
                .addOnCompleteListener(listener);
    }
}
