package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundBinding;
import com.example.models.OrderItem;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class ReturnRefundActivity extends BaseAppCompatActivity {
    private ActivityReturnRefundBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất nội dung
        View root = findViewById(R.id.return_refund_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        String orderId = getIntent().getStringExtra("orderId");
        String orderCode = getIntent().getStringExtra("orderCode");
        String paymentMethod = getIntent().getStringExtra("paymentMethod");
        String shippingAddress = getIntent().getStringExtra("shippingAddress");
        ArrayList<OrderItem> items = (ArrayList<OrderItem>) getIntent().getSerializableExtra("items");

        if (orderCode != null && !orderCode.isEmpty()) {
            binding.tvOrderCodeLabel.setText("Mã đơn hàng: " + orderCode);
        } else if (orderId != null && !orderId.isEmpty()) {
            // Fallback if code is missing, use ID but maybe indicate it
            binding.tvOrderCodeLabel.setText("Đơn hàng: " + orderId);
        }

        if (items != null && !items.isEmpty()) {
            OrderItem firstItem = items.get(0);
            binding.tvProductName.setText(firstItem.getName());
            binding.tvVariant.setText(firstItem.getVariantLabel());
            binding.tvPrice.setText(df.format(firstItem.getPrice()));
            binding.tvQuantity.setText("x" + firstItem.getQuantity());
            
            // Xử lý hiển thị ảnh sản phẩm từ assets hoặc URL
            String imagePath = firstItem.getImageUrl();
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
                        .into(binding.imgProduct);
            } else {
                binding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
            }
            
            // Click product image or name to see product details
            View.OnClickListener toProductDetail = v -> {
                if (items != null && !items.isEmpty() && items.get(0).getProductId() != null) {
                    android.content.Intent detailIntent = new android.content.Intent(this, ProductDetailActivity.class);
                    detailIntent.putExtra("productId", items.get(0).getProductId());
                    startActivity(detailIntent);
                }
            };
            binding.imgProduct.setOnClickListener(toProductDetail);
            binding.tvProductName.setOnClickListener(toProductDetail);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.cvWrongItem.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ReturnRefundDetailActivity.class);
            intent.putExtra("items", items);
            intent.putExtra("orderId", orderId);
            intent.putExtra("orderCode", orderCode);
            intent.putExtra("paymentMethod", paymentMethod);
            intent.putExtra("shippingAddress", shippingAddress);
            startActivity(intent);
        });

        binding.cvMissingItem.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ReturnRefundDetailActivity.class);
            intent.putExtra("items", items);
            intent.putExtra("orderId", orderId);
            intent.putExtra("orderCode", orderCode);
            intent.putExtra("paymentMethod", paymentMethod);
            intent.putExtra("shippingAddress", shippingAddress);
            intent.putExtra("reason", "Thiếu hàng");
            startActivity(intent);
        });
    }
}
