package com.group.healthup;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.group.healthup.databinding.ActivityProductReviewsBinding;
import com.group.models.Review;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProductReviewsActivity extends AppCompatActivity {
    private ActivityProductReviewsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String productName = getIntent().getStringExtra("product_name");
        if (productName != null) {
            binding.tvToolbarTitle.setText("Đánh giá " + productName);
        }

        binding.btnBack.setOnClickListener(v -> finish());

        setupList();
    }

    private void setupList() {
        // Mocking reviews for this product
        List<Review> mockReviews = new ArrayList<>();
        mockReviews.add(new Review(5, "Sản phẩm tuyệt vời, rất đáng mua!", null, new Timestamp(new Date(System.currentTimeMillis() - 86400000L))));
        mockReviews.add(new Review(4, "Giao hàng nhanh, đóng gói đẹp.", null, new Timestamp(new Date(System.currentTimeMillis() - 86400000L * 2))));
        mockReviews.add(new Review(5, "Hạt rất giòn và ngon.", null, new Timestamp(new Date(System.currentTimeMillis() - 86400000L * 3))));

        binding.rvReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReviews.setAdapter(new ProductReviewEntryAdapter(mockReviews));
    }
}
