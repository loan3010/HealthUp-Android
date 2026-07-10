package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminCustomerDetailActivity extends AppCompatActivity
        implements AdminCustomerOrderAdapter.Listener {

    public static final String EXTRA_CUSTOMER_UID = "customer_uid";

    private final AdminRepository repository = new AdminRepository();
    private final List<Order> customerOrders = new ArrayList<>();
    private final NumberFormat moneyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    private String customerUid;
    private boolean disabled;
    private SwitchMaterial switchDisabled;
    private ProgressBar progressBar;
    private LinearLayout layoutContact;
    private LinearLayout layoutAccount;
    private MaterialCardView cardDisabledReason;
    private TextView tvDisabledReason;
    private TextView tvOrdersEmpty;
    private AdminCustomerOrderAdapter orderAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_customer_detail);

        customerUid = getIntent().getStringExtra(EXTRA_CUSTOMER_UID);
        MaterialToolbar toolbar = findViewById(R.id.toolbarAdminCustomer);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchDisabled = findViewById(R.id.switchCustomerDetailDisabled);
        progressBar = findViewById(R.id.progressCustomerDetail);
        layoutContact = findViewById(R.id.layoutCustomerContact);
        layoutAccount = findViewById(R.id.layoutCustomerAccount);
        cardDisabledReason = findViewById(R.id.cardDisabledReason);
        tvDisabledReason = findViewById(R.id.tvAdminCustomerDisabledReason);
        tvOrdersEmpty = findViewById(R.id.tvCustomerOrdersEmpty);

        RecyclerView rvOrders = findViewById(R.id.rvCustomerOrders);
        orderAdapter = new AdminCustomerOrderAdapter(customerOrders, this);
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        rvOrders.setAdapter(orderAdapter);

        if (TextUtils.isEmpty(customerUid)) {
            Toast.makeText(this, "Không tìm thấy khách hàng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        switchDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            handleDisableToggle(isChecked);
        });

        loadCustomer();
        loadCustomerOrders();
    }

    private void loadCustomer() {
        progressBar.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance().collection("users").document(customerUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Không tìm thấy hồ sơ khách", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    bindCustomer(doc);
                    loadOrderStats();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void bindCustomer(DocumentSnapshot doc) {
        String fullName = firstNonEmpty(doc.getString("fullName"), doc.getString("name"), doc.getString("displayName"));
        String displayName = firstNonEmpty(fullName, "Khách hàng");
        String username = doc.getString("username");
        String phone = doc.getString("phone");
        String email = firstNonEmpty(doc.getString("displayEmail"), doc.getString("email"));
        String role = firstNonEmpty(doc.getString("role"), doc.getString("userRole"), "buyer");
        String authProvider = doc.getString("authProvider");
        Boolean phoneVerified = doc.getBoolean("phoneVerified");
        Long spent = doc.getLong("spentAmount");
        Boolean disabledVal = doc.getBoolean("disabled");
        String disabledReason = doc.getString("disabledReason");
        disabled = disabledVal != null && disabledVal;

        TextView tvAvatar = findViewById(R.id.tvAdminCustomerAvatar);
        TextView tvName = findViewById(R.id.tvAdminCustomerName);
        TextView tvUsername = findViewById(R.id.tvAdminCustomerUsername);
        TextView tvStatus = findViewById(R.id.tvAdminCustomerStatus);
        TextView tvSpent = findViewById(R.id.tvAdminCustomerSpent);

        tvAvatar.setText(initialLetter(displayName));
        tvName.setText(displayName);
        tvUsername.setText(TextUtils.isEmpty(username) ? "—" : ("@" + username));
        tvSpent.setText(moneyFormat.format(spent != null ? spent : 0L) + " đ");
        updateStatusBadge(tvStatus);

        if (disabled && !TextUtils.isEmpty(disabledReason)) {
            cardDisabledReason.setVisibility(View.VISIBLE);
            tvDisabledReason.setText(disabledReason);
        } else {
            cardDisabledReason.setVisibility(View.GONE);
        }

        resetDisableSwitch();

        layoutContact.removeAllViews();
        addInfoRow(layoutContact, R.string.admin_customer_label_phone, phone, R.drawable.ic_phone);
        addInfoRow(layoutContact, R.string.admin_customer_label_email, email, R.drawable.ic_mail);

        layoutAccount.removeAllViews();
        addInfoRow(layoutAccount, R.string.admin_customer_label_username, username, R.drawable.ic_profile);
        addInfoRow(layoutAccount, R.string.admin_customer_label_role, role, R.drawable.ic_chat_person);
        addInfoRow(layoutAccount, R.string.admin_customer_label_auth, formatAuthProvider(authProvider), R.drawable.ic_settings);
        addInfoRow(layoutAccount, R.string.admin_customer_label_verified,
                Boolean.TRUE.equals(phoneVerified)
                        ? getString(R.string.admin_customer_verified_yes)
                        : getString(R.string.admin_customer_verified_no),
                R.drawable.ic_verified);
        addInfoRow(layoutAccount, R.string.admin_customer_label_uid, customerUid, R.drawable.ic_info);
    }

    private void loadCustomerOrders() {
        repository.loadCustomerOrders(customerUid, new AdminRepository.OrdersCallback() {
            @Override
            public void onSuccess(@NonNull List<Order> orders) {
                customerOrders.clear();
                customerOrders.addAll(orders);
                orderAdapter.notifyDataSetChanged();
                tvOrdersEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull String message) {
                tvOrdersEmpty.setVisibility(View.VISIBLE);
                tvOrdersEmpty.setText(message);
            }
        });
    }

    private void loadOrderStats() {
        repository.loadCustomerOrderStats(customerUid, new AdminRepository.CustomerOrderStatsCallback() {
            @Override
            public void onSuccess(int totalOrders, int pendingOrders) {
                progressBar.setVisibility(View.GONE);
                TextView tvOrderCount = findViewById(R.id.tvAdminCustomerOrderCount);
                TextView tvPending = findViewById(R.id.tvAdminCustomerPendingOrders);
                tvOrderCount.setText(String.valueOf(totalOrders));
                if (pendingOrders > 0) {
                    tvPending.setVisibility(View.VISIBLE);
                    tvPending.setText(getString(R.string.admin_customer_pending_orders, pendingOrders));
                } else {
                    tvPending.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                progressBar.setVisibility(View.GONE);
                TextView tvOrderCount = findViewById(R.id.tvAdminCustomerOrderCount);
                tvOrderCount.setText("0");
            }
        });
    }

    private void handleDisableToggle(boolean shouldDisable) {
        if (shouldDisable) {
            resetDisableSwitch();
            AdminDisableDialogHelper.showLockReasonDialog(this,
                    reason -> applyDisableChange(true, reason),
                    this::resetDisableSwitch);
        } else {
            resetDisableSwitch();
            AdminDisableDialogHelper.showUnlockReasonDialog(this,
                    reason -> applyDisableChange(false, reason),
                    this::resetDisableSwitch);
        }
    }

    private void applyDisableChange(boolean shouldDisable, @Nullable String reason) {
        switchDisabled.setEnabled(false);
        repository.setCustomerDisabled(customerUid, shouldDisable, reason, new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                disabled = shouldDisable;
                switchDisabled.setEnabled(true);
                updateStatusBadge(findViewById(R.id.tvAdminCustomerStatus));
                if (shouldDisable && !TextUtils.isEmpty(reason)) {
                    cardDisabledReason.setVisibility(View.VISIBLE);
                    tvDisabledReason.setText(reason);
                } else {
                    cardDisabledReason.setVisibility(View.GONE);
                }
                resetDisableSwitch();
                Toast.makeText(AdminCustomerDetailActivity.this,
                        shouldDisable ? "Đã khóa và gửi thông báo cho khách" : "Đã mở khóa và gửi thông báo cho khách",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                switchDisabled.setEnabled(true);
                resetDisableSwitch();
                Toast.makeText(AdminCustomerDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetDisableSwitch() {
        switchDisabled.setOnCheckedChangeListener(null);
        switchDisabled.setChecked(disabled);
        switchDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            handleDisableToggle(isChecked);
        });
    }

    private void updateStatusBadge(TextView tvStatus) {
        if (disabled) {
            tvStatus.setText(R.string.admin_customer_locked);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        } else {
            tvStatus.setText(R.string.admin_customer_active);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        }
    }

    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(this, AdminOrderDetailActivity.class);
        intent.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(intent);
    }

    private void addInfoRow(LinearLayout parent, int labelRes, @Nullable String value,
                            @DrawableRes int iconRes) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_admin_customer_info_row, parent, false);
        ImageView icon = row.findViewById(R.id.imgInfoIcon);
        TextView tvLabel = row.findViewById(R.id.tvInfoLabel);
        TextView tvValue = row.findViewById(R.id.tvInfoValue);
        icon.setImageResource(iconRes);
        tvLabel.setText(getString(labelRes));
        tvValue.setText(TextUtils.isEmpty(value) ? "—" : value);
        parent.addView(row);
    }

    private String formatAuthProvider(@Nullable String authProvider) {
        if (TextUtils.isEmpty(authProvider)) return "—";
        switch (authProvider.toLowerCase(Locale.ROOT)) {
            case "google": return "Google";
            case "facebook": return "Facebook";
            case "password": return "Số điện thoại / Mật khẩu";
            default: return authProvider;
        }
    }

    private static String initialLetter(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
