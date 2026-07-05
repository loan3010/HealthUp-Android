package com.example.healthup.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.example.models.Product;

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

        // 1. Lọc theo danh mục (Ưu tiên lọc field này trước)
        if (category != null && !category.isEmpty() && !category.equals("Tất cả")) {
            query = query.whereEqualTo("cat", category);
        }

        // 2. Lọc theo giá - CHỈ lọc nếu không phải dải mặc định
        if (minPrice > 0 || maxPrice < 10000000) {
            query = query.whereGreaterThanOrEqualTo("price", minPrice)
                         .whereLessThanOrEqualTo("price", maxPrice);
            // Lưu ý: Nếu lọc range trên field 'price', Firestore yêu cầu orderBy trên chính field đó trước
            query = query.orderBy("price", Query.Direction.ASCENDING);
        }

        // 3. Sắp xếp
        if (sortOrder != null) {
            if (sortOrder.equals("Giá Thấp-Cao")) {
                // Đã được handle bởi logic range filter nếu có
                if (!(minPrice > 0 || maxPrice < 10000000)) {
                    query = query.orderBy("price", Query.Direction.ASCENDING);
                }
            } else if (sortOrder.equals("Giá Cao-Thấp")) {
                query = query.orderBy("price", Query.Direction.DESCENDING);
            } else if (sortOrder.equals("Mới nhất")) {
                query = query.orderBy("createdAt", Query.Direction.DESCENDING);
            } else if (sortOrder.equals("Phổ biến")) {
                // Sắp xếp theo số lượng bán nếu có field 'sold'
                query = query.orderBy("sold", Query.Direction.DESCENDING);
            }
        }

        return query;
    }
}
