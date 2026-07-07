package com.example.healthup;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityViewReviewsBinding;
import com.example.healthup.databinding.ItemViewReviewBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.Review;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class ViewReviewsActivity extends AppCompatActivity {
    private ActivityViewReviewsBinding binding;
    private Order order;
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất nội dung
        View root = findViewById(R.id.view_reviews_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        order = (Order) getIntent().getSerializableExtra("order");
        if (order == null) {
            finish();
            return;
        }


        setupUI();
        binding.btnBack.setOnClickListener(v -> finish());
    }


    private void setupUI() {
        binding.lnReviewContainer.removeAllViews();
        for (OrderItem item : order.getItems()) {
            ItemViewReviewBinding itemBinding = ItemViewReviewBinding.inflate(getLayoutInflater(), binding.lnReviewContainer, false);


            itemBinding.tvProductName.setText(item.getName());
            String variantLabel = item.getVariantLabel();
            if (!TextUtils.isEmpty(variantLabel)) {
                itemBinding.tvVariant.setVisibility(View.VISIBLE);
                itemBinding.tvVariant.setText("Phân loại: " + variantLabel);
            } else {
                itemBinding.tvVariant.setVisibility(View.GONE);
            }

            // Click product image or name to see product details
            View.OnClickListener toProductDetail = v -> {
                if (item.getProductId() != null) {
                    Intent detailIntent = new Intent(this, ProductDetailActivity.class);
                    detailIntent.putExtra("productId", item.getProductId());
                    startActivity(detailIntent);
                }
            };
            itemBinding.imgContainer.setOnClickListener(toProductDetail);
            itemBinding.tvProductName.setOnClickListener(toProductDetail);

            String imagePath = item.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Object loadTarget;


                if (cleanPath.startsWith("images/")) {
                    loadTarget = "file:///android_asset/" + cleanPath;
                } else if (imagePath.startsWith("http")) {
                    loadTarget = imagePath;
                } else {
                    loadTarget = "file:///android_asset/images/products/" + cleanPath;
                }


                Glide.with(this)
                        .load(loadTarget)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(itemBinding.imgProduct);
            } else {
                itemBinding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
            }


            Review review = item.getReview();
            if (review != null) {
                itemBinding.lnReviewedContent.setVisibility(View.VISIBLE);
                itemBinding.btnWriteReviewNow.setVisibility(View.GONE);

                itemBinding.ratingBar.setRating(review.getRating());
                itemBinding.tvComment.setText(review.getComment());
                if (review.getComment() == null || review.getComment().isEmpty()) {
                    itemBinding.tvComment.setVisibility(View.GONE);
                }

                if (review.getCreatedAt() != null) {
                    itemBinding.tvReviewTime.setText("Đã đánh giá vào: " + sdf.format(review.getCreatedAt().toDate()));
                }


                if (review.getMediaUris() != null && !review.getMediaUris().isEmpty()) {
                    List<Uri> uris = new ArrayList<>();
                    for (String s : review.getMediaUris()) uris.add(Uri.parse(s));

                    MediaAdapter adapter = new MediaAdapter(uris, null);
                    adapter.setViewOnly(true);
                    itemBinding.rvMedia.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                    itemBinding.rvMedia.setAdapter(adapter);
                } else {
                    itemBinding.rvMedia.setVisibility(View.GONE);
                }

                // Link to all reviews for this product
                itemBinding.btnViewAllReviews.setOnClickListener(v -> {
                    if (item.getProductId() != null) {
                        Intent reviewsIntent = new Intent(this, ProductReviewsActivity.class);
                        reviewsIntent.putExtra("productId", item.getProductId());
                        reviewsIntent.putExtra("product_name", item.getName());
                        startActivity(reviewsIntent);
                    }
                });
            } else {
                itemBinding.lnReviewedContent.setVisibility(View.GONE);
                itemBinding.btnWriteReviewNow.setVisibility(View.VISIBLE);
                itemBinding.btnWriteReviewNow.setOnClickListener(v -> {
                    Intent intent = new Intent(this, WriteReviewActivity.class);
                    intent.putExtra("order", order);
                    startActivity(intent);
                    finish();
                });
            }


            binding.lnReviewContainer.addView(itemBinding.getRoot());
        }
    }
}