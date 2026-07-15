package com.example.healthup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapters.PaymentAccountAdapter;
import com.example.healthup.databinding.ActivityPaymentInfoBinding;
import com.example.models.PaymentAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PaymentInfoActivity extends AppCompatActivity {
    public static final String EXTRA_SELECT_MODE = "select_mode";
    public static final String EXTRA_TARGET_TYPE = "target_type";
    
    private ActivityPaymentInfoBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private final List<PaymentAccount> linkedMethods = new ArrayList<>();
    private PaymentAccountAdapter adapter;
    private boolean isSelectMode = false;
    private String targetType = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isSelectMode = getIntent().getBooleanExtra(EXTRA_SELECT_MODE, false);
        targetType = getIntent().getStringExtra(EXTRA_TARGET_TYPE);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        // Xử lý chế độ khách
        if (userId == null && !isSelectMode) {
            setupGuestMode();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        
        setupRecyclerView();
        setupListeners();
        loadLinkedMethods();
    }

    private void setupGuestMode() {
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.sectionLinked.setVisibility(View.GONE);
        binding.tvAddMethodTitle.setVisibility(View.GONE);
        binding.cardAddZalo.setVisibility(View.GONE);
        binding.cardAddMoMo.setVisibility(View.GONE);
        binding.cardAddVnpay.setVisibility(View.GONE);
        binding.cardAddAtm.setVisibility(View.GONE);
        binding.cardAddLinkedBank.setVisibility(View.GONE);
        binding.cardAddCard.setVisibility(View.GONE);
        
        binding.btnBack.setOnClickListener(v -> finish());
        
        com.example.healthup.util.GuestLoginRequiredHelper.bind(binding.getRoot(), this);
        com.example.healthup.util.GuestRecommendationsHelper.bind(binding.getRoot(), this);
    }

    private void setupRecyclerView() {
        adapter = new PaymentAccountAdapter(linkedMethods, new PaymentAccountAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(PaymentAccount item) {
                if (isSelectMode) {
                    returnResultAndFinish(item);
                }
            }

            @Override
            public void onDeleteClick(PaymentAccount item) {
                if (userId == null) {
                    removeFromLocalCache(item.getType());
                    loadLinkedMethods();
                    return;
                }
                new AlertDialog.Builder(PaymentInfoActivity.this)
                        .setTitle("Hủy liên kết")
                        .setMessage("Bạn có chắc muốn hủy liên kết phương thức này?")
                        .setPositiveButton("Hủy liên kết", (d, w) -> {
                            removeFromLocalCache(item.getType());
                            if (item.getId() != null) {
                                db.collection("users").document(userId).collection("paymentMethods")
                                        .document(item.getId()).delete().addOnSuccessListener(v -> loadLinkedMethods());
                            } else {
                                loadLinkedMethods();
                            }
                        })
                        .setNegativeButton("Bỏ qua", null).show();
            }
        });
        binding.rvLinkedMethods.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLinkedMethods.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnLinkZalo.setOnClickListener(v -> showAuthDialog("Ví ZaloPay", PaymentAccount.TYPE_ZALOPAY));
        binding.btnLinkMoMo.setOnClickListener(v -> showAuthDialog("Ví MoMo", PaymentAccount.TYPE_MOMO));
        binding.btnLinkVnpay.setOnClickListener(v -> showAuthDialog("VNPay QR", PaymentAccount.TYPE_VNPAY));
        binding.btnLinkAtm.setOnClickListener(v -> showFormDialog(PaymentAccount.TYPE_ATM, "Thẻ ATM nội địa"));
        binding.btnLinkBank.setOnClickListener(v -> showFormDialog(PaymentAccount.TYPE_LINKED_BANK, "Liên kết ngân hàng"));
        binding.btnLinkCard.setOnClickListener(v -> showFormDialog(PaymentAccount.TYPE_CARD, "Thẻ tín dụng / Ghi nợ"));
    }

    private void showAuthDialog(String name, String type) {
        new AlertDialog.Builder(this)
                .setTitle("Cấp quyền truy cập")
                .setMessage("Cho phép HealthUp liên kết và truy cập thông tin từ " + name + " để bảo mật giao dịch?")
                .setPositiveButton("Cho phép", (d, w) -> {
                    PaymentAccount acc = new PaymentAccount(type, name, "Đã cấp quyền");
                    saveAccount(acc, null);
                })
                .setNegativeButton("Từ chối", null).show();
    }

    private void loadLinkedMethods() {
        linkedMethods.clear();
        // 1. Load từ Local Cache trước để hiển thị ngay lập tức
        linkedMethods.addAll(loadFromLocalCache());
        updateUI(); 
        
        if (userId != null) {
            db.collection("users").document(userId).collection("paymentMethods")
                .get().addOnSuccessListener(snapshot -> {
                    Set<String> types = new HashSet<>();
                    for(PaymentAccount p : linkedMethods) types.add(p.getType());

                    boolean added = false;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        PaymentAccount acc = doc.toObject(PaymentAccount.class);
                        acc.setId(doc.getId());
                        if (!types.contains(acc.getType())) {
                            linkedMethods.add(acc);
                            types.add(acc.getType());
                            added = true;
                        }
                    }
                    if (added) updateUI();
                }).addOnFailureListener(e -> updateUI());
        }
    }

    private void updateUI() {
        if (binding == null || adapter == null) return;

        boolean hasLinked = !linkedMethods.isEmpty();
        binding.layoutEmpty.setVisibility(hasLinked ? View.GONE : View.VISIBLE);
        binding.sectionLinked.setVisibility(hasLinked ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();

        binding.cardAddZalo.setVisibility(isLinked(PaymentAccount.TYPE_ZALOPAY) ? View.GONE : View.VISIBLE);
        binding.cardAddMoMo.setVisibility(isLinked(PaymentAccount.TYPE_MOMO) ? View.GONE : View.VISIBLE);
        binding.cardAddVnpay.setVisibility(isLinked(PaymentAccount.TYPE_VNPAY) ? View.GONE : View.VISIBLE);
        binding.cardAddAtm.setVisibility(isLinked(PaymentAccount.TYPE_ATM) ? View.GONE : View.VISIBLE);
        binding.cardAddLinkedBank.setVisibility(isLinked(PaymentAccount.TYPE_LINKED_BANK) ? View.GONE : View.VISIBLE);
        binding.cardAddCard.setVisibility(isLinked(PaymentAccount.TYPE_CARD) ? View.GONE : View.VISIBLE);

        // Logic chọn phương thức khi từ màn Checkout sang
        if (isSelectMode && targetType != null) {
            for (PaymentAccount acc : linkedMethods) {
                if (targetType.equals(acc.getType()) || 
                   (targetType.equals(PaymentAccount.TYPE_CARD) && PaymentAccount.TYPE_ATM.equals(acc.getType()))) {
                    returnResultAndFinish(acc);
                    return;
                }
            }
        }
    }

    private void returnResultAndFinish(PaymentAccount acc) {
        Intent result = new Intent();
        result.putExtra("payment_account", acc);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private boolean isLinked(String type) {
        for (PaymentAccount acc : linkedMethods) if (type.equals(acc.getType())) return true;
        return false;
    }

    private void showFormDialog(String type, String title) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_card_info, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        TextView tvTitle = v.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(title);
        v.findViewById(R.id.btnClose).setOnClickListener(view -> dialog.dismiss());

        Spinner spinner = v.findViewById(R.id.spinnerBank);
        if (type.equals(PaymentAccount.TYPE_ATM)) {
            ((TextView)v.findViewById(R.id.tvLabelNumber)).setText("Số thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelName)).setText("Tên chủ thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelDate)).setText("Ngày phát hành:");
        } else if (type.equals(PaymentAccount.TYPE_CARD)) {
            ((TextView)v.findViewById(R.id.tvLabelBank)).setText("Loại thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelNumber)).setText("Số thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelName)).setText("Chủ thẻ:");
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.card_types, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        } else if (type.equals(PaymentAccount.TYPE_LINKED_BANK)) {
            v.findViewById(R.id.rowCCCD).setVisibility(View.VISIBLE);
            v.findViewById(R.id.rowPhone).setVisibility(View.VISIBLE);
        }

        v.findViewById(R.id.btnSave).setOnClickListener(view -> {
            String provider = spinner.getSelectedItem().toString();
            String num = ((EditText)v.findViewById(R.id.etNumber)).getText().toString().trim();
            if (num.length() < 6) { Toast.makeText(this, "Số tài khoản/thẻ không hợp lệ", Toast.LENGTH_SHORT).show(); return; }
            
            String displayId = "**** " + num.substring(num.length() - 4);
            PaymentAccount newAcc = new PaymentAccount(type, provider, displayId);
            saveAccount(newAcc, dialog);
        });
        dialog.show();
    }

    private void saveAccount(PaymentAccount acc, AlertDialog dialog) {
        saveToLocalCache(acc); 
        
        if (userId != null) {
            db.collection("users").document(userId).collection("paymentMethods")
                .add(acc).addOnSuccessListener(ref -> {
                    if (dialog != null) dialog.dismiss();
                    Toast.makeText(this, "Liên kết thành công", Toast.LENGTH_SHORT).show();
                    loadLinkedMethods();
                }).addOnFailureListener(e -> {
                    if (dialog != null) dialog.dismiss();
                    loadLinkedMethods();
                });
        } else {
            if (dialog != null) dialog.dismiss();
            loadLinkedMethods();
        }
    }

    private void saveToLocalCache(PaymentAccount acc) {
        getSharedPreferences("payment_cache", MODE_PRIVATE).edit()
            .putBoolean("linked_" + acc.getType(), true)
            .putString("name_" + acc.getType(), acc.getProviderName())
            .putString("id_" + acc.getType(), acc.getAccountIdentifier())
            .apply();
    }

    private void removeFromLocalCache(String type) {
        getSharedPreferences("payment_cache", MODE_PRIVATE).edit().remove("linked_" + type).apply();
    }

    private List<PaymentAccount> loadFromLocalCache() {
        List<PaymentAccount> list = new ArrayList<>();
        String[] types = {PaymentAccount.TYPE_MOMO, PaymentAccount.TYPE_ZALOPAY, PaymentAccount.TYPE_VNPAY, PaymentAccount.TYPE_ATM, PaymentAccount.TYPE_CARD, PaymentAccount.TYPE_LINKED_BANK};
        android.content.SharedPreferences prefs = getSharedPreferences("payment_cache", MODE_PRIVATE);
        for(String t : types) {
            if (prefs.getBoolean("linked_" + t, false)) {
                list.add(new PaymentAccount(t, prefs.getString("name_" + t, ""), prefs.getString("id_" + t, "")));
            }
        }
        return list;
    }
}
