package com.example.healthup;

import android.os.Bundle;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PromoCouponFragment extends Fragment {

    private ImageButton btnBack;
    private EditText etVoucherCode;
    private Button btnApply, btnConfirm;
    private RecyclerView rvVouchers, rvVouchersDiscount;
    private TextView tvTotalDiscount, tvShippingHeader;

    private VoucherAdapter adapterShipping, adapterDiscount;
    private List<Voucher> shippingList, discountList;
    
    private List<Voucher> previouslySelected = new ArrayList<>();
    private double orderTotal = 0;
    private double shippingFee = 0;
    private boolean hasVisited = false;

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
        loadVoucherData();

        btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        btnApply.setOnClickListener(v -> {
            String code = etVoucherCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(getContext(), "Vui lòng nhập mã voucher", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Mã " + code + " không hợp lệ hoặc đã hết hạn", Toast.LENGTH_SHORT).show();
            }
        });

        btnConfirm.setOnClickListener(v -> confirmSelection());

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
    }

    private void setupRecyclerViews() {
        shippingList = new ArrayList<>();
        adapterShipping = new VoucherAdapter(requireContext(), shippingList, voucher -> {
            if (voucher.isSelected()) {
                for (Voucher v : shippingList) if (v != voucher) v.setSelected(false);
                adapterShipping.notifyDataSetChanged();
            }
            updateTotalDiscount();
        });
        rvVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchers.setAdapter(adapterShipping);

        discountList = new ArrayList<>();
        adapterDiscount = new VoucherAdapter(requireContext(), discountList, voucher -> {
            if (voucher.isSelected()) {
                for (Voucher v : discountList) if (v != voucher) v.setSelected(false);
                adapterDiscount.notifyDataSetChanged();
            }
            updateTotalDiscount();
        });
        rvVouchersDiscount.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchersDiscount.setAdapter(adapterDiscount);
    }

    private void loadVoucherData() {
        FirebaseManager.getInstance().getVouchers().addOnSuccessListener(snapshot -> {
            if (!isAdded()) return;
            
            shippingList.clear();
            discountList.clear();
            
            if (snapshot != null && !snapshot.isEmpty()) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    Voucher v = parseVoucherFromPromoCode(doc);
                    if (v == null) continue;

                    // ✅ CHỈ HIỂN THỊ NẾU ĐỦ ĐIỀU KIỆN ĐƠN TỐI THIỂU
                    if (orderTotal >= v.getMinOrderAmount()) {
                        if (v.getType() == Voucher.Type.SHIPPING) {
                            // ✅ NẾU CHỌN GIAO TIÊU CHUẨN (phí = 0) THÌ KHÔNG HIỆN MÃ GIẢM SHIP
                            if (shippingFee > 0) {
                                shippingList.add(v);
                            }
                        } else {
                            discountList.add(v);
                        }
                    }
                }
            }
            
            // Xử lý ẩn/hiện tiêu đề VẬN CHUYỂN
            if (tvShippingHeader != null) {
                tvShippingHeader.setVisibility(shippingList.isEmpty() ? View.GONE : View.VISIBLE);
                rvVouchers.setVisibility(shippingList.isEmpty() ? View.GONE : View.VISIBLE);
            }

            syncSelections();
        }).addOnFailureListener(e -> {
            syncSelections();
        });
    }

    private void checkAndAddFallbacks() {
        if (shippingList.isEmpty()) {
            shippingList.add(new Voucher("v_ship_free", "FREE SHIP", "Miễn phí vận chuyển toàn quốc", 21000, "31/12/2025", Voucher.Type.SHIPPING));
        }
        if (discountList.isEmpty()) {
            discountList.add(new Voucher("v_healthup5", "HEALTHUP5", "Giảm 5% cho đơn hàng HealthUp", 5, "31/12/2025", Voucher.Type.DISCOUNT));
        }
    }

    private Voucher parseVoucherFromPromoCode(com.google.firebase.firestore.DocumentSnapshot doc) {
        Boolean active = doc.getBoolean("isActive");
        if (active != null && !active) return null;

        String code = doc.getString("code");
        if (code == null) code = doc.getId();

        Voucher v = new Voucher();
        v.setId(doc.getId());
        v.setCode(code);
        v.setTitle(code);
        String desc = doc.getString("description");
        v.setDescription(desc);
        
        v.setMinOrderAmount(getDouble(doc, "minOrderValue"));
        
        // ✅ ĐỌC GIÁ TRỊ GIẢM GIÁ ĐA LUỒNG
        double amount = getDouble(doc, "discountAmount");
        if (amount == 0) amount = getDouble(doc, "discountValue");
        if (amount == 0) amount = getDouble(doc, "value");
        if (amount == 0) amount = getDouble(doc, "amount");
        if (amount == 0) amount = getDouble(doc, "discount_amount");

        double percent = getDouble(doc, "discountPercent");
        if (percent > 0) amount = percent;

        // ✅ FALLBACK: Tách số từ mô tả nếu các trường value đều trống
        if (amount == 0 && desc != null) {
            amount = extractNumberFromDesc(desc);
        }
        v.setDiscountAmount(amount);

        // Xử lý Ngày hết hạn
        com.google.firebase.Timestamp expiry = doc.getTimestamp("expiryDate");
        if (expiry != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            v.setExpiryDate(sdf.format(expiry.toDate()));
        } else {
            v.setExpiryDate("30/08/2026"); // Fallback theo ảnh của user
        }

        // Phân loại Type
        String upperCode = code.toUpperCase();
        if (upperCode.contains("SHIP") || upperCode.contains("FREE")) {
            v.setType(Voucher.Type.SHIPPING);
        } else if (upperCode.contains("CASHBACK") || upperCode.contains("TIER")) {
            v.setType(Voucher.Type.CASHBACK);
        } else {
            v.setType(Voucher.Type.DISCOUNT);
        }

        return v;
    }

    private double extractNumberFromDesc(String desc) {
        try {
            String cleaned = desc.replaceAll("[^0-9]", " ");
            String[] parts = cleaned.trim().split("\\s+");
            for (String p : parts) {
                if (p.length() >= 2) {
                    double val = Double.parseDouble(p);
                    if (val > 100) return val;
                    if (val > 0) return val;
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private double getDouble(com.google.firebase.firestore.DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception e) {}
        }
        return 0;
    }

    private void syncSelections() {
        if (hasVisited && !previouslySelected.isEmpty()) {
            // Nếu đã từng vào và có mã đã chọn, giữ nguyên lựa chọn cũ
            for (Voucher prev : previouslySelected) {
                for (Voucher cur : shippingList) if (cur.getId().equals(prev.getId())) cur.setSelected(true);
                for (Voucher cur : discountList) if (cur.getId().equals(prev.getId())) cur.setSelected(true);
            }
        } else {
            // ✅ TỰ ĐỘNG CHỌN MÃ TỐT NHẤT THỎA MÃN ĐIỀU KIỆN
            autoSelectBestVoucher(shippingList);
            autoSelectBestVoucher(discountList);
        }

        if (adapterShipping != null) adapterShipping.notifyDataSetChanged();
        if (adapterDiscount != null) adapterDiscount.notifyDataSetChanged();
        updateTotalDiscount();
    }

    private void autoSelectBestVoucher(List<Voucher> list) {
        Voucher best = null;
        double maxSaving = -1;

        for (Voucher v : list) {
            // Kiểm tra điều kiện đơn tối thiểu từ tổng tiền đơn hàng
            if (orderTotal >= v.getMinOrderAmount()) {
                double saving = calculateSavingForTotal(v);
                
                if (saving > maxSaving) {
                    maxSaving = saving;
                    best = v;
                }
            }
        }

        if (best != null) {
            best.setSelected(true);
        }
    }

    private void updateTotalDiscount() {
        double totalItemSaving = 0;
        double totalShipSaving = 0;

        for (Voucher v : shippingList) {
            if (v.isSelected()) {
                totalShipSaving += calculateSavingForTotal(v);
            }
        }
        for (Voucher v : discountList) {
            if (v.isSelected()) {
                totalItemSaving += calculateSavingForTotal(v);
            }
        }

        // Khớp logic với Checkout: Giới hạn mức giảm
        double finalShipDiscount = Math.min(totalShipSaving, shippingFee);
        double finalItemDiscount = Math.min(totalItemSaving, orderTotal);
        double totalSaving = finalShipDiscount + finalItemDiscount;

        tvTotalDiscount.setText(String.format(Locale.getDefault(), "Tiết kiệm %,.0fđ", totalSaving).replace(",", "."));
    }

    private double calculateSavingForTotal(Voucher v) {
        double val = v.getDiscountAmount();

        // ✅ ĐỒNG BỘ LOGIC VỚI CHECKOUT: Kiểm tra điều kiện "Giao nhanh"
        if (v.getType() == Voucher.Type.SHIPPING) {
            String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
            String code = (v.getCode() != null) ? v.getCode().toLowerCase() : "";
            
            boolean isFastVoucher = desc.contains("giao nhanh") || code.contains("fast") || desc.contains("2 giờ");
            boolean isFastShipping = (shippingFee > 25000); // 45k là fast
            
            if (isFastVoucher && !isFastShipping) {
                return 0; // Không áp dụng cho giao thường
            }

            // Nếu giá trị <= 100 thì đó là %
            if (val > 0 && val <= 100) {
                return (val / 100.0) * shippingFee;
            }
        } else {
            // Giảm giá sản phẩm %
            if (val > 0 && val <= 100) {
                return (val / 100.0) * orderTotal;
            }
        }

        return val;
    }

    private void confirmSelection() {
        ArrayList<Voucher> resultList = new ArrayList<>();
        for (Voucher v : shippingList) if (v.isSelected()) resultList.add(v);
        for (Voucher v : discountList) if (v.isSelected()) resultList.add(v);

        Bundle result = new Bundle();
        result.putSerializable("selected_vouchers", resultList);
        getParentFragmentManager().setFragmentResult("voucher_result", result);

        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
}
