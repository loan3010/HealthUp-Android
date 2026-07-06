package com.example.healthup;


import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.healthup.databinding.ActivityProductReviewsBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Review;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;


public class ProductReviewsActivity extends AppCompatActivity {
    private ActivityProductReviewsBinding binding;
    private String productId;
    private ProductReviewEntryAdapter reviewAdapter;
    private final List<Review> reviewList = new ArrayList<>();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // FIX: nhận productId để query dữ liệu đánh giá thật của đúng sản phẩm
        productId = getIntent().getStringExtra("productId");


        String productName = getIntent().getStringExtra("product_name");
        if (productName != null) {
            binding.tvToolbarTitle.setText("Đánh giá " + productName);
        }


        binding.btnBack.setOnClickListener(v -> finish());


        setupList();


        if (productId != null) {
            fetchReviewsFromFirestore();
        } else {
            // Không có productId truyền vào -> không thể tải đánh giá thật
            binding.tvNoReviews.setVisibility(View.VISIBLE);
        }
    }


    private void setupList() {
        reviewAdapter = new ProductReviewEntryAdapter(reviewList);
        binding.rvReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReviews.setAdapter(reviewAdapter);
    }


    // FIX: thay dữ liệu mock bằng dữ liệu thật, lấy toàn bộ đánh giá của sản phẩm
    // từ sub-collection "reviews", giống cách ProductDetailActivity.fetchReviews() đang làm
    // (nhưng KHÔNG giới hạn limit(5) vì đây là màn hình "Xem tất cả")
    private void fetchReviewsFromFirestore() {
        FirestoreManager.getInstance().getProductsCollection()
                .document(productId)
                .collection("reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    reviewList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Review r = doc.toObject(Review.class);
                        if (r != null) reviewList.add(r);
                    }
                    reviewAdapter.notifyDataSetChanged();
                    binding.tvNoReviews.setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    binding.tvNoReviews.setVisibility(View.VISIBLE);
                });
    }
}