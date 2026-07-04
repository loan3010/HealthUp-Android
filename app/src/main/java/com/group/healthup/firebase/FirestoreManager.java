package com.group.healthup.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.group.models.Product;

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

    public FirebaseFirestore getFirestore() {
        return db;
    }

    public CollectionReference getProductsCollection() {
        return db.collection("products");
    }

    public Query getFilteredProducts(String category, String sortOrder, double minPrice, double maxPrice, float minRating) {
        Query query = db.collection("products");

        // 1. Lọc theo danh mục (Sử dụng field 'cat' như trong Product model)
        if (category != null && !category.isEmpty() && !category.equals("Tất cả")) {
            query = query.whereEqualTo("cat", category);
        }

        // 2. Lọc đơn giản để tránh lỗi Index Firestore
        if (maxPrice > 0 && maxPrice < 5000000) {
            query = query.whereGreaterThanOrEqualTo("price", minPrice)
                         .whereLessThanOrEqualTo("price", maxPrice)
                         .orderBy("price");
        } else if (sortOrder != null) {
            switch (sortOrder) {
                case "Giá Thấp-Cao":
                    query = query.orderBy("price", Query.Direction.ASCENDING);
                    break;
                case "Giá Cao-Thấp":
                    query = query.orderBy("price", Query.Direction.DESCENDING);
                    break;
                case "Mới nhất":
                    query = query.orderBy("createdAt", Query.Direction.DESCENDING);
                    break;
            }
        }

        return query;
    }
}
