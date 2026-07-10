package com.example.healthup.firebase;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.models.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * CHỈ lọc theo category trên Firestore (whereEqualTo đơn lẻ).
     * KHÔNG kết hợp orderBy hay whereGreaterThan/LessThan ở đây nữa,
     * vì đó là nguyên nhân gây lỗi "FAILED_PRECONDITION: query requires an index".
     * Việc lọc giá/rating và sắp xếp sẽ được xử lý ở processProductSnapshots() bên dưới.
     */
    public Query getFilteredProductsQuery(String category) {
        Query query = db.collection("products");
        if (category != null && !category.isEmpty() && !category.equals("Tất cả")) {
            query = query.whereEqualTo("cat", category);
        }
        return query;
    }

    /**
     * Nhận kết quả thô từ Firestore (đã lọc category), sau đó:
     * 1. Lọc theo khoảng giá (minPrice - maxPrice)
     * 2. Lọc theo rating tối thiểu (minRating)
     * 3. Sắp xếp theo sortOrder
     * Tất cả xử lý bằng Java, không cần Firestore composite index.
     *
     * Lưu ý: đọc trực tiếp field thô ("price", "rating", "sold", "createdAt") từ
     * DocumentSnapshot để không phụ thuộc vào tên hàm getter cụ thể trong Product.java.
     */
    public List<Product> processProductSnapshots(QuerySnapshot snapshots, String sortOrder,
                                                 double minPrice, double maxPrice, float minRating) {
        List<Product> filtered = new ArrayList<>();
        Map<String, Double> priceMap = new HashMap<>();
        Map<String, Long> soldMap = new HashMap<>();
        Map<String, Timestamp> createdAtMap = new HashMap<>();

        if (snapshots == null) return filtered;

        for (DocumentSnapshot doc : snapshots) {
            Product p = doc.toObject(Product.class);
            if (p == null) continue;
            if (!Product.isVisibleToBuyers(doc)) continue;
            p.setId(doc.getId());

            Double priceVal = doc.getDouble("price");
            double price = (priceVal != null) ? priceVal : 0;

            Double ratingVal = doc.getDouble("rating");
            double rating = (ratingVal != null) ? ratingVal : 0;

            // Lọc theo giá
            if (price < minPrice || price > maxPrice) continue;
            // Lọc theo rating tối thiểu
            if (minRating > 0 && rating < minRating) continue;

            priceMap.put(doc.getId(), price);
            Long soldVal = doc.getLong("sold");
            soldMap.put(doc.getId(), soldVal != null ? soldVal : 0L);
            createdAtMap.put(doc.getId(), doc.getTimestamp("createdAt"));

            filtered.add(p);
        }

        if (sortOrder != null) {
            if (sortOrder.equals("Giá Thấp-Cao")) {
                filtered.sort((a, b) -> Double.compare(
                        priceMap.getOrDefault(a.getId(), 0.0),
                        priceMap.getOrDefault(b.getId(), 0.0)));
            } else if (sortOrder.equals("Giá Cao-Thấp")) {
                filtered.sort((a, b) -> Double.compare(
                        priceMap.getOrDefault(b.getId(), 0.0),
                        priceMap.getOrDefault(a.getId(), 0.0)));
            } else if (sortOrder.equals("Mới nhất")) {
                filtered.sort((a, b) -> {
                    Timestamp ta = createdAtMap.get(a.getId());
                    Timestamp tb = createdAtMap.get(b.getId());
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta); // mới nhất lên trước
                });
            } else if (sortOrder.equals("Phổ biến")) {
                filtered.sort((a, b) -> Long.compare(
                        soldMap.getOrDefault(b.getId(), 0L),
                        soldMap.getOrDefault(a.getId(), 0L)));
            }
        }

        return filtered;
    }
}
