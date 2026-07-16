package com.example.healthup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundHistoryDetailBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.ItemTimelineStepBinding;
import com.example.healthup.util.FullscreenImagePager;
import com.example.healthup.util.OrderSeenManager;
import com.example.healthup.util.ReturnProgressHelper;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.PaymentAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReturnRefundHistoryDetailActivity extends BaseAppCompatActivity {
    private ActivityReturnRefundHistoryDetailBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private Order order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundHistoryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            populateUI();
        } else if (orderIdFallback != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("orders")
                    .document(orderIdFallback)
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

        binding.btnBack.setOnClickListener(v -> finish());
        setupSupportAndContactListeners();
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
            intent.setData(Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        binding.rowContactEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:healthup@gmail.com"));
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
            intent.setData(Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
    }

    private void populateUI() {
        // Mark any return status as seen when opened
        if (order.getReturnStatus() != null && !"none".equalsIgnoreCase(order.getReturnStatus())) {
            OrderSeenManager.markAsSeen(this, order.getId() + "_" + order.getReturnStatus());
        }

        String handling = ReturnProgressHelper.normalizeHandling(order.getReturnHandling());
        boolean isReship = ReturnProgressHelper.isReship(handling);
        boolean isRejected = Order.RETURN_REJECTED.equals(order.getReturnStatus());
        int maxStep = ReturnProgressHelper.maxStep(handling);
        int step = Math.max(0, order.getReturnStep());
        boolean isCompleted = !isRejected && (
                step >= maxStep
                        || Order.RETURN_COMPLETED.equals(order.getReturnStatus())
                        || "completed".equalsIgnoreCase(order.getStatus()));

        if (isRejected) {
            binding.tvStatusBanner.setText("KHÔNG THÀNH CÔNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_cancelled);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else if (isCompleted) {
            binding.tvStatusBanner.setText("ĐÃ HOÀN THÀNH");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_delivered);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else {
            binding.tvStatusBanner.setText("ĐANG XỬ LÝ");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        }

        setupTimeline(handling, step, isRejected, isCompleted);

        binding.tvRequestType.setText(handling);
        binding.tvReason.setText(order.getReturnReason() != null ? order.getReturnReason() : "Hàng bị lỗi/hư hỏng");

        String desc = order.getReturnDescription();
        binding.tvDescription.setText(desc != null && !desc.isEmpty() ? desc : "Bạn chưa cung cấp mô tả.");

        if (isRejected && order.getReturnRejectReason() != null && !order.getReturnRejectReason().isEmpty()) {
            binding.lnRejectReasonRow.setVisibility(View.VISIBLE);
            binding.tvRejectReason.setText(order.getReturnRejectReason());
        } else {
            binding.lnRejectReasonRow.setVisibility(View.GONE);
        }

        // Rejected: hide refund success fields. Reship: address instead of refund.
        if (isRejected) {
            binding.dividerRefundSection.setVisibility(View.GONE);
            binding.lnRefundAmountRow.setVisibility(View.GONE);
            binding.lnRefundMethodRow.setVisibility(View.GONE);
            binding.lnShippingAddressRow.setVisibility(View.GONE);
        } else if (isReship) {
            binding.dividerRefundSection.setVisibility(View.VISIBLE);
            binding.lnRefundAmountRow.setVisibility(View.GONE);
            binding.lnRefundMethodRow.setVisibility(View.GONE);
            binding.lnShippingAddressRow.setVisibility(View.VISIBLE);
            if (order.getAddress() != null) {
                binding.tvShippingAddress.setText(order.getAddress().getAddressDetail());
            }
        } else {
            binding.dividerRefundSection.setVisibility(View.VISIBLE);
            binding.lnRefundAmountRow.setVisibility(View.VISIBLE);
            binding.lnRefundMethodRow.setVisibility(View.VISIBLE);
            binding.lnShippingAddressRow.setVisibility(View.GONE);
            binding.tvRefundAmount.setText(df.format(order.getTotalPrice()));
            fetchRealRefundDetailAndPopulate();
        }

        List<String> mediaStrings = order.getReturnMediaUris();
        if (mediaStrings != null && !mediaStrings.isEmpty()) {
            List<Uri> mediaUris = new ArrayList<>();
            List<String> urlList = new ArrayList<>();
            for (String s : mediaStrings) {
                if (s == null || s.trim().isEmpty()) continue;
                mediaUris.add(Uri.parse(s));
                urlList.add(s);
            }
            MediaAdapter mediaAdapter = new MediaAdapter(mediaUris, null);
            mediaAdapter.setViewOnly(true);
            mediaAdapter.setOnImageClickListener((position, uri) ->
                    FullscreenImagePager.show(this, urlList, position));
            binding.rvEvidence.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            binding.rvEvidence.setAdapter(mediaAdapter);
        }

        binding.tvOrderCode.setText(order.getOrderCode());
        binding.btnCopyOrderCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Order Code", order.getOrderCode());
            clipboard.setPrimaryClip(clip);
        });

        binding.lnItemsContainer.removeAllViews();
        if (order.getItems() == null) return;
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

    private void setupTimeline(String handling, int step, boolean isRejected, boolean isCompleted) {
        binding.lnTimeline.removeAllViews();
        long updatedAtMs = (order.getUpdatedAt() != null) ? order.getUpdatedAt().getTime() : System.currentTimeMillis();
        long hour = 3600000L;
        long day = 86400000L;
        String refundMethodDisplay = getDisplayRefundMethod(order.getPaymentMethod());
        int maxStep = ReturnProgressHelper.maxStep(handling);
        if (isCompleted) {
            step = maxStep;
        }

        // Always: request submitted
        addTimelineStep("Yêu cầu đã được gửi",
                sdf.format(new Date(updatedAtMs - day)), false, false);

        if (isRejected) {
            String rejectTime = sdf.format(new Date(updatedAtMs));
            String reason = order.getReturnRejectReason();
            String subtitle = reason != null && !reason.isEmpty()
                    ? rejectTime + " – " + reason
                    : rejectTime;
            addTimelineStep("Yêu cầu bị từ chối", subtitle, false, true);
            return;
        }

        // Pending approval
        if (Order.RETURN_REQUESTED.equals(order.getReturnStatus()) && step < 1) {
            addTimelineStep("Chờ HealthUp duyệt yêu cầu", "Đang chờ...", true, true);
            return;
        }

        // Approved
        addTimelineStep("HealthUp đã duyệt yêu cầu",
                sdf.format(new Date(updatedAtMs - day + hour)), false, step < 2 && !isCompleted);

        if (step < 2 && !isCompleted) {
            return;
        }

        for (int s = 2; s <= maxStep; s++) {
            boolean reached = step >= s;
            boolean isCurrent = !isCompleted && step == s;
            boolean isLast = s == maxStep;
            if (!reached && !isCurrent) break;

            String title = ReturnProgressHelper.stepTitle(handling, s);
            String time;
            if (isCurrent) {
                time = "Đang xử lý...";
            } else if (s == maxStep && step >= maxStep) {
                if (ReturnProgressHelper.isReship(handling)) {
                    time = sdf.format(new Date(updatedAtMs));
                } else {
                    time = sdf.format(new Date(updatedAtMs)) + " – qua " + refundMethodDisplay;
                }
            } else {
                time = sdf.format(new Date(updatedAtMs - hour * (maxStep - s + 1)));
            }
            addTimelineStep(title, time, isCurrent, isLast && step >= maxStep);
            if (isCurrent) break;
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
            stepBinding.imgStepIndicator.setImageTintList(null);
            stepBinding.viewLine.setBackgroundColor(getResources().getColor(R.color.primary));
            stepBinding.tvStepTitle.setTextColor(getResources().getColor(R.color.text_main));
        }

        if (isLast) {
            stepBinding.viewLine.setVisibility(View.GONE);
        }

        binding.lnTimeline.addView(stepBinding.getRoot());
    }

    private void fetchRealRefundDetailAndPopulate() {
        if (order == null || order.getPaymentMethod() == null) {
            binding.tvRefundMethod.setText("Chưa rõ phương thức");
            return;
        }
        binding.tvRefundMethod.setText(getDisplayRefundMethod(order.getPaymentMethod()));
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
        } else if (pm.contains("card") || pm.contains("thẻ tín dụng") || pm.contains("ghi nợ")) {
            return "Thẻ Tín dụng / Ghi nợ";
        } else if (pm.contains("atm") || pm.contains("nội địa")) {
            return "Thẻ ATM nội địa";
        }
        return paymentMethod;
    }
}
