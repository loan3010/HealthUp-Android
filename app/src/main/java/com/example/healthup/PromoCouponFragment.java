package com.example.healthup;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.VoucherAdapter;
import com.example.models.Voucher;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PromoCouponFragment extends Fragment {

    private ImageButton btnBack;
    private EditText etVoucherCode;
    private Button btnApply, btnConfirm;
    private RecyclerView rvVouchers, rvVouchersDiscount;
    private TextView tvTotalDiscount, tvShippingHeader, btnViewAllShipping, btnViewAllDiscount;
    private TextView tvSelectedCountFooter;

    private VoucherAdapter adapterShipping, adapterDiscount;
    private List<Voucher> shippingListFull = new ArrayList<>();
    private List<Voucher> discountListFull = new ArrayList<>();
    private List<Voucher> shippingListVisible = new ArrayList<>();
    private List<Voucher> discountListVisible = new ArrayList<>();
    
    private boolean isShippingExpanded = false;
    private boolean isDiscountExpanded = false;
    private static final int VOUCHER_LIMIT = 2;
    
    private List<Voucher> previouslySelected = new ArrayList<>();
    private double orderTotal = 0;
    private double shippingFee = 0;
    private boolean hasVisited = false;
    private String userTier = "Member";

    public PromoCouponFragment() {
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_promo_coupon, container, false);

        if (getArguments() != null) {
            Serializable data = getArguments().getSerializable("selected_vouchers");
            if (data instanceof List) {
                previouslySelected = (List<Voucher>) data;
            }
            orderTotal = getArguments().getDouble("order_total", 0);
            shippingFee = getArguments().getDouble("shipping_fee", 0);
            hasVisited = getArguments().getBoolean("has_visited", false);
        }

        initViews(view);
        setupRecyclerViews();
        loadUserDataAndVouchers();

        return view;
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        etVoucherCode = view.findViewById(R.id.etVoucherCode);
        btnApply = view.findViewById(R.id.btnApply);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        rvVouchers = view.findViewById(R.id.rvVouchers);
        rvVouchersDiscount = view.findViewById(R.id.rvVouchersDiscount);
        tvTotalDiscount = view.findViewById(R.id.tvTotalDiscount);
        tvShippingHeader = view.findViewById(R.id.tvShippingHeader);
        btnViewAllShipping = view.findViewById(R.id.btnViewAllShipping);
        btnViewAllDiscount = view.findViewById(R.id.btnViewAllDiscount);
        tvSelectedCountFooter = view.findViewById(R.id.tvSelectedCountFooter);

        btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        
        etVoucherCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.green_button)));
                } else {
                    btnApply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#A0A0A0")));
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnApply.setOnClickListener(v -> {
            String codeInput = etVoucherCode.getText().toString().trim().toUpperCase();
            if (codeInput.isEmpty()) {
                Toast.makeText(getContext(), "Vui lòng nhập mã voucher", Toast.LENGTH_SHORT).show();
                return;
            }
            searchAndApplyVoucher(codeInput);
        });

        btnConfirm.setOnClickListener(v -> confirmSelection());

        btnViewAllShipping.setOnClickListener(v -> {
            isShippingExpanded = !isShippingExpanded;
            updateVisibleLists();
        });

        btnViewAllDiscount.setOnClickListener(v -> {
            isDiscountExpanded = !isDiscountExpanded;
            updateVisibleLists();
        });
    }

    private void setupRecyclerViews() {
        adapterShipping = new VoucherAdapter(requireContext(), shippingListVisible, orderTotal, shippingFee, userTier, voucher -> {
            handleVoucherClickLogic(voucher, shippingListFull);
        });
        rvVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchers.setAdapter(adapterShipping);

        adapterDiscount = new VoucherAdapter(requireContext(), discountListVisible, orderTotal, shippingFee, userTier, voucher -> {
            handleVoucherClickLogic(voucher, discountListFull);
        });
        rvVouchersDiscount.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchersDiscount.setAdapter(adapterDiscount);
    }

    private void handleVoucherClickLogic(Voucher voucher, List<Voucher> listFull) {
        if (voucher.isSelected()) {
            voucher.setSelected(false);
        } else {
            // DUY NHẤT 1 MÃ MỖI LOẠI
            for (Voucher item : listFull) item.setSelected(false);
            voucher.setSelected(true);
        }
        updateTotalDiscount();
        if (adapterShipping != null) adapterShipping.notifyDataSetChanged();
        if (adapterDiscount != null) adapterDiscount.notifyDataSetChanged();
    }

    private void loadUserDataAndVouchers() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .get().addOnSuccessListener(doc -> {
                        String tier = doc.getString("tier");
                        if (tier != null) {
                            userTier = tier;
                            if (adapterShipping != null) adapterShipping.setUserTier(userTier);
                            if (adapterDiscount != null) adapterDiscount.setUserTier(userTier);
                        }
                        loadVoucherData();
                    }).addOnFailureListener(e -> loadVoucherData());
        } else {
            loadVoucherData();
        }
    }

    private void loadVoucherData() {
        FirebaseManager.getInstance().getVouchers().addOnSuccessListener(snapshot -> {
            if (!isAdded()) return;
            shippingListFull.clear();
            discountListFull.clear();
            if (snapshot != null && !snapshot.isEmpty()) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Voucher v = parseVoucherFromPromoCode(doc);
                    if (v != null) {
                        if (v.getType() == Voucher.Type.SHIPPING) shippingListFull.add(v);
                        else discountListFull.add(v);
                    }
                }
            }
            if (tvShippingHeader != null) {
                tvShippingHeader.setVisibility(shippingListFull.isEmpty() ? View.GONE : View.VISIBLE);
            }
            sortVouchers(shippingListFull);
            sortVouchers(discountListFull);
            syncSelections();
        }).addOnFailureListener(e -> syncSelections());
    }

    private void searchAndApplyVoucher(String code) {
        // Search memory
        Voucher localMatch = null;
        for (Voucher v : shippingListFull) if (v.getCode().equalsIgnoreCase(code)) localMatch = v;
        if (localMatch == null) {
            for (Voucher v : discountListFull) if (v.getCode().equalsIgnoreCase(code)) localMatch = v;
        }

        if (localMatch != null) {
            showVoucherConfirmation(localMatch);
            return;
        }

        // Query Firestore
        FirebaseFirestore.getInstance().collection("promoCodes").document(code).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Voucher v = parseVoucherFromPromoCode(doc);
                        if (v != null) showVoucherConfirmation(v);
                    } else {
                        Toast.makeText(getContext(), "Mã giảm giá không tồn tại hoặc đã hết hạn", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showVoucherConfirmation(Voucher voucherToApply) {
        if (!isVoucherEligible(voucherToApply)) {
            Toast.makeText(getContext(), "Đơn hàng chưa đủ điều kiện áp dụng mã này", Toast.LENGTH_LONG).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận áp mã")
                .setMessage("Bạn có muốn áp dụng mã " + voucherToApply.getCode() + " cho đơn hàng này không? (Sẽ thay thế mã cũ cùng loại)")
                .setPositiveButton("Áp dụng", (dialog, which) -> {
                    hasVisited = false; // Tắt flag để ghi đè lựa chọn cũ
                    
                    Voucher finalV = voucherToApply;
                    List<Voucher> targetList = (finalV.getType() == Voucher.Type.SHIPPING) ? shippingListFull : discountListFull;
                    
                    boolean alreadyInList = false;
                    for (Voucher item : targetList) {
                        if (item.getId().equals(finalV.getId())) {
                            finalV = item;
                            alreadyInList = true;
                            break;
                        }
                    }
                    if (!alreadyInList) targetList.add(0, finalV);
                    
                    // Xóa các mã cùng loại khác
                    for (Voucher item : targetList) if (item != finalV) item.setSelected(false);
                    finalV.setSelected(true);
                    
                    syncSelections();
                    Toast.makeText(getContext(), "Đã áp dụng mã " + finalV.getCode(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void syncSelections() {
        // ✅ Ưu tiên 1: Nếu có lựa chọn cũ từ màn hình trước và chưa có tương tác mới
        if (hasVisited && !previouslySelected.isEmpty()) {
            for (Voucher prev : previouslySelected) {
                for (Voucher cur : shippingListFull) {
                    if (Objects.equals(cur.getId(), prev.getId()) && isTierMatch(cur)) cur.setSelected(true);
                }
                for (Voucher cur : discountListFull) {
                    if (Objects.equals(cur.getId(), prev.getId()) && isTierMatch(cur)) cur.setSelected(true);
                }
            }
            hasVisited = false; 
        } 
        
        // ✅ Ưu tiên 2: Nếu sau khi đồng bộ hoặc vào lần đầu mà chưa có mã nào được chọn, tự động chọn mã tốt nhất
        if (!hasAnyVoucherSelected()) {
            autoSelectBestVoucher(shippingListFull);
            autoSelectBestVoucher(discountListFull);
        }

        updateVisibleLists();
        updateTotalDiscount();
    }

    private boolean hasAnyVoucherSelected() {
        for (Voucher v : shippingListFull) if (v.isSelected()) return true;
        for (Voucher v : discountListFull) if (v.isSelected()) return true;
        return false;
    }

    private void autoSelectBestVoucher(List<Voucher> list) {
        Voucher best = null;
        double maxSaving = -1;
        
        // Reset trạng thái trước khi tìm kiếm tự động
        for (Voucher v : list) v.setSelected(false);

        for (Voucher v : list) {
            if (isVoucherEligible(v)) {
                double saving = calculateSavingForTotal(v);
                if (saving > maxSaving && saving > 0) {
                    maxSaving = saving;
                    best = v;
                }
            }
        }
        if (best != null) {
            best.setSelected(true);
        }
    }

    private void updateVisibleLists() {
        updateList(shippingListFull, shippingListVisible, btnViewAllShipping, isShippingExpanded);
        updateList(discountListFull, discountListVisible, btnViewAllDiscount, isDiscountExpanded);
        if (adapterShipping != null) adapterShipping.notifyDataSetChanged();
        if (adapterDiscount != null) adapterDiscount.notifyDataSetChanged();
    }

    private void updateList(List<Voucher> full, List<Voucher> visible, TextView btn, boolean expanded) {
        visible.clear();
        if (expanded || full.size() <= VOUCHER_LIMIT) {
            visible.addAll(full);
            if (btn != null) btn.setText(full.size() <= VOUCHER_LIMIT ? "" : "Thu gọn");
        } else {
            visible.addAll(full.subList(0, VOUCHER_LIMIT));
            if (btn != null) btn.setText("Xem tất cả");
        }
        if (btn != null) btn.setVisibility(full.size() <= VOUCHER_LIMIT ? View.GONE : View.VISIBLE);
    }

    private void sortVouchers(List<Voucher> list) {
        Collections.sort(list, (v1, v2) -> {
            boolean e1 = isVoucherEligible(v1), e2 = isVoucherEligible(v2);
            if (e1 && !e2) return -1;
            if (!e1 && e2) return 1;
            if (e1) return Double.compare(calculateSavingForTotal(v2), calculateSavingForTotal(v1));
            return Double.compare(v1.getMinOrderAmount() - orderTotal, v2.getMinOrderAmount() - orderTotal);
        });
    }

    private boolean isVoucherEligible(Voucher v) {
        return orderTotal >= v.getMinOrderAmount() && isTierMatch(v);
    }

    private boolean isTierMatch(Voucher v) {
        String req = v.getRequiredTier();
        return req == null || req.isEmpty() || req.equalsIgnoreCase("Member") || userTier.equalsIgnoreCase(req);
    }

    private Voucher parseVoucherFromPromoCode(DocumentSnapshot doc) {
        Boolean active = doc.getBoolean("isActive");
        if (active != null && !active) return null;
        String code = doc.getString("code");
        if (code == null) code = doc.getId();
        Voucher v = new Voucher();
        v.setId(doc.getId());
        v.setCode(code);
        v.setTitle(code);
        v.setDescription(doc.getString("description"));
        v.setMinOrderAmount(getDouble(doc, "minOrderValue"));
        String reqTier = doc.getString("requiredTier");
        if (reqTier == null) reqTier = doc.getString("tier");
        if (code.toUpperCase().startsWith("VIP")) reqTier = "VIP";
        v.setRequiredTier(reqTier);
        double amount = getDouble(doc, "discountAmount");
        if (amount == 0) amount = getDouble(doc, "discountValue");
        if (amount == 0) amount = getDouble(doc, "discountPercent");
        v.setDiscountAmount(amount);
        com.google.firebase.Timestamp expiry = doc.getTimestamp("expiryDate");
        if (expiry != null) v.setExpiryDate(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(expiry.toDate()));
        else v.setExpiryDate("30/08/2026");
        String upperCode = code.toUpperCase();
        if (upperCode.contains("SHIP") || upperCode.contains("FREE")) v.setType(Voucher.Type.SHIPPING);
        else if (upperCode.contains("CASHBACK") || upperCode.contains("TIER")) v.setType(Voucher.Type.CASHBACK);
        else v.setType(Voucher.Type.DISCOUNT);
        return v;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) try { return Double.parseDouble((String) val); } catch (Exception ignored) {}
        return 0;
    }

    private void updateTotalDiscount() {
        double itemSaving = 0, shipSaving = 0;
        int count = 0;
        Voucher selectedShipVoucher = null;
        Voucher selectedDiscountVoucher = null;
        boolean hasIneligibleSelected = false;

        for (Voucher v : shippingListFull) {
            if (v.isSelected()) {
                if (isVoucherEligible(v)) {
                    shipSaving += calculateSavingForTotal(v);
                    count++;
                    selectedShipVoucher = v;
                } else {
                    hasIneligibleSelected = true;
                }
            }
        }
        for (Voucher v : discountListFull) {
            if (v.isSelected()) {
                if (isVoucherEligible(v)) {
                    itemSaving += calculateSavingForTotal(v);
                    count++;
                    selectedDiscountVoucher = v;
                } else {
                    hasIneligibleSelected = true;
                }
            }
        }

        if (tvSelectedCountFooter != null) {
            tvSelectedCountFooter.setText(String.format(Locale.getDefault(), "ĐÃ CHỌN %d VOUCHER", count));
        }

        if (count > 0) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            
            // Colors
            int orangeRedColor = 0xFFFF4E00; 
            int greenColor = 0xFF2E7D32; 
            int goldColor = 0xFFFBC02D;

            if (selectedShipVoucher != null) {
                int start = ssb.length();
                if (shippingFee <= 21000) { 
                    ssb.append("Đã áp dụng mã vận chuyển");
                } else {
                    double finalShipDiscount = Math.min(shipSaving, shippingFee);
                    ssb.append("Giảm vận chuyển ").append(String.format(Locale.getDefault(), "%,.0fđ", finalShipDiscount).replace(",", "."));
                }
                
                int colorToUse = "VIP".equalsIgnoreCase(selectedShipVoucher.getRequiredTier()) ? goldColor : orangeRedColor;
                ssb.setSpan(new ForegroundColorSpan(colorToUse), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (selectedDiscountVoucher != null) {
                if (ssb.length() > 0) ssb.append(", ");
                int start = ssb.length();
                double finalItemDiscount = Math.min(itemSaving, orderTotal);
                ssb.append("Giảm sản phẩm ").append(String.format(Locale.getDefault(), "%,.0fđ", finalItemDiscount).replace(",", "."));
                
                int colorToUse = "VIP".equalsIgnoreCase(selectedDiscountVoucher.getRequiredTier()) ? goldColor : greenColor;
                ssb.setSpan(new ForegroundColorSpan(colorToUse), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            tvTotalDiscount.setText(ssb);
        } else {
            if (hasIneligibleSelected) {
                tvTotalDiscount.setText("Đơn hàng chưa đủ điều kiện để áp mã, bạn cần mua thêm");
            } else {
                tvTotalDiscount.setText("Tiết kiệm 0đ");
            }
        }
    }

    private double calculateSavingForTotal(Voucher v) {
        if (!isVoucherEligible(v)) return 0;
        double val = v.getDiscountAmount();
        String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
        if (v.getType() == Voucher.Type.SHIPPING) {
            if (val == 100) return shippingFee;
            if (desc.contains("giao nhanh") && shippingFee <= 25000) return 0;
            return (val > 0 && val < 100) ? (val / 100.0) * shippingFee : val;
        } else {
            return (val > 0 && val <= 100) ? (val / 100.0) * orderTotal : val;
        }
    }

    private void confirmSelection() {
        ArrayList<Voucher> res = new ArrayList<>();
        for (Voucher v : shippingListFull) if (v.isSelected()) res.add(v);
        for (Voucher v : discountListFull) if (v.isSelected()) res.add(v);
        Bundle b = new Bundle();
        b.putSerializable("selected_vouchers", res);
        getParentFragmentManager().setFragmentResult("voucher_result", b);
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
}
