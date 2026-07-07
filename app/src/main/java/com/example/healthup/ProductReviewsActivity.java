package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Locale;

public class ProductReviewsActivity extends AppCompatActivity {
    private ActivityProductReviewsBinding binding;
    private String productId;
    private ProductReviewEntryAdapter reviewAdapter;

    // FIX: giữ 2 danh sách - allReviews (dữ liệu gốc từ Firestore) và reviewList (dữ liệu
    // đã lọc theo chip + từ khóa search, được adapter hiển thị)
    private final List<Review> allReviews = new ArrayList<>();
    private final List<Review> reviewList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất nội dung
        View root = findViewById(R.id.product_reviews_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        productId = getIntent().getStringExtra("productId");
        String productName = getIntent().getStringExtra("product_name");
        float avgRating = getIntent().getFloatExtra("avgRating", 0f);
        int reviewCountExtra = getIntent().getIntExtra("reviewCount", -1);

        if (productName != null) {
            binding.tvToolbarTitle.setText("Đánh giá " + productName);
        }

        if (avgRating > 0) {
            updateSummary(avgRating, reviewCountExtra >= 0 ? reviewCountExtra : 0);
        }

        binding.btnBack.setOnClickListener(v -> finish());

        setupList();
        setupFilterChips();
        setupSearch();

        if (productId != null) {
            fetchReviewsFromFirestore();
        } else {
            binding.tvNoReviews.setVisibility(View.VISIBLE);
        }
    }

    private void setupList() {
        reviewAdapter = new ProductReviewEntryAdapter(reviewList);
        binding.rvReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReviews.setAdapter(reviewAdapter);
    }

    private void setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
    }

    private void setupSearch() {
        binding.etSearchReview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
    }

    private void fetchReviewsFromFirestore() {
        FirestoreManager.getInstance().getProductsCollection()
                .document(productId)
                .collection("reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allReviews.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Review r = doc.toObject(Review.class);
                        if (r != null) allReviews.add(r);
                    }

                    // Nếu không truyền avgRating/reviewCount từ trang chi tiết -> tự tính từ dữ liệu thật
                    if (getIntent().getFloatExtra("avgRating", 0f) <= 0 && !allReviews.isEmpty()) {
                        float sum = 0;
                        for (Review r : allReviews) sum += r.getRating();
                        updateSummary(sum / allReviews.size(), allReviews.size());
                    }

                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    binding.tvNoReviews.setVisibility(View.VISIBLE);
                });
    }

    private void updateSummary(float avgRating, int totalCount) {
        binding.tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
        binding.ratingBarAvg.setRating(avgRating);
        binding.tvTotalReviews.setText(totalCount + " đánh giá");
    }

    // FIX: lọc theo chip đang chọn (sao / có hình ảnh) + từ khóa tìm kiếm trong nội dung comment
    private void applyFilters() {
        String keyword = binding.etSearchReview.getText().toString().trim().toLowerCase(Locale.getDefault());
        int checkedChipId = binding.chipGroupFilter.getCheckedChipId();

        reviewList.clear();

        for (Review r : allReviews) {
            if (!matchesChip(r, checkedChipId)) continue;
            if (!keyword.isEmpty() && (r.getComment() == null
                    || !r.getComment().toLowerCase(Locale.getDefault()).contains(keyword))) continue;

            reviewList.add(r);
        }

        reviewAdapter.notifyDataSetChanged();
        binding.tvNoReviews.setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesChip(Review r, int checkedChipId) {
        if (checkedChipId == binding.chipHasImage.getId()) {
            return r.getMediaUris() != null && !r.getMediaUris().isEmpty();
        } else if (checkedChipId == binding.chip5star.getId()) {
            return Math.round(r.getRating()) == 5;
        } else if (checkedChipId == binding.chip4star.getId()) {
            return Math.round(r.getRating()) == 4;
        } else if (checkedChipId == binding.chip3star.getId()) {
            return Math.round(r.getRating()) == 3;
        } else if (checkedChipId == binding.chip2star.getId()) {
            return Math.round(r.getRating()) == 2;
        } else if (checkedChipId == binding.chip1star.getId()) {
            return Math.round(r.getRating()) == 1;
        }
        // chip_all hoặc không chọn gì -> hiện tất cả
        return true;
    }
}
