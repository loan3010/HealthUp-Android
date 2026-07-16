package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.example.healthup.BaseAppCompatActivity;

import com.example.healthup.R;
import com.example.healthup.util.ImageLoadHelper;
import com.example.healthup.util.ReturnProgressHelper;
import com.example.models.Address;
import com.example.models.DeliveryFailure;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminOrderDetailActivity extends BaseAppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private final AdminRepository repository = new AdminRepository();
    private String orderId;
    private Order currentOrder;
    private String customerLabel = "—";
    private LinearLayout layoutHistory;
    private LinearLayout layoutOrderItems;
    private LinearLayout layoutOrderActions;
    private LinearLayout layoutDeliveryFailures;
    private LinearLayout layoutReturnInfo;
    private LinearLayout layoutReturnImages;
    private View scrollReturnImages;
    private TextView tvCustomer;
    private TextView tvDeliveryAttemptsTitle;
    private TextView tvReturnInfoTitle;
    private final NumberFormat priceFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_order_detail);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        MaterialToolbar toolbar = findViewById(R.id.toolbarOrderDetail);
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutHistory = findViewById(R.id.layoutOrderHistory);
        layoutOrderItems = findViewById(R.id.layoutOrderItems);
        layoutOrderActions = findViewById(R.id.layoutOrderActions);
        layoutDeliveryFailures = findViewById(R.id.layoutDeliveryFailures);
        layoutReturnInfo = findViewById(R.id.layoutReturnInfo);
        layoutReturnImages = findViewById(R.id.layoutReturnImages);
        scrollReturnImages = findViewById(R.id.scrollReturnImages);
        tvCustomer = findViewById(R.id.tvOrderDetailCustomer);
        tvDeliveryAttemptsTitle = findViewById(R.id.tvDeliveryAttemptsTitle);
        tvReturnInfoTitle = findViewById(R.id.tvReturnInfoTitle);
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
        bindDeliveryFailures();
        bindReturnInfo();
        bindActions();

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
    }

    private void bindActions() {
        layoutOrderActions.removeAllViews();
        if (currentOrder == null) return;

        String status = currentOrder.getStatus() != null ? currentOrder.getStatus() : "";
        boolean returnRequested = Order.RETURN_REQUESTED.equals(currentOrder.getReturnStatus())
                || Order.STATUS_RETURNED.equalsIgnoreCase(status);
        boolean returnApproved = Order.RETURN_APPROVED.equals(currentOrder.getReturnStatus());

        if (Order.STATUS_PENDING.equals(status)) {
            addActionButton(getString(R.string.admin_action_confirm_order), true, v ->
                    runAction(() -> repository.confirmOrder(currentOrder, refreshCallback())));
            addActionButton(getString(R.string.admin_action_cancel_order), false, v ->
                    showAdminCancelOrderDialog());
        } else if (Order.STATUS_CONFIRMED.equals(status)) {
            addActionButton(getString(R.string.admin_action_start_shipping), true, v ->
                    runAction(() -> repository.startShipping(currentOrder, refreshCallback())));
        } else if (Order.STATUS_SHIPPING.equals(status)) {
            if (currentOrder.isNeedsRedelivery()) {
                int next = currentOrder.getDeliveryAttempts() + 1;
                addActionButton(getString(R.string.admin_action_redeliver, next), true, v ->
                        runAction(() -> repository.scheduleRedelivery(currentOrder, refreshCallback())));
            } else if (!currentOrder.isShopConfirmedDelivery()) {
                addActionButton(getString(R.string.admin_action_delivery_success), true, v ->
                        runAction(() -> repository.confirmShopDelivery(currentOrder, refreshCallback())));
                addActionButton(getString(R.string.admin_action_delivery_failed), false, v ->
                        showDeliveryFailureDialog());
            } else {
                TextView waiting = new TextView(this);
                waiting.setText(R.string.admin_waiting_customer_confirm);
                waiting.setTextColor(getColor(R.color.text_secondary));
                layoutOrderActions.addView(waiting);
            }
        }

        if (returnRequested) {
            addActionButton(ReturnProgressHelper.approveLabel(currentOrder), true, v ->
                    runAction(() -> repository.approveReturn(currentOrder, refreshCallback())));
            addActionButton(ReturnProgressHelper.rejectLabel(currentOrder), false, v ->
                    showRejectReturnDialog());
        } else if (returnApproved) {
            String advanceLabel = ReturnProgressHelper.nextAdvanceLabel(currentOrder);
            if (advanceLabel != null) {
                addActionButton(advanceLabel, true, v ->
                        runAction(() -> repository.advanceReturnProgress(currentOrder, refreshCallback())));
            }
            addActionButton(ReturnProgressHelper.rejectLabel(currentOrder), false, v ->
                    showRejectReturnDialog());
        }

        if (layoutOrderActions.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.admin_no_actions);
            empty.setTextColor(getColor(R.color.text_secondary));
            layoutOrderActions.addView(empty);
        }
    }

    private void addActionButton(String label, boolean primary, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = layoutOrderActions.getChildCount() == 0 ? 0 : 8;
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setCornerRadius(24);
        if (primary) {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary)));
            btn.setTextColor(getColor(R.color.white));
        } else {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.white)));
            btn.setTextColor(getColor(R.color.brand_primary));
            btn.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary)));
            btn.setStrokeWidth(2);
        }
        btn.setOnClickListener(listener);
        layoutOrderActions.addView(btn);
    }

    private void showAdminCancelOrderDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.admin_cancel_order_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_cancel_order_title)
                .setMessage(R.string.admin_cancel_order_message)
                .setView(input)
                .setPositiveButton(R.string.admin_action_cancel_order, (d, w) -> {
                    String reason = input.getText() != null ? input.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        Toast.makeText(this, R.string.admin_cancel_order_reason_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runAction(() -> repository.cancelPendingOrder(currentOrder, reason, refreshCallback()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeliveryFailureDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_delivery_failure, null);
        Spinner spinner = dialogView.findViewById(R.id.spinnerFailureReason);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Order.deliveryFailureReasons()));

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_delivery_failure_title)
                .setView(dialogView)
                .setPositiveButton(R.string.admin_confirm, (d, w) -> {
                    String reason = (String) spinner.getSelectedItem();
                    if (TextUtils.isEmpty(reason)) {
                        Toast.makeText(this, R.string.admin_failure_reason_label, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runAction(() -> repository.recordDeliveryFailure(currentOrder, reason, null, refreshCallback()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRejectReturnDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.admin_reject_return_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(ReturnProgressHelper.rejectLabel(currentOrder))
                .setView(input)
                .setPositiveButton(R.string.admin_confirm, (d, w) -> {
                    String reason = input.getText() != null ? input.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        Toast.makeText(this, R.string.admin_reject_return_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runAction(() -> repository.rejectReturn(currentOrder, reason, refreshCallback()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runAction(Runnable action) {
        action.run();
    }

    private AdminRepository.SimpleCallback refreshCallback() {
        return new AdminRepository.SimpleCallback() {
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
        };
    }

    private void bindDeliveryFailures() {
        layoutDeliveryFailures.removeAllViews();
        List<DeliveryFailure> failures = currentOrder != null ? currentOrder.getDeliveryFailures() : null;
        if (failures == null || failures.isEmpty()) {
            tvDeliveryAttemptsTitle.setVisibility(View.GONE);
            if (currentOrder != null && !TextUtils.isEmpty(currentOrder.getCancelReason())
                    && Order.STATUS_CANCELLED.equalsIgnoreCase(currentOrder.getStatus())) {
                tvDeliveryAttemptsTitle.setVisibility(View.VISIBLE);
                tvDeliveryAttemptsTitle.setText(R.string.admin_cancel_info);
                TextView tv = new TextView(this);
                tv.setText(currentOrder.getCancelReason());
                tv.setTextColor(getColor(R.color.text_secondary));
                layoutDeliveryFailures.addView(tv);
            }
            return;
        }
        tvDeliveryAttemptsTitle.setVisibility(View.VISIBLE);
        tvDeliveryAttemptsTitle.setText(getString(R.string.admin_delivery_attempts_count,
                currentOrder.getDeliveryAttempts()));
        for (DeliveryFailure failure : failures) {
            TextView tv = new TextView(this);
            String time = failure.getAt() != null ? dateFormat.format(failure.getAt()) : "";
            StringBuilder sb = new StringBuilder();
            sb.append("Lần ").append(failure.getAttempt()).append(": ").append(failure.getReason());
            if (!TextUtils.isEmpty(failure.getNote())) {
                sb.append(" — ").append(failure.getNote());
            }
            if (!TextUtils.isEmpty(time)) {
                sb.append("\n").append(time);
            }
            if (!TextUtils.isEmpty(failure.getByEmail())) {
                sb.append(" • ").append(failure.getByEmail());
            }
            tv.setText(sb.toString());
            tv.setTextColor(getColor(R.color.text_secondary));
            tv.setPadding(0, 0, 0, 16);
            layoutDeliveryFailures.addView(tv);
        }
    }

    private void bindReturnInfo() {
        layoutReturnInfo.removeAllViews();
        layoutReturnImages.removeAllViews();
        scrollReturnImages.setVisibility(View.GONE);

        if (currentOrder == null || !currentOrder.hasActiveReturn()) {
            tvReturnInfoTitle.setVisibility(View.GONE);
            return;
        }
        tvReturnInfoTitle.setVisibility(View.VISIBLE);

        addInfoLine("Trạng thái trả: " + returnStatusLabel(currentOrder.getReturnStatus()));
        if (Order.RETURN_APPROVED.equals(currentOrder.getReturnStatus())
                || Order.RETURN_COMPLETED.equals(currentOrder.getReturnStatus())) {
            int max = com.example.healthup.util.ReturnProgressHelper.maxStep(currentOrder);
            int step = Math.max(currentOrder.getReturnStep(), 0);
            addInfoLine("Tiến trình: bước " + step + "/" + max
                    + " — " + com.example.healthup.util.ReturnProgressHelper.stepTitle(
                    currentOrder.getReturnHandling(), Math.max(step, 1)));
        }
        if (!TextUtils.isEmpty(currentOrder.getReturnReason())) {
            addInfoLine("Lý do: " + currentOrder.getReturnReason());
        }
        if (!TextUtils.isEmpty(currentOrder.getReturnDescription())) {
            addInfoLine("Mô tả: " + currentOrder.getReturnDescription());
        }
        if (!TextUtils.isEmpty(currentOrder.getReturnHandling())) {
            addInfoLine("Hình thức: " + currentOrder.getReturnHandling());
        }
        if (!TextUtils.isEmpty(currentOrder.getReturnRejectReason())) {
            addInfoLine("Lý do từ chối: " + currentOrder.getReturnRejectReason());
        }
        if (currentOrder.getReturnRequestedAt() != null) {
            addInfoLine("Gửi lúc: " + dateFormat.format(currentOrder.getReturnRequestedAt()));
        }
        List<Map<String, Object>> returnItems = currentOrder.getReturnItems();
        if (returnItems != null && !returnItems.isEmpty()) {
            StringBuilder items = new StringBuilder("Sản phẩm trả: ");
            for (int i = 0; i < returnItems.size(); i++) {
                Map<String, Object> item = returnItems.get(i);
                if (i > 0) items.append(", ");
                Object name = item.get("name");
                Object qty = item.get("quantity");
                items.append(name != null ? name : "?");
                if (qty != null) items.append(" x").append(qty);
            }
            addInfoLine(items.toString());
        }

        List<String> media = currentOrder.getReturnMediaUris();
        if (media != null && !media.isEmpty()) {
            scrollReturnImages.setVisibility(View.VISIBLE);
            for (String uri : media) {
                ImageView img = new ImageView(this);
                int size = (int) (96 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
                img.setLayoutParams(lp);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ImageLoadHelper.loadInto(img, uri);
                layoutReturnImages.addView(img);
            }
        }
    }

    private void addInfoLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.text_secondary));
        tv.setPadding(0, 0, 0, 8);
        layoutReturnInfo.addView(tv);
    }

    private String returnStatusLabel(String status) {
        if (status == null) return "—";
        switch (status) {
            case Order.RETURN_REQUESTED: return "Chờ duyệt";
            case Order.RETURN_APPROVED: return "Đang xử lý";
            case Order.RETURN_REJECTED: return "Không thành công";
            case Order.RETURN_COMPLETED: return "Hoàn thành";
            default: return status;
        }
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
                LayoutInflater inflater = LayoutInflater.from(AdminOrderDetailActivity.this);
                for (int i = 0; i < entries.size(); i++) {
                    AdminRepository.OrderHistoryEntry entry = entries.get(i);
                    View row = inflater.inflate(R.layout.item_order_history_timeline, layoutHistory, false);
                    View topLine = row.findViewById(R.id.viewTimelineTopLine);
                    View bottomLine = row.findViewById(R.id.viewTimelineBottomLine);
                    TextView tvEvent = row.findViewById(R.id.tvHistoryEvent);
                    TextView tvActor = row.findViewById(R.id.tvHistoryActor);
                    TextView tvTime = row.findViewById(R.id.tvHistoryTime);
                    TextView tvNote = row.findViewById(R.id.tvHistoryNote);

                    topLine.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
                    bottomLine.setVisibility(i == entries.size() - 1 ? View.INVISIBLE : View.VISIBLE);

                    String eventLabel = AdminUiHelper.historyEventLabel(entry);
                    tvEvent.setText(eventLabel);

                    String actor = AdminUiHelper.historyActorLabel(entry);
                    if (TextUtils.isEmpty(actor)) {
                        tvActor.setVisibility(View.GONE);
                    } else {
                        tvActor.setVisibility(View.VISIBLE);
                        tvActor.setText(actor);
                    }

                    String time = entry.createdAt != null ? dateFormat.format(entry.createdAt.toDate()) : "";
                    tvTime.setText(time);

                    String note = AdminUiHelper.historyNoteLabel(entry);
                    if (TextUtils.isEmpty(note) || note.equals(eventLabel)) {
                        tvNote.setVisibility(View.GONE);
                    } else {
                        tvNote.setVisibility(View.VISIBLE);
                        tvNote.setText(note);
                    }
                    layoutHistory.addView(row);
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
        if (low.contains("atm")) return "Thẻ ATM nội địa";
        return method;
    }

    private String formatPaymentStatus(@Nullable String status) {
        if (TextUtils.isEmpty(status)) return "Chưa rõ";
        if ("paid".equalsIgnoreCase(status)) return "Đã thanh toán";
        if ("pending".equalsIgnoreCase(status)) return "Chờ thanh toán";
        if ("refunded".equalsIgnoreCase(status)) return "Đã hoàn tiền";
        return status;
    }
}
