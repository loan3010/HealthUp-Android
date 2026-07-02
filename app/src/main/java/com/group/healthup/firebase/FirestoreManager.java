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

        if (category != null && !category.isEmpty() && !category.equals("Tất cả")) {
            query = query.whereEqualTo("cat", category);
        }

        if (minPrice >= 0) {
            query = query.whereGreaterThanOrEqualTo("price", minPrice);
        }
        if (maxPrice > 0) {
            query = query.whereLessThanOrEqualTo("price", maxPrice);
        }
        
        // Apply ordering based on sortOrder
        if (sortOrder != null) {
            switch (sortOrder) {
                case "Phổ biến":
                    query = query.orderBy("soldCount", Query.Direction.DESCENDING);
                    break;
                case "Mới nhất":
                    query = query.orderBy("createdAt", Query.Direction.DESCENDING);
                    break;
                case "Giá Thấp-Cao":
                    query = query.orderBy("price", Query.Direction.ASCENDING);
                    break;
                case "Giá Cao-Thấp":
                    query = query.orderBy("price", Query.Direction.DESCENDING);
                    break;
                case "Được yêu thích":
                    query = query.orderBy("favoriteCount", Query.Direction.DESCENDING);
                    break;
                default:
                    // Default sort if none matches
                    query = query.orderBy("soldCount", Query.Direction.DESCENDING);
                    break;
            }
        }

        return query;
    }
}
