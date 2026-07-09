package com.example.healthup.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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
    private Spinner spinnerStatus;
    private LinearLayout layoutHistory;
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
        MaterialButton btnSave = findViewById(R.id.btnUpdateOrderStatus);

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
        TextView tvTotal = findViewById(R.id.tvOrderDetailTotal);
        TextView tvCustomer = findViewById(R.id.tvOrderDetailCustomer);

        tvCode.setText(currentOrder.getOrderCode() != null ? currentOrder.getOrderCode() : currentOrder.getId());
        tvTotal.setText("Tổng tiền: " + priceFormat.format(currentOrder.getTotalPrice()) + " đ");
        tvCustomer.setText("Khách hàng: Đang tải…");
        AdminCustomerResolver.resolve(currentOrder, label ->
                tvCustomer.post(() -> tvCustomer.setText("Khách hàng: " + label)));

        int index = STATUSES.indexOf(currentOrder.getStatus());
        if (index >= 0) spinnerStatus.setSelection(index);
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
}
