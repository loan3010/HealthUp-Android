package com.example.healthup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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

        binding.btnBack.setOnClickListener(v -> finish());
        
        setupRecyclerView();
        setupListeners();
        loadLinkedMethods();
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
                if (userId == null) return;
                new AlertDialog.Builder(PaymentInfoActivity.this)
                        .setTitle("Hủy liên kết")
                        .setMessage("Bạn có chắc muốn hủy liên kết phương thức này?")
                        .setPositiveButton("Hủy liên kết", (d, w) -> {
                            // Xóa local
                            removeFromLocalCache(item.getType());
                            // Xóa server
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
        // 1. Load từ Local Cache trước (đảm bảo luôn nhớ)
        linkedMethods.addAll(loadFromLocalCache());
        
        if (userId != null) {
            db.collection("users").document(userId).collection("paymentMethods")
                .get().addOnSuccessListener(snapshot -> {
                    // Tránh trùng lặp với local
                    Set<String> types = new HashSet<>();
                    for(PaymentAccount p : linkedMethods) types.add(p.getType());

                    for (QueryDocumentSnapshot doc : snapshot) {
                        PaymentAccount acc = doc.toObject(PaymentAccount.class);
                        acc.setId(doc.getId());
                        if (!types.contains(acc.getType())) {
                            linkedMethods.add(acc);
                            types.add(acc.getType());
                        }
                    }
                    updateUI();
                }).addOnFailureListener(e -> updateUI());
        } else {
            updateUI();
        }
    }

    private void updateUI() {
        boolean hasLinked = !linkedMethods.isEmpty();
        binding.layoutEmpty.setVisibility(hasLinked ? View.GONE : View.VISIBLE);
        binding.layoutLinked.setVisibility(hasLinked ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();

        binding.cardAddZalo.setVisibility(isLinked(PaymentAccount.TYPE_ZALOPAY) ? View.GONE : View.VISIBLE);
        binding.cardAddMoMo.setVisibility(isLinked(PaymentAccount.TYPE_MOMO) ? View.GONE : View.VISIBLE);
        binding.cardAddVnpay.setVisibility(isLinked(PaymentAccount.TYPE_VNPAY) ? View.GONE : View.VISIBLE);
        binding.cardAddAtm.setVisibility(isLinked(PaymentAccount.TYPE_ATM) ? View.GONE : View.VISIBLE);
        binding.cardAddLinkedBank.setVisibility(isLinked(PaymentAccount.TYPE_LINKED_BANK) ? View.GONE : View.VISIBLE);
        binding.cardAddCard.setVisibility(isLinked(PaymentAccount.TYPE_CARD) ? View.GONE : View.VISIBLE);

        if (isSelectMode && targetType != null) {
            for (PaymentAccount acc : linkedMethods) {
                // Logic linh hoạt cho Thẻ
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
        
        ((TextView)v.findViewById(R.id.tvDialogTitle)).setText(title);
        v.findViewById(R.id.btnClose).setOnClickListener(view -> dialog.dismiss());

        if (type.equals(PaymentAccount.TYPE_ATM)) {
            ((TextView)v.findViewById(R.id.tvLabelNumber)).setText("Số thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelName)).setText("Tên chủ thẻ:");
            ((TextView)v.findViewById(R.id.tvLabelDate)).setText("Ngày phát hành:");
        } else if (type.equals(PaymentAccount.TYPE_LINKED_BANK)) {
            v.findViewById(R.id.rowCCCD).setVisibility(View.VISIBLE);
            v.findViewById(R.id.rowPhone).setVisibility(View.VISIBLE);
        }

        v.findViewById(R.id.btnSave).setOnClickListener(view -> {
            String bank = ((Spinner)v.findViewById(R.id.spinnerBank)).getSelectedItem().toString();
            String num = ((EditText)v.findViewById(R.id.etNumber)).getText().toString().trim();
            if (num.isEmpty()) { Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show(); return; }
            saveAccount(new PaymentAccount(type, bank, "**** " + (num.length() > 4 ? num.substring(num.length() - 4) : num)), dialog);
        });
        dialog.show();
    }

    private void saveAccount(PaymentAccount acc, AlertDialog dialog) {
        saveToLocalCache(acc); // ✅ LUÔN LƯU LOCAL ĐỂ GHI NHỚ TRƯỚC
        
        if (userId != null) {
            db.collection("users").document(userId).collection("paymentMethods")
                .add(acc).addOnSuccessListener(ref -> {
                    if (dialog != null) dialog.dismiss();
                    Toast.makeText(this, "Liên kết thành công", Toast.LENGTH_SHORT).show();
                    loadLinkedMethods();
                }).addOnFailureListener(e -> {
                    // Nếu lỗi quyền (PERMISSION_DENIED), vẫn coi là thành công cho Demo
                    if (dialog != null) dialog.dismiss();
                    Toast.makeText(this, "Liên kết thành công (Ghi nhớ thiết bị)", Toast.LENGTH_SHORT).show();
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
