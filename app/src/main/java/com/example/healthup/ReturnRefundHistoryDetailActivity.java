package com.example.healthup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundHistoryDetailBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.ItemTimelineStepBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReturnRefundHistoryDetailActivity extends AppCompatActivity {
    private ActivityReturnRefundHistoryDetailBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private Order order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundHistoryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        order = (Order) getIntent().getSerializableExtra("order");
        String orderIdFallback = getIntent().getStringExtra("extra_order_id");

        if (order != null && order.getUpdatedAt() != null) {
            populateUI();
        } else {
            String orderId = (order != null) ? order.getId() : orderIdFallback;
            if (orderId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("orders")
                        .document(orderId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                order = doc.toObject(Order.class);
                                if (order != null) {
                                    order.setId(doc.getId());
                                    populateUI();
                                }
                            }
                        });
            }
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.rowChat.setOnClickListener(v ->
                OrderChatHelper.openOrderChat(this, order, binding.tvOrderCode.getText().toString()));
    }

    private void populateUI() {
        String handling = order.getReturnHandling() != null ? order.getReturnHandling() : "Trả hàng & Hoàn tiền";
        boolean isReship = handling.contains("Nhận bổ sung");
        
        // Status Badge Logic
        String paymentStatus = order.getPaymentStatus();
        boolean isCompleted = "refunded".equalsIgnoreCase(paymentStatus) || "reshipped".equalsIgnoreCase(paymentStatus);
        
        if (isCompleted) {
            binding.tvStatusBanner.setText(isReship ? "ĐÃ GỬI HÀNG BÙ" : "ĐÃ HOÀN TIỀN");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_delivered);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else {
            binding.tvStatusBanner.setText("ĐANG XỬ LÝ");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        }

        // Timeline
        setupTimeline(isCompleted, isReship);

        // Request Info
        binding.tvRequestType.setText(handling);
        binding.tvReason.setText(order.getReturnReason() != null ? order.getReturnReason() : "Hàng bị lỗi/hư hỏng");
        
        String desc = order.getReturnDescription();
        binding.tvDescription.setText(desc != null && !desc.isEmpty() ? desc : "Bạn chưa cung cấp mô tả.");
        
        if (isReship) {
            binding.lnRefundAmountRow.setVisibility(View.GONE);
            binding.lnRefundMethodRow.setVisibility(View.GONE);
            binding.lnShippingAddressRow.setVisibility(View.VISIBLE);
            binding.tvShippingAddress.setText(order.getAddress().getAddressDetail());
        } else {
            binding.lnRefundAmountRow.setVisibility(View.VISIBLE);
            binding.lnRefundMethodRow.setVisibility(View.VISIBLE);
            binding.lnShippingAddressRow.setVisibility(View.GONE);
            
            binding.tvRefundAmount.setText(df.format(order.getTotalPrice()));
            binding.tvRefundMethod.setText(order.getPaymentMethod());
            if (order.getPaymentMethod().contains("Thẻ") || order.getPaymentMethod().contains("Tài khoản")) {
                binding.imgRefundMethod.setImageResource(R.drawable.ic_payment_card);
            } else {
                binding.imgRefundMethod.setImageResource(R.drawable.ic_payment_wallet);
            }
        }

        // Evidence
        List<String> mediaStrings = order.getReturnMediaUris();
        if (mediaStrings != null && !mediaStrings.isEmpty()) {
            List<Uri> mediaUris = new ArrayList<>();
            for (String s : mediaStrings) mediaUris.add(Uri.parse(s));
            
            MediaAdapter mediaAdapter = new MediaAdapter(mediaUris, null);
            mediaAdapter.setViewOnly(true);
            binding.rvEvidence.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            binding.rvEvidence.setAdapter(mediaAdapter);
        }

        // Order Info
        binding.tvOrderCode.setText(order.getOrderCode());
        binding.btnCopyOrderCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Order Code", order.getOrderCode());
            clipboard.setPrimaryClip(clip);
        });

        // Items
        binding.lnItemsContainer.removeAllViews();
        for (OrderItem item : order.getItems()) {
            ItemOrderProductBinding pBinding = ItemOrderProductBinding.inflate(getLayoutInflater(), binding.lnItemsContainer, false);
            pBinding.tvProductName.setText(item.getName());
            pBinding.tvVariant.setText(item.getVariantLabel());
            pBinding.tvPrice.setText(df.format(item.getPrice()));
            pBinding.tvQuantity.setText("x" + item.getQuantity());

            if (item.getOriginalPrice() > item.getPrice() && item.getOriginalPrice() > 0) {
                pBinding.tvPriceOld.setVisibility(View.VISIBLE);
                pBinding.tvPriceOld.setText(df.format(item.getOriginalPrice()));
                pBinding.tvPriceOld.setPaintFlags(pBinding.tvPriceOld.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                pBinding.tvPriceOld.setVisibility(View.GONE);
            }

            // Xử lý hiển thị ảnh sản phẩm từ assets hoặc URL
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
                        .into(pBinding.imgProduct);
            } else {
                pBinding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
            }

            pBinding.btnAskProduct.setOnClickListener(v ->
                    OrderChatHelper.openProductChat(
                            this, order, binding.tvOrderCode.getText().toString(), item));

            binding.lnItemsContainer.addView(pBinding.getRoot());
        }
    }

    private void setupTimeline(boolean isCompleted, boolean isReship) {
        binding.lnTimeline.removeAllViews();
        long updatedAtMs = (order.getUpdatedAt() != null) ? order.getUpdatedAt().getSeconds() * 1000 : System.currentTimeMillis();
        long day = 86400000L;

        addTimelineStep("Yêu cầu đã được gửi", sdf.format(new Date(updatedAtMs - day * 2)));
        addTimelineStep("HealthUp đã duyệt yêu cầu", sdf.format(new Date(updatedAtMs - day - 3600000 * 5)));
        
        if (isCompleted) {
            if (isReship) {
                addTimelineStep("Đang chuẩn bị hàng gửi bù", sdf.format(new Date(updatedAtMs - 3600000 * 4)));
                addTimelineStep("Đã gửi hàng bổ sung thành công", sdf.format(new Date(updatedAtMs)), true);
            } else {
                addTimelineStep("Đã nhận lại hàng trả", sdf.format(new Date(updatedAtMs - 3600000 * 2)));
                addTimelineStep("Hoàn tiền thành công", sdf.format(new Date(updatedAtMs)) + " – qua " + order.getPaymentMethod(), true);
            }
        } else {
            String pendingStep = isReship ? "Đang chuẩn bị hàng gửi bù" : "Đang kiểm tra hàng trả";
            addTimelineStep(pendingStep, "Đang xử lý...", true);
        }
    }

    private void addTimelineStep(String title, String time) {
        addTimelineStep(title, time, false);
    }

    private void addTimelineStep(String title, String time, boolean isLast) {
        ItemTimelineStepBinding stepBinding = ItemTimelineStepBinding.inflate(getLayoutInflater(), binding.lnTimeline, false);
        stepBinding.tvStepTitle.setText(title);
        stepBinding.tvStepTime.setText(time);
        
        if (isLast) {
            stepBinding.viewLine.setVisibility(View.GONE);
        }
        
        binding.lnTimeline.addView(stepBinding.getRoot());
    }
}
