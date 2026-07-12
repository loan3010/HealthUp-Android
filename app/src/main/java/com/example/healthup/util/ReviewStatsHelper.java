package com.example.healthup.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.example.models.Review;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReviewStatsHelper {

    public static final class Stats {
        public final float avgRating;
        public final int count;

        public Stats(float avgRating, int count) {
            this.avgRating = avgRating;
            this.count = count;
        }
    }

    public interface StatsCallback {
        void onLoaded(@NonNull Stats stats);
    }

    private ReviewStatsHelper() {
    }

    @NonNull
    public static Stats computeFromSnapshot(@Nullable QuerySnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return new Stats(0f, 0);
        }
        float sum = 0f;
        int count = 0;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Review review = doc.toObject(Review.class);
            if (review != null && review.getRating() > 0) {
                sum += review.getRating();
                count++;
            }
        }
        if (count == 0) {
            return new Stats(0f, 0);
        }
        return new Stats(sum / count, count);
    }

    public static void loadForProduct(@NonNull String productId, @NonNull StatsCallback callback) {
        FirestoreManager.getInstance().getProductsCollection()
                .document(productId)
                .collection("reviews")
                .get()
                .addOnSuccessListener(snapshot -> callback.onLoaded(computeFromSnapshot(snapshot)))
                .addOnFailureListener(e -> callback.onLoaded(new Stats(0f, 0)));
    }

    public static void applyToProduct(@NonNull Product product, @NonNull Stats stats) {
        product.setReviewCount(stats.count);
        product.setRating(stats.avgRating);
    }

    public static void enrichProducts(@Nullable List<Product> products, @Nullable Runnable onComplete) {
        if (products == null || products.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        AtomicInteger remaining = new AtomicInteger(products.size());
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                if (remaining.decrementAndGet() == 0 && onComplete != null) {
                    onComplete.run();
                }
                continue;
            }
            loadForProduct(product.getId(), stats -> {
                if (stats.count > 0) {
                    applyToProduct(product, stats);
                }
                if (remaining.decrementAndGet() == 0 && onComplete != null) {
                    onComplete.run();
                }
            });
        }
    }

    @NonNull
    public static String formatAvgRating(float rating) {
        return String.format(Locale.getDefault(), "%.1f", rating);
    }

    @NonNull
    public static String formatReviewCountLabel(int count) {
        return count + " đánh giá";
    }

    @NonNull
    public static String formatCardRating(@NonNull Product product) {
        float rating = product.getRating();
        int count = product.getReviewCount();
        if (count > 0) {
            return String.format(Locale.getDefault(), "%.1f (%d)", rating, count);
        }
        if (rating > 0) {
            return formatAvgRating(rating);
        }
        return "0.0";
    }
}
