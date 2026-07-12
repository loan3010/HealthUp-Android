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
import com.example.healthup.util.ReviewStatsHelper;
import com.example.models.Review;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class ProductReviewsActivity extends AppCompatActivity {
    private ActivityProductReviewsBinding binding;
    private String productId;
    private ProductReviewEntryAdapter reviewAdapter;

    private final List<Review> allReviews = new ArrayList<>();
    private final List<Review> reviewList = new ArrayList<>();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


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
        float avgRating = getIntent().getFloatExtra("avgRating", 0f);
        int reviewCountExtra = getIntent().getIntExtra("reviewCount", -1);

        // FIX (yêu cầu): tiêu đề trang chỉ cần "Đánh giá" (ngắn gọn), không hiển thị
        // lại đầy đủ tên sản phẩm nữa — giữ nguyên giá trị mặc định đã đặt sẵn trong XML,
        // không override bằng tên sản phẩm như trước.


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
        binding.btnClearSearchReview.setOnClickListener(v -> binding.etSearchReview.setText(""));
        binding.tvCancelSearchReview.setOnClickListener(v -> {
            binding.etSearchReview.setText("");
            binding.etSearchReview.clearFocus();
            // Ẩn bàn phím
            View view = this.getCurrentFocus();
            if (view != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        });

        binding.etSearchReview.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                applyFilters();
                binding.etSearchReview.clearFocus();
                return true;
            }
            return false;
        });


        binding.etSearchReview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}


            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                binding.btnClearSearchReview.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                binding.tvCancelSearchReview.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }


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
                .get() // Bỏ orderBy để lấy toàn bộ, kể cả đơn cũ thiếu field createdAt
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allReviews.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Review r = doc.toObject(Review.class);
                        if (r != null) {
                            // Nếu thiếu createdAt thì lấy thời gian doc được tạo (nếu có) hoặc mặc định
                            if (r.getCreatedAt() == null) {
                                // Gán tạm thời gian hiện tại hoặc từ date String
                                // r.setCreatedAt(...) 
                            }
                            allReviews.add(r);
                        }
                    }

                    // Sắp xếp thủ công ở Client để tránh lỗi missing field ở Firestore query
                    allReviews.sort((r1, r2) -> {
                        long t1 = r1.getCreatedAt() != null ? r1.getCreatedAt().getTime() : 0;
                        long t2 = r2.getCreatedAt() != null ? r2.getCreatedAt().getTime() : 0;
                        return Long.compare(t2, t1); // Mới nhất lên đầu
                    });

                    ReviewStatsHelper.Stats stats = ReviewStatsHelper.computeFromSnapshot(queryDocumentSnapshots);
                    updateSummary(stats.avgRating, stats.count);

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
        return true;
    }
}