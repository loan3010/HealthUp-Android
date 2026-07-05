package com.example.healthup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.Date;
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
            itemBinding.tvVariant.setText(item.getVariantLabel());
            Glide.with(this).load(item.getImageUrl()).placeholder(R.drawable.ic_launcher_background).into(itemBinding.imgProduct);

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
            } else {
                itemBinding.lnReviewedContent.setVisibility(View.GONE);
                itemBinding.btnWriteReviewNow.setVisibility(View.VISIBLE);
                itemBinding.btnWriteReviewNow.setOnClickListener(v -> {
                    Intent intent = new Intent(this, WriteReviewActivity.class);
                    intent.putExtra("order", order);
                    startActivity(intent);
                    finish(); // Usually better to finish and let them re-enter or refresh
                });
            }

            binding.lnReviewContainer.addView(itemBinding.getRoot());
        }
    }
}
