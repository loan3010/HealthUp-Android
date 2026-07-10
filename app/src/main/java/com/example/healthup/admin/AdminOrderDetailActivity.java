package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.R;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.Address;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AdminOrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private static final List<String> STATUSES = Arrays.asList(
            Order.STATUS_PENDING,
            Order.STATUS_CONFIRMED,
            Order.STATUS_SHIPPING,
            Order.STATUS_DELIVERED,
            Order.STATUS_CANCELLED,
            "returned"
    );

    private final AdminRepository repository = new AdminRepository();
    private String orderId;
    private Order currentOrder;
    private String customerLabel = "—";
    private Spinner spinnerStatus;
    private LinearLayout layoutHistory;
    private LinearLayout layoutOrderItems;
    private TextView tvCustomer;
    private MaterialCardView cardCancelRequest;
    private TextView tvCancelRequestReason;
    private TextView tvCancelRequestTime;
    private MaterialButton btnApproveCancelRequest;
    private MaterialButton btnRejectCancelRequest;
    private final NumberFormat priceFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_order_detail);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        MaterialToolbar toolbar = findViewById(R.id.toolbarOrderDetail);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerStatus = findViewById(R.id.spinnerOrderStatus);
        layoutHistory = findViewById(R.id.layoutOrderHistory);
        layoutOrderItems = findViewById(R.id.layoutOrderItems);
        tvCustomer = findViewById(R.id.tvOrderDetailCustomer);
        cardCancelRequest = findViewById(R.id.cardCancelRequest);
        tvCancelRequestReason = findViewById(R.id.tvCancelRequestReason);
        tvCancelRequestTime = findViewById(R.id.tvCancelRequestTime);
        btnApproveCancelRequest = findViewById(R.id.btnApproveCancelRequest);
        btnRejectCancelRequest = findViewById(R.id.btnRejectCancelRequest);
        MaterialButton btnSave = findViewById(R.id.btnUpdateOrderStatus);

        btnApproveCancelRequest.setOnClickListener(v -> approveCancelRequest());
        btnRejectCancelRequest.setOnClickListener(v -> rejectCancelRequest());

        List<String> labels = Arrays.asList(
                AdminUiHelper.statusLabel(Order.STATUS_PENDING),
                AdminUiHelper.statusLabel(Order.STATUS_CONFIRMED),
                AdminUiHelper.statusLabel(Order.STATUS_SHIPPING),
                AdminUiHelper.statusLabel(Order.STATUS_DELIVERED),
                AdminUiHelper.statusLabel(Order.STATUS_CANCELLED),
                AdminUiHelper.statusLabel("returned")
        );
        spinnerStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        btnSave.setOnClickListener(v -> updateStatus());
        tvCustomer.setOnClickListener(v -> openCustomerDetail());

        loadOrder();
        loadHistory();
    }

    private void loadOrder() {
        FirebaseFirestore.getInstance().collection("orders").document(orderId).get()
                .addOnSuccessListener(this::bindOrder)
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void bindOrder(DocumentSnapshot doc) {
        currentOrder = AdminRepository.parseOrderDocument(doc);
        if (currentOrder == null) return;

        TextView tvCode = findViewById(R.id.tvOrderDetailCode);
        TextView tvStatus = findViewById(R.id.tvOrderDetailStatus);
        TextView tvCreatedAt = findViewById(R.id.tvOrderDetailCreatedAt);
        TextView tvAddress = findViewById(R.id.tvOrderDetailAddress);
        TextView tvPayment = findViewById(R.id.tvOrderDetailPayment);
        TextView tvSubtotal = findViewById(R.id.tvOrderDetailSubtotal);
        TextView tvShipping = findViewById(R.id.tvOrderDetailShipping);
        TextView tvDiscount = findViewById(R.id.tvOrderDetailDiscount);
        TextView tvTotal = findViewById(R.id.tvOrderDetailTotal);

        tvCode.setText(currentOrder.getOrderCode() != null ? currentOrder.getOrderCode() : currentOrder.getId());
        tvStatus.setText(AdminUiHelper.orderStatusLabel(currentOrder));
        tvCreatedAt.setText("Thời gian đặt: "
                + (currentOrder.getCreatedAt() != null ? dateFormat.format(currentOrder.getCreatedAt()) : "—"));
        tvTotal.setText("Tổng tiền: " + priceFormat.format(currentOrder.getTotalPrice()) + " đ");
        tvSubtotal.setText("Tạm tính: " + priceFormat.format(currentOrder.getSubtotal()) + " đ");
        tvShipping.setText("Phí giao hàng: " + priceFormat.format(currentOrder.getShippingFee()) + " đ");

        if (currentOrder.getDiscountAmount() > 0) {
            tvDiscount.setVisibility(View.VISIBLE);
            String promo = currentOrder.getPromoCode();
            tvDiscount.setText("Giảm giá"
                    + (TextUtils.isEmpty(promo) ? "" : (" (" + promo + ")"))
                    + ": " + priceFormat.format(currentOrder.getDiscountAmount()) + " đ");
        } else {
            tvDiscount.setVisibility(View.GONE);
        }

        tvPayment.setText("Phương thức: " + formatPaymentMethod(currentOrder.getPaymentMethod())
                + " • " + formatPaymentStatus(currentOrder.getPaymentStatus()));
        tvAddress.setText(formatAddress(currentOrder.getAddress()));

        bindOrderItems(currentOrder.getItems());

        customerLabel = "Đang tải…";
        tvCustomer.setText(customerLabel + "  ›");
        tvCustomer.setEnabled(false);
        AdminCustomerResolver.resolve(currentOrder, label -> {
            customerLabel = label;
            tvCustomer.post(() -> {
                tvCustomer.setText(label + "  ›");
                tvCustomer.setEnabled(!TextUtils.isEmpty(currentOrder.getUserId()));
            });
        });

        int index = STATUSES.indexOf(currentOrder.getStatus());
        if (index >= 0) spinnerStatus.setSelection(index);

        bindCancelRequestSection();
    }

    private void bindCancelRequestSection() {
        if (cardCancelRequest == null || currentOrder == null) {
            return;
        }
        boolean show = currentOrder.isCancelRequested()
                && Order.STATUS_PENDING.equalsIgnoreCase(currentOrder.getStatus());
        cardCancelRequest.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        String reason = currentOrder.getCancelReason();
        tvCancelRequestReason.setText(getString(R.string.admin_cancel_request_reason,
                TextUtils.isEmpty(reason) ? "—" : reason));
        if (currentOrder.getCancelRequestedAt() != null) {
            tvCancelRequestTime.setText(getString(R.string.admin_cancel_request_time,
                    dateFormat.format(currentOrder.getCancelRequestedAt())));
        } else {
            tvCancelRequestTime.setText("");
        }
    }

    private void approveCancelRequest() {
        if (currentOrder == null) return;
        repository.approveCancelRequest(orderId, currentOrder.getUserId(),
                currentOrder.getTotalPrice(), new AdminRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(AdminOrderDetailActivity.this, R.string.admin_cancel_approved, Toast.LENGTH_SHORT).show();
                        loadOrder();
                        loadHistory();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(AdminOrderDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void rejectCancelRequest() {
        if (currentOrder == null) return;
        repository.rejectCancelRequest(orderId, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminOrderDetailActivity.this, R.string.admin_cancel_rejected, Toast.LENGTH_SHORT).show();
                loadOrder();
                loadHistory();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminOrderDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindOrderItems(@Nullable List<OrderItem> items) {
        layoutOrderItems.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Không có sản phẩm");
            empty.setTextColor(getColor(R.color.text_secondary));
            layoutOrderItems.addView(empty);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (OrderItem item : items) {
            View row = inflater.inflate(R.layout.item_admin_order_line, layoutOrderItems, false);
            ImageView img = row.findViewById(R.id.imgAdminOrderLine);
            TextView tvName = row.findViewById(R.id.tvAdminOrderLineName);
            TextView tvVariant = row.findViewById(R.id.tvAdminOrderLineVariant);
            TextView tvPrice = row.findViewById(R.id.tvAdminOrderLinePrice);
            TextView tvQty = row.findViewById(R.id.tvAdminOrderLineQty);

            tvName.setText(item.getName() != null ? item.getName() : "Sản phẩm");
            if (!TextUtils.isEmpty(item.getVariantLabel())) {
                tvVariant.setVisibility(View.VISIBLE);
                tvVariant.setText(item.getVariantLabel());
            } else {
                tvVariant.setVisibility(View.GONE);
            }
            tvPrice.setText(priceFormat.format(item.getPrice()) + " đ");
            tvQty.setText("x" + item.getQuantity());
            ImageLoadHelper.loadInto(img, item.getImageUrl());
            layoutOrderItems.addView(row);
        }
    }

    private void openCustomerDetail() {
        if (currentOrder == null || TextUtils.isEmpty(currentOrder.getUserId())) {
            return;
        }
        Intent intent = new Intent(this, AdminCustomerDetailActivity.class);
        intent.putExtra(AdminCustomerDetailActivity.EXTRA_CUSTOMER_UID, currentOrder.getUserId());
        startActivity(intent);
    }

    private void updateStatus() {
        if (currentOrder == null) return;
        String newStatus = STATUSES.get(spinnerStatus.getSelectedItemPosition());
        if (newStatus.equals(currentOrder.getStatus())) {
            Toast.makeText(this, "Trạng thái không thay đổi", Toast.LENGTH_SHORT).show();
            return;
        }
        repository.updateOrderStatus(orderId, currentOrder.getStatus(), newStatus, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminOrderDetailActivity.this, R.string.admin_saved, Toast.LENGTH_SHORT).show();
                loadOrder();
                loadHistory();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminOrderDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadHistory() {
        if (orderId == null || orderId.isEmpty()) {
            return;
        }
        repository.loadOrderHistory(orderId, new AdminRepository.OrderHistoryCallback() {
            @Override
            public void onSuccess(List<AdminRepository.OrderHistoryEntry> entries) {
                layoutHistory.removeAllViews();
                if (entries.isEmpty()) {
                    TextView empty = new TextView(AdminOrderDetailActivity.this);
                    empty.setText("Chưa có lịch sử");
                    empty.setTextColor(getColor(R.color.text_secondary));
                    layoutHistory.addView(empty);
                    return;
                }
                for (AdminRepository.OrderHistoryEntry entry : entries) {
                    TextView tv = new TextView(AdminOrderDetailActivity.this);
                    String time = entry.createdAt != null ? dateFormat.format(entry.createdAt.toDate()) : "";
                    tv.setText(time + " • "
                            + AdminUiHelper.statusLabel(entry.fromStatus) + " → "
                            + AdminUiHelper.statusLabel(entry.toStatus)
                            + (entry.adminEmail != null ? (" • " + entry.adminEmail) : ""));
                    tv.setPadding(0, 0, 0, 16);
                    layoutHistory.addView(tv);
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private String formatAddress(@Nullable Address address) {
        if (address == null) {
            return "—";
        }
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(address.getRecipientName())) {
            builder.append(address.getRecipientName());
        }
        if (!TextUtils.isEmpty(address.getPhone())) {
            if (builder.length() > 0) builder.append(" • ");
            builder.append(address.getPhone());
        }
        String full = address.getFullAddress();
        if (!TextUtils.isEmpty(full)) {
            if (builder.length() > 0) builder.append("\n");
            builder.append(full);
        }
        return builder.length() > 0 ? builder.toString() : "—";
    }

    private String formatPaymentMethod(@Nullable String method) {
        if (TextUtils.isEmpty(method)) return "—";
        String low = method.toLowerCase(Locale.ROOT);
        if (low.contains("momo")) return "Ví MoMo";
        if (low.contains("zalopay")) return "Ví ZaloPay";
        if (low.contains("vnpay")) return "Ví VNPAY";
        if (low.contains("cod") || low.contains("nhận hàng")) return "COD";
        return method;
    }

    private String formatPaymentStatus(@Nullable String status) {
        if (TextUtils.isEmpty(status)) return "Chưa rõ";
        if ("paid".equalsIgnoreCase(status)) return "Đã thanh toán";
        if ("pending".equalsIgnoreCase(status)) return "Chờ thanh toán";
        return status;
    }
}
