package com.example.healthup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityOrderDetailBinding;
import com.example.healthup.databinding.DialogLoadingBinding;
import com.example.healthup.databinding.DialogSuccessBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.LayoutBottomSheetCancelOrderBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.ReturnReason;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrderDetailActivity extends AppCompatActivity {
    private ActivityOrderDetailBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private Order currentOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentOrder = (Order) getIntent().getSerializableExtra("order");
        if (currentOrder != null) {
            populateUI(currentOrder);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancelOrder.setOnClickListener(v -> showCancelOrderBottomSheet());
        binding.btnConfirmReceived.setOnClickListener(v -> handleConfirmReceived());
        
        binding.btnReturnRefundDetail.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReturnRefundActivity.class);
            intent.putExtra("orderId", currentOrder.getId());
            intent.putExtra("orderCode", currentOrder.getOrderCode());
            intent.putExtra("items", new java.util.ArrayList<>(currentOrder.getItems()));
            intent.putExtra("paymentMethod", currentOrder.getPaymentMethod());
            intent.putExtra("shippingAddress", currentOrder.getAddress().getAddressDetail());
            startActivity(intent);
        });
    }

    private void handleConfirmReceived() {
        showLoadingAndThenUpdateFirebase("delivered");
    }

    private void showCancelOrderBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetCancelOrderBinding dialogBinding = LayoutBottomSheetCancelOrderBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        List<ReturnReason> reasons = Arrays.asList(
                new ReturnReason("Tôi muốn thay đổi địa chỉ giao hàng", ""),
                new ReturnReason("Tôi muốn thay đổi sản phẩm", ""),
                new ReturnReason("Đặt nhầm sản phẩm", ""),
                new ReturnReason("Tìm thấy giá tốt hơn ở nơi khác", ""),
                new ReturnReason("Thời gian giao hàng quá lâu", ""),
                new ReturnReason("Lý do khác", "")
        );

        dialogBinding.rvReasons.setLayoutManager(new LinearLayoutManager(this));
        ReturnReasonAdapter adapter = new ReturnReasonAdapter(reasons, null);
        dialogBinding.rvReasons.setAdapter(adapter);

        dialogBinding.btnBack.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmCancel.setOnClickListener(v -> {
            if (adapter.getSelectedReason() == null) {
                Toast.makeText(this, "Vui lòng chọn lý do hủy đơn", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showLoadingAndThenUpdateFirebase("cancelled");
        });

        dialog.show();
    }

    private void showLoadingAndThenUpdateFirebase(String targetStatus) {
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();

        com.google.android.gms.tasks.Task<Void> updateTask;
        String targetTab;
        if ("cancelled".equals(targetStatus)) {
            updateTask = FirebaseManager.getInstance().updateOrderStatus(currentOrder.getId(), "cancelled");
            targetTab = "cancelled_tab";
        } else {
            updateTask = FirebaseManager.getInstance().confirmReceived(currentOrder.getId());
            targetTab = "delivered_tab";
        }

        updateTask.addOnSuccessListener(aVoid -> {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadingDialog.dismiss();
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", targetTab);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }, 3000);
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
            Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void populateUI(Order order) {
        String status = order.getStatus().toLowerCase();
        
        if ("pending".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ XÁC NHẬN");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("confirmed".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ LẤY HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("shipping".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ GIAO HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("delivered".equals(status)) {
            binding.tvStatusBanner.setText("ĐÃ GIAO HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_delivered);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else if ("cancelled".equals(status)) {
            binding.tvStatusBanner.setText("ĐÃ HỦY");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_cancelled);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        }

        binding.tvPaymentMethod.setText("Thanh toán bằng " + order.getPaymentMethod());
        if (order.getPaymentMethod().contains("Thẻ") || order.getPaymentMethod().contains("Tài khoản")) {
            binding.imgPaymentIcon.setImageResource(R.drawable.ic_payment_card);
        } else {
            binding.imgPaymentIcon.setImageResource(R.drawable.ic_payment_wallet);
        }

        if (order.getAddress() != null) {
            binding.tvCustomerName.setText(order.getAddress().getRecipientName());
            binding.tvCustomerPhone.setText("(+84) " + order.getAddress().getPhone().substring(1));
            binding.tvShippingAddress.setText(order.getAddress().getAddressDetail());
        }

        if ("shipping".equals(status) || "delivered".equals(status) || "cancelled".equals(status) || "returned".equals(status)) {
            binding.btnUpdateAddress.setVisibility(View.GONE);
        } else {
            binding.btnUpdateAddress.setVisibility(View.VISIBLE);
        }

        binding.tvOrderCode.setText(order.getOrderCode());
        binding.btnCopyOrderCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Order Code", order.getOrderCode());
            clipboard.setPrimaryClip(clip);
        });

        binding.lnItemsContainer.removeAllViews();
        List<OrderItem> items = order.getItems();
        for (OrderItem item : items) {
            ItemOrderProductBinding pBinding = ItemOrderProductBinding.inflate(
                    LayoutInflater.from(this), binding.lnItemsContainer, false);
            pBinding.tvProductName.setText(item.getName());
            pBinding.tvVariant.setText(item.getVariantLabel());
            pBinding.tvPrice.setText(df.format(item.getPrice()));
            pBinding.tvQuantity.setText("x" + item.getQuantity());
            Glide.with(this).load(item.getImageUrl()).placeholder(R.drawable.ic_launcher_background).into(pBinding.imgProduct);
            binding.lnItemsContainer.addView(pBinding.getRoot());
        }

        if ("confirmed".equals(status)) {
            long shipBeforeTime = order.getCreatedAt() + 86400000L;
            binding.tvOrderTime.setText("Đơn hàng sẽ được gửi đi trước " + sdf.format(new Date(shipBeforeTime)));
        } else if ("shipping".equals(status)) {
            if (order.isShopConfirmedDelivery()) {
                binding.tvOrderTime.setText("Đơn hàng đã được giao thành công vào " + sdf.format(new Date(order.getDeliveredAt())));
            } else {
                long deliveryBeforeTime = order.getCreatedAt() + 3 * 86400000L;
                binding.tvOrderTime.setText("Đơn hàng sẽ được giao đến bạn trước ngày " + sdfDate.format(new Date(deliveryBeforeTime)));
            }
        } else if ("delivered".equals(status)) {
            long deliveryTime = order.getDeliveredAt() > 0 ? order.getDeliveredAt() : order.getCreatedAt() + 2 * 86400000L;
            binding.tvOrderTime.setText("Đơn hàng đã được giao thành công vào " + sdf.format(new Date(deliveryTime)));
        } else {
            binding.tvOrderTime.setText("Thời gian đặt hàng: " + sdf.format(new Date(order.getCreatedAt())));
        }

        binding.btnCancelOrder.setVisibility(View.GONE);
        binding.btnConfirmReceived.setVisibility(View.GONE);
        binding.lnDeliveredActions.setVisibility(View.GONE);
        binding.cvCancelledInfo.setVisibility(View.GONE);
        binding.btnRebuyFull.setVisibility(View.GONE);

        if ("pending".equals(status)) {
            binding.btnCancelOrder.setVisibility(View.VISIBLE);
        } else if ("shipping".equals(status)) {
            binding.btnConfirmReceived.setVisibility(View.VISIBLE);
            if (order.isShopConfirmedDelivery()) {
                binding.btnConfirmReceived.setEnabled(true);
                binding.btnConfirmReceived.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary)));
                binding.btnConfirmReceived.setTextColor(getResources().getColor(R.color.white));
            } else {
                binding.btnConfirmReceived.setEnabled(false);
                binding.btnConfirmReceived.setBackgroundTintList(ColorStateList.valueOf(0xFFF0F0F0));
                binding.btnConfirmReceived.setTextColor(0xFF888888);
            }
        } else if ("delivered".equals(status)) {
            binding.lnDeliveredActions.setVisibility(View.VISIBLE);
            if (order.isReviewed()) {
                binding.btnReviewDetail.setText("Xem đánh giá");
                binding.btnReviewDetail.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ViewReviewsActivity.class);
                    intent.putExtra("order", order);
                    startActivity(intent);
                });
            } else {
                binding.btnReviewDetail.setText("Đánh giá");
                binding.btnReviewDetail.setOnClickListener(v -> {
                    Intent intent = new Intent(this, WriteReviewActivity.class);
                    intent.putExtra("order", order);
                    startActivity(intent);
                });
            }
        } else if ("cancelled".equals(status)) {
            binding.cvCancelledInfo.setVisibility(View.VISIBLE);
            binding.btnRebuyFull.setVisibility(View.VISIBLE);
            binding.tvCancelledTime.setText(sdf.format(new Date(order.getUpdatedAt())));
            String method = order.getPaymentMethod();
            binding.tvPaymentMethodCancelled.setText(method.contains("Thanh toán khi nhận hàng") ? "COD" : method);

            if (!method.contains("Thanh toán khi nhận hàng")) {
                binding.tvRefundCancelledInfo.setVisibility(View.VISIBLE);
                String refundMsg;
                if (method.contains("MoMo")) refundMsg = "Tiền sẽ được hoàn về ví MoMo của bạn trong vòng 24h làm việc.";
                else if (method.contains("ZaloPay")) refundMsg = "Tiền sẽ được hoàn về ví ZaloPay của bạn trong vòng 24h làm việc.";
                else if (method.contains("VNPAY")) refundMsg = "Tiền sẽ được hoàn về tài khoản VNPAY của bạn trong vòng 1-3 ngày làm việc.";
                else if (method.contains("Thẻ ATM") || method.contains("Tài khoản")) refundMsg = "Tiền sẽ được hoàn về đúng thẻ/tài khoản bạn đã dùng thanh toán trong vòng 3-7 ngày làm việc.";
                else refundMsg = "Tiền sẽ được hoàn về tài khoản của bạn trong vòng 3-7 ngày làm việc.";
                binding.tvRefundCancelledInfo.setText(refundMsg);
            } else {
                binding.tvRefundCancelledInfo.setVisibility(View.GONE);
            }
        }
    }
}
