package com.example.healthup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundHistoryDetailBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.ItemTimelineStepBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
    private int currentStep = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundHistoryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất nội dung
        View root = findViewById(R.id.return_refund_history_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        order = (Order) getIntent().getSerializableExtra("order");
        String orderIdFallback = getIntent().getStringExtra("extra_order_id");

        if (order != null) {
            currentStep = order.getReturnStep() > 0 ? order.getReturnStep() : 1;
            populateUI();
        } else {
            String orderId = orderIdFallback;
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
                                    currentStep = order.getReturnStep() > 0 ? order.getReturnStep() : 1;
                                    populateUI();
                                }
                            }
                        });
            }
        }

        binding.btnBack.setOnClickListener(v -> finish());
        setupSupportAndContactListeners();
        
        // Cho phép bấm vào vùng tiến trình để giả lập bước tiếp theo (Demo mode)
        binding.lnTimeline.setOnClickListener(v -> advanceStepDemo());
    }

    private void setupSupportAndContactListeners() {
        binding.rowFAQ.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigate_to", "faq");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        binding.rowChat.setOnClickListener(v -> {
            if (order != null) {
                showContactOptions(order);
            }
        });

        binding.rowContactPhone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(android.net.Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        binding.rowContactEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:healthup@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Hỗ trợ yêu cầu trả hàng đơn #" + (order != null ? order.getOrderCode() : ""));
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, "Không tìm thấy ứng dụng email", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showContactOptions(Order order) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding dialogBinding = 
            com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.btnChat.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(ChatActivity.buyerIntentForOrder(this, order.getOrderCode(), order.getId()));
        });

        dialogBinding.btnCall.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(android.net.Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void advanceStepDemo() {
        String handling = order.getReturnHandling() != null ? order.getReturnHandling() : "Trả hàng & Hoàn tiền";
        int maxStep = handling.equals("Trả hàng & Hoàn tiền") ? 4 : 3;

        if (currentStep < maxStep) {
            final int nextStep = currentStep + 1;
            final boolean isFinal = (nextStep == maxStep);
            
            FirebaseManager.getInstance().advanceReturnStep(order.getId(), nextStep, isFinal)
                    .addOnSuccessListener(aVoid -> {
                        currentStep = nextStep;
                        order.setReturnStep(currentStep);
                        if (isFinal) {
                            order.setStatus("completed");
                        }
                        populateUI();
                    });
        }
    }

    private void populateUI() {
        String handling = order.getReturnHandling() != null ? order.getReturnHandling() : "Trả hàng & Hoàn tiền";
        boolean isReship = handling.contains("Nhận bổ sung");
        
        // Cập nhật thẻ trạng thái ở góc phải (Banner)
        int maxStep = handling.equals("Trả hàng & Hoàn tiền") ? 4 : 3;
        boolean isCompleted = currentStep >= maxStep || "completed".equalsIgnoreCase(order.getStatus());
        
        if (isCompleted) {
            binding.tvStatusBanner.setText("ĐÃ HOÀN THÀNH");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_delivered);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else {
            binding.tvStatusBanner.setText("ĐANG XỬ LÝ");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        }

        // Timeline
        setupTimeline(currentStep, handling);

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
            
            String displayRefundMethod = getDisplayRefundMethod(order.getPaymentMethod());
            binding.tvRefundAmount.setText(df.format(order.getTotalPrice()));
            binding.tvRefundMethod.setText(displayRefundMethod);
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

            // Click product image or name to see product details
            View.OnClickListener toProductDetail = v -> {
                if (item.getProductId() != null) {
                    Intent detailIntent = new Intent(this, ProductDetailActivity.class);
                    detailIntent.putExtra("productId", item.getProductId());
                    startActivity(detailIntent);
                }
            };
            pBinding.imgProduct.setOnClickListener(toProductDetail);
            pBinding.tvProductName.setOnClickListener(toProductDetail);
        }
    }

    private void setupTimeline(int currentStep, String handling) {
        binding.lnTimeline.removeAllViews();
        long updatedAtMs = (order.getUpdatedAt() != null) ? order.getUpdatedAt().getTime() : System.currentTimeMillis();
        long hour = 3600000L;
        long day = 86400000L;

        String refundMethodDisplay = getDisplayRefundMethod(order.getPaymentMethod());

        // Bước 0 & 1: Luôn màu xanh (Vì gửi và duyệt tự động)
        addTimelineStep("Yêu cầu đã được gửi", sdf.format(new Date(updatedAtMs - day)), false, false);
        addTimelineStep("HealthUp đã duyệt yêu cầu", sdf.format(new Date(updatedAtMs - day + hour)), false, false);

        if (handling.equals("Trả hàng & Hoàn tiền")) {
            // maxStep = 4. Quy trình: 1 (Duyệt) -> 2 (Chờ gửi) -> 3 (Kiểm tra) -> 4 (Hoàn tiền xong)
            
            // Bước 2: Chờ gửi hàng
            if (currentStep >= 2) {
                addTimelineStep("Đang chờ khách hàng gửi trả hàng", 
                    currentStep == 2 ? "Đang xử lý..." : sdf.format(new Date(updatedAtMs - hour * 5)), 
                    currentStep == 2, false);
            }
            // Bước 3: Kiểm tra hàng
            if (currentStep >= 3) {
                addTimelineStep("Đang kiểm tra hàng trả", 
                    currentStep == 3 ? "Đang xử lý..." : sdf.format(new Date(updatedAtMs - hour * 2)), 
                    currentStep == 3, false);
            }
            // Bước 4: Hoàn tiền thành công
            if (currentStep >= 4) {
                addTimelineStep("Hoàn tiền thành công", 
                    sdf.format(new Date(updatedAtMs)) + " – qua " + refundMethodDisplay, 
                    false, true);
            }
        } else if (handling.equals("Hoàn tiền sản phẩm bị thiếu")) {
            // maxStep = 3. Quy trình: 1 (Duyệt) -> 2 (Đang xử lý hoàn tiền) -> 3 (Hoàn tiền xong)
            
            // Bước 2: Đang xử lý hoàn tiền
            if (currentStep >= 2) {
                addTimelineStep("Đang xử lý hoàn tiền", 
                    currentStep == 2 ? "Đang xử lý..." : sdf.format(new Date(updatedAtMs - hour * 3)), 
                    currentStep == 2, false);
            }
            // Bước 3: Hoàn tiền thành công
            if (currentStep >= 3) {
                addTimelineStep("Hoàn tiền thành công", 
                    sdf.format(new Date(updatedAtMs)) + " – qua " + refundMethodDisplay,
                    false, true);
            }
        } else if (handling.contains("bổ sung")) {
            // maxStep = 3. Quy trình: 1 (Duyệt) -> 2 (Chuẩn bị hàng bù) -> 3 (Gửi bù xong)
            
            // Bước 2: Chuẩn bị hàng bù
            if (currentStep >= 2) {
                addTimelineStep("Đang chuẩn bị hàng gửi bù", 
                    currentStep == 2 ? "Đang xử lý..." : sdf.format(new Date(updatedAtMs - hour * 4)), 
                    currentStep == 2, false);
            }
            // Bước 3: Gửi bù thành công
            if (currentStep >= 3) {
                addTimelineStep("Đã gửi hàng bổ sung thành công", 
                    sdf.format(new Date(updatedAtMs)), 
                    false, true);
            }
        }
    }

    private void addTimelineStep(String title, String time, boolean isPending, boolean isLast) {
        ItemTimelineStepBinding stepBinding = ItemTimelineStepBinding.inflate(getLayoutInflater(), binding.lnTimeline, false);
        stepBinding.tvStepTitle.setText(title);
        stepBinding.tvStepTime.setText(time);
        
        if (isPending) {
            stepBinding.imgStepIndicator.setImageResource(R.drawable.ic_pending_circle);
            stepBinding.imgStepIndicator.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_hint)));
            stepBinding.viewLine.setBackgroundColor(getResources().getColor(R.color.border_color));
            stepBinding.tvStepTitle.setTextColor(getResources().getColor(R.color.text_hint));
        } else {
            stepBinding.imgStepIndicator.setImageResource(R.drawable.ic_check_circle);
            stepBinding.imgStepIndicator.setImageTintList(null); // Green from drawable
            stepBinding.viewLine.setBackgroundColor(getResources().getColor(R.color.primary));
            stepBinding.tvStepTitle.setTextColor(getResources().getColor(R.color.text_main));
        }
        
        if (isLast) {
            stepBinding.viewLine.setVisibility(View.GONE);
        }
        
        binding.lnTimeline.addView(stepBinding.getRoot());
    }

    private String getDisplayRefundMethod(String paymentMethod) {
        if (paymentMethod == null) return "Phương thức đã chọn";
        String pm = paymentMethod.toLowerCase();
        if (pm.contains("cod") || pm.contains("nhận hàng")) {
            return "Tài khoản Ngân hàng liên kết";
        } else if (pm.contains("momo")) {
            return "Ví MoMo";
        } else if (pm.contains("zalopay")) {
            return "Ví ZaloPay";
        } else if (pm.contains("vnpay")) {
            return "Ví VNPAY";
        } else if (pm.contains("card") || pm.contains("thẻ") || pm.contains("tài khoản")) {
            return "Thẻ Tín dụng / Ghi nợ";
        }
        return paymentMethod;
    }
}
