package com.example.healthup;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundBinding;
import com.example.models.OrderItem;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class ReturnRefundActivity extends AppCompatActivity {
    private ActivityReturnRefundBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String orderId = getIntent().getStringExtra("orderId");
        String orderCode = getIntent().getStringExtra("orderCode");
        String paymentMethod = getIntent().getStringExtra("paymentMethod");
        String shippingAddress = getIntent().getStringExtra("shippingAddress");
        ArrayList<OrderItem> items = (ArrayList<OrderItem>) getIntent().getSerializableExtra("items");

        if (orderCode != null) {
            binding.tvOrderCodeLabel.setText("Mã đơn hàng: " + orderCode);
        } else if (orderId != null) {
            binding.tvOrderCodeLabel.setText("Mã đơn hàng: " + orderId.substring(0, 8));
        }

        if (items != null && !items.isEmpty()) {
            OrderItem firstItem = items.get(0);
            binding.tvProductName.setText(firstItem.getName());
            binding.tvVariant.setText(firstItem.getVariantLabel());
            binding.tvPrice.setText(df.format(firstItem.getPrice()));
            binding.tvQuantity.setText("x" + firstItem.getQuantity());
            Glide.with(this).load(firstItem.getImageUrl()).placeholder(R.drawable.ic_launcher_background).into(binding.imgProduct);
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
