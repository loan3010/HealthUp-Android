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
    private TextView btnViewAllShipping, btnViewAllDiscount;

    private VoucherAdapter adapterShipping, adapterDiscount;
    private List<Voucher> shippingListFull = new ArrayList<>();
    private List<Voucher> discountListFull = new ArrayList<>();
    private List<Voucher> shippingListVisible = new ArrayList<>();
    private List<Voucher> discountListVisible = new ArrayList<>();
    
    private boolean isShippingExpanded = false;
    private boolean isDiscountExpanded = false;
    private static final int VOUCHER_LIMIT = 2; // Hiển thị 2 cái đầu
    
    private List<Voucher> previouslySelected = new ArrayList<>();
    private double orderTotal = 0;
    private double shippingFee = 0;
    private boolean hasVisited = false;
    private String userTier = "Member"; // Default

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
        loadUserDataAndVouchers(); // Combined

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
        btnViewAllShipping = view.findViewById(R.id.btnViewAllShipping);
        btnViewAllDiscount = view.findViewById(R.id.btnViewAllDiscount);

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
            if (voucher.isSelected()) {
                for (Voucher v : shippingListFull) if (v != voucher) v.setSelected(false);
            }
            updateTotalDiscount();
            adapterShipping.notifyDataSetChanged();
        });
        rvVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchers.setAdapter(adapterShipping);

        adapterDiscount = new VoucherAdapter(requireContext(), discountListVisible, orderTotal, shippingFee, userTier, voucher -> {
            if (voucher.isSelected()) {
                for (Voucher v : discountListFull) if (v != voucher) v.setSelected(false);
            }
            updateTotalDiscount();
            adapterDiscount.notifyDataSetChanged();
        });
        rvVouchersDiscount.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVouchersDiscount.setAdapter(adapterDiscount);
    }

    private void loadUserDataAndVouchers() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
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
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    Voucher v = parseVoucherFromPromoCode(doc);
                    if (v == null) continue;

                    // Phân loại TẤT CẢ voucher (Shopee style) vào 2 list riêng biệt
                    if (v.getType() == Voucher.Type.SHIPPING) {
                        shippingListFull.add(v);
                    } else {
                        discountListFull.add(v);
                    }
                }
            }
            
            // Xử lý ẩn/hiện tiêu đề VẬN CHUYỂN dựa trên list full
            if (tvShippingHeader != null) {
                boolean hasShipping = !shippingListFull.isEmpty();
                tvShippingHeader.setVisibility(hasShipping ? View.VISIBLE : View.GONE);
                rvVouchers.setVisibility(hasShipping ? View.VISIBLE : View.GONE);
            }

            // ✅ SẮP XẾP VOUCHER: Đưa cái tốt nhất/khả dụng lên đầu
            sortVouchers(shippingListFull);
            sortVouchers(discountListFull);

            updateVisibleLists();
            syncSelections();
        }).addOnFailureListener(e -> {
            syncSelections();
        });
    }

    private void updateVisibleLists() {
        shippingListVisible.clear();
        if (isShippingExpanded || shippingListFull.size() <= VOUCHER_LIMIT) {
            shippingListVisible.addAll(shippingListFull);
            if (btnViewAllShipping != null) {
                if (shippingListFull.size() <= VOUCHER_LIMIT) {
                    btnViewAllShipping.setVisibility(View.GONE);
                } else {
                    btnViewAllShipping.setVisibility(View.VISIBLE);
                    btnViewAllShipping.setText("Thu gọn");
                }
            }
        } else {
            shippingListVisible.addAll(shippingListFull.subList(0, VOUCHER_LIMIT));
            if (btnViewAllShipping != null) {
                btnViewAllShipping.setVisibility(View.VISIBLE);
                btnViewAllShipping.setText("Xem tất cả");
            }
        }

        discountListVisible.clear();
        if (isDiscountExpanded || discountListFull.size() <= VOUCHER_LIMIT) {
            discountListVisible.addAll(discountListFull);
            if (btnViewAllDiscount != null) {
                if (discountListFull.size() <= VOUCHER_LIMIT) {
                    btnViewAllDiscount.setVisibility(View.GONE);
                } else {
                    btnViewAllDiscount.setVisibility(View.VISIBLE);
                    btnViewAllDiscount.setText("Thu gọn");
                }
            }
        } else {
            discountListVisible.addAll(discountListFull.subList(0, VOUCHER_LIMIT));
            if (btnViewAllDiscount != null) {
                btnViewAllDiscount.setVisibility(View.VISIBLE);
                btnViewAllDiscount.setText("Xem tất cả");
            }
        }

        if (adapterShipping != null) adapterShipping.notifyDataSetChanged();
        if (adapterDiscount != null) adapterDiscount.notifyDataSetChanged();
    }

    private void sortVouchers(List<Voucher> list) {
        java.util.Collections.sort(list, (v1, v2) -> {
            boolean e1 = isVoucherEligible(v1);
            boolean e2 = isVoucherEligible(v2);

            if (e1 && !e2) return -1;
            if (!e1 && e2) return 1;

            if (e1) {
                // Cả hai đều đủ điều kiện: Sắp xếp theo giá trị giảm dần
                double s1 = calculateSavingForTotal(v1);
                double s2 = calculateSavingForTotal(v2);
                return Double.compare(s2, s1);
            } else {
                // Ưu tiên theo Tier trước
                boolean t1 = isTierMatch(v1);
                boolean t2 = isTierMatch(v2);
                if (t1 && !t2) return -1;
                if (!t1 && t2) return 1;

                // Cả hai đều chưa đủ tiền: Sắp xếp theo cái nào gần đủ nhất
                double d1 = v1.getMinOrderAmount() - orderTotal;
                double d2 = v2.getMinOrderAmount() - orderTotal;
                return Double.compare(d1, d2);
            }
        });
    }

    private boolean isVoucherEligible(Voucher v) {
        return orderTotal >= v.getMinOrderAmount() && isTierMatch(v);
    }

    private boolean isTierMatch(Voucher v) {
        String req = v.getRequiredTier();
        if (req == null || req.isEmpty() || req.equalsIgnoreCase("Member")) return true;
        return userTier.equalsIgnoreCase(req);
    }

    private void checkAndAddFallbacks() {
        if (shippingListFull.isEmpty()) {
            shippingListFull.add(new Voucher("v_ship_free", "FREE SHIP", "Miễn phí vận chuyển toàn quốc", 21000, "31/12/2025", Voucher.Type.SHIPPING));
        }
        if (discountListFull.isEmpty()) {
            discountListFull.add(new Voucher("v_healthup5", "HEALTHUP5", "Giảm 5% cho đơn hàng HealthUp", 5, "31/12/2025", Voucher.Type.DISCOUNT));
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
        
        // ✅ ĐỌC HẠNG YÊU CẦU: Ưu tiên requiredTier, fallback sang tier
        String reqTier = doc.getString("requiredTier");
        if (reqTier == null) reqTier = doc.getString("tier");
        
        // ✅ CỰC KỲ QUAN TRỌNG: Nếu tên mã bắt đầu bằng "VIP", ép buộc hạng VIP
        if (code.toUpperCase().startsWith("VIP")) {
            reqTier = "VIP";
        }
        v.setRequiredTier(reqTier);
        
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
            // Nếu đã từng vào và có mã đã chọn, giữ nguyên lựa chọn cũ (nhưng phải check lại Tier)
            for (Voucher prev : previouslySelected) {
                for (Voucher cur : shippingListFull) {
                    if (java.util.Objects.equals(cur.getId(), prev.getId()) && isTierMatch(cur)) {
                        cur.setSelected(true);
                    }
                }
                for (Voucher cur : discountListFull) {
                    if (java.util.Objects.equals(cur.getId(), prev.getId()) && isTierMatch(cur)) {
                        cur.setSelected(true);
                    }
                }
            }
        } else {
            // ✅ TỰ ĐỘNG CHỌN MÃ TỐT NHẤT THỎA MÃN ĐIỀU KIỆN
            autoSelectBestVoucher(shippingListFull);
            autoSelectBestVoucher(discountListFull);
        }

        updateVisibleLists();
        updateTotalDiscount();
    }

    private void autoSelectBestVoucher(List<Voucher> list) {
        Voucher best = null;
        double maxSaving = -1;

        for (Voucher v : list) {
            // Kiểm tra điều kiện đơn tối thiểu và Tier
            if (isVoucherEligible(v)) {
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
        int selectedCount = 0;

        for (Voucher v : shippingListFull) {
            if (v.isSelected()) {
                totalShipSaving += calculateSavingForTotal(v);
                selectedCount++;
            }
        }
        for (Voucher v : discountListFull) {
            if (v.isSelected()) {
                totalItemSaving += calculateSavingForTotal(v);
                selectedCount++;
            }
        }

        // Khớp logic với Checkout: Giới hạn mức giảm
        double finalShipDiscount = Math.min(totalShipSaving, shippingFee);
        double finalItemDiscount = Math.min(totalItemSaving, orderTotal);
        double totalSaving = finalShipDiscount + finalItemDiscount;

        tvTotalDiscount.setText(String.format(Locale.getDefault(), "Tiết kiệm %,.0fđ", totalSaving).replace(",", "."));
        
        // Cập nhật text hiển thị số lượng
        TextView tvFooterCount = getView() != null ? getView().findViewById(R.id.tvSelectedCountFooter) : null;
        if (tvFooterCount != null) {
            tvFooterCount.setText(String.format(Locale.getDefault(), "ĐÃ CHỌN %d VOUCHER", selectedCount));
        }
        
        TextView tvHeader = getView() != null ? getView().findViewById(R.id.tvBestVoucherHeader) : null;
        if (tvHeader != null) {
            if (selectedCount > 0) {
                tvHeader.setText(String.format(Locale.getDefault(), 
                    "Chúng tôi đã chọn %d voucher tốt nhất giúp bạn tiết kiệm nhiều nhất.", selectedCount));
            } else {
                tvHeader.setText(R.string.cart_voucher_hint);
            }
        }
    }

    private double calculateSavingForTotal(Voucher v) {
        // ✅ ĐỒNG BỘ LOGIC: Kiểm tra điều kiện đơn tối thiểu và Tier
        if (!isVoucherEligible(v)) {
            return 0;
        }

        double val = v.getDiscountAmount();
        String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
        String code = (v.getCode() != null) ? v.getCode().toLowerCase() : "";

        if (v.getType() == Voucher.Type.SHIPPING) {
            // ✅ Chỉ giảm 100% nếu giá trị là 100
            if (val == 100) {
                return shippingFee;
            }

            boolean isFastVoucher = desc.contains("giao nhanh") || code.contains("fast") || desc.contains("2 giờ");
            boolean isFastShipping = (shippingFee > 25000); 
            if (isFastVoucher && !isFastShipping) return 0;

            if (val > 0 && val < 100) {
                return (val / 100.0) * shippingFee;
            }
            return val;
        } else {
            // Giảm giá sản phẩm %
            if (val > 0 && val <= 100) {
                return (val / 100.0) * orderTotal;
            }
            return val;
        }
    }

    private void confirmSelection() {
        ArrayList<Voucher> resultList = new ArrayList<>();
        for (Voucher v : shippingListFull) if (v.isSelected()) resultList.add(v);
        for (Voucher v : discountListFull) if (v.isSelected()) resultList.add(v);

        Bundle result = new Bundle();
        result.putSerializable("selected_vouchers", resultList);
        getParentFragmentManager().setFragmentResult("voucher_result", result);

        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }
}
