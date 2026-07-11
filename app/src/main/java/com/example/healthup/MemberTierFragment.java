package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.adapters.LoyaltyVoucherAdapter;
import com.example.models.Voucher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemberTierFragment extends Fragment {

    private static final long MUC_VIP = 5_000_000;

    private TextView tvSpentAmount, tvNextRankInfo;
    private TextView tvMemberBadge, tvCurrentRankTitle, tvVipTitle;
    private ProgressBar pbLoyalty;
    private ImageView ivCheckMember, ivCheckVip, ivLockVip;
    private View cardMember, cardVip;
    
    private LoyaltyVoucherAdapter shippingAdapter, discountAdapter;
    private final List<Voucher> shippingFull = new ArrayList<>();
    private final List<Voucher> discountFull = new ArrayList<>();
    private final List<Voucher> shippingVisible = new ArrayList<>();
    private final List<Voucher> discountVisible = new ArrayList<>();
    
    private boolean isShippingExpanded = false;
    private boolean isDiscountExpanded = false;
    private static final int DISPLAY_LIMIT = 2;
    private String userTier = "Member";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_member_tier, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup views
        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        tvSpentAmount = view.findViewById(R.id.tvSpentAmount);
        tvNextRankInfo = view.findViewById(R.id.tvNextRankInfo);
        tvMemberBadge = view.findViewById(R.id.tvMemberBadge);
        tvCurrentRankTitle = view.findViewById(R.id.tvCurrentRankTitle);
        tvVipTitle = view.findViewById(R.id.tvVipTitle);
        pbLoyalty = view.findViewById(R.id.pbLoyalty);
        ivCheckMember = view.findViewById(R.id.ivCheck);
        ivCheckVip = view.findViewById(R.id.ivCheckVip);
        ivLockVip = view.findViewById(R.id.ivLock);
        cardMember = (View) tvCurrentRankTitle.getParent();
        cardVip = view.findViewById(R.id.cardVip);
        
        setupVoucherSections(view);

        view.findViewById(R.id.rowNutritionSuggestion).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), DietLandingActivity.class));
        });

        loadUserData();

        return view;
    }

    private void setupVoucherSections(View view) {
        RecyclerView rvShippingVouchers = view.findViewById(R.id.rvShippingVouchers);
        RecyclerView rvDiscountVouchers = view.findViewById(R.id.rvDiscountVouchers);
        
        LoyaltyVoucherAdapter.OnUseNowClickListener useNowListener = voucher -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_category);
                }
            }
        };

        shippingAdapter = new LoyaltyVoucherAdapter(requireContext(), shippingVisible, useNowListener);
        discountAdapter = new LoyaltyVoucherAdapter(requireContext(), discountVisible, useNowListener);

        rvShippingVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDiscountVouchers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvShippingVouchers.setAdapter(shippingAdapter);
        rvDiscountVouchers.setAdapter(discountAdapter);

        view.findViewById(R.id.btnViewAllShipping).setOnClickListener(v -> {
            isShippingExpanded = !isShippingExpanded;
            updateLists();
        });

        view.findViewById(R.id.btnViewAllDiscount).setOnClickListener(v -> {
            isDiscountExpanded = !isDiscountExpanded;
            updateLists();
        });
    }

    private void updateLists() {
        View view = getView();
        if (view == null) return;

        shippingVisible.clear();
        if (isShippingExpanded || shippingFull.size() <= DISPLAY_LIMIT) {
            shippingVisible.addAll(shippingFull);
            TextView btn = view.findViewById(R.id.btnViewAllShipping);
            if (btn != null) btn.setText(shippingFull.size() <= DISPLAY_LIMIT ? "" : "Thu gọn");
        } else {
            shippingVisible.addAll(shippingFull.subList(0, DISPLAY_LIMIT));
            TextView btn = view.findViewById(R.id.btnViewAllShipping);
            if (btn != null) btn.setText("Xem tất cả");
        }
        shippingAdapter.notifyDataSetChanged();

        discountVisible.clear();
        if (isDiscountExpanded || discountFull.size() <= DISPLAY_LIMIT) {
            discountVisible.addAll(discountFull);
            TextView btn = view.findViewById(R.id.btnViewAllDiscount);
            if (btn != null) btn.setText(discountFull.size() <= DISPLAY_LIMIT ? "" : "Thu gọn");
        } else {
            discountVisible.addAll(discountFull.subList(0, DISPLAY_LIMIT));
            TextView btn = view.findViewById(R.id.btnViewAllDiscount);
            if (btn != null) btn.setText("Xem tất cả");
        }
        discountAdapter.notifyDataSetChanged();
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("orders")
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("status", "delivered")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;

                    double actualSpent = 0;
                    for (DocumentSnapshot orderDoc : queryDocumentSnapshots) {
                        Double total = orderDoc.getDouble("totalPrice");
                        if (total != null) actualSpent += total;
                    }

                    userTier = (actualSpent >= MUC_VIP) ? "VIP" : "Member";
                    shippingAdapter.setUserTier(userTier);
                    discountAdapter.setUserTier(userTier);
                    
                    bindDataWithSpentAmount((long) actualSpent);
                    loadVouchersFromDb();
                    
                    db.collection("users").document(currentUser.getUid())
                            .update("spentAmount", actualSpent);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        bindDataWithSpentAmount(0L);
                        Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadVouchersFromDb() {
        db.collection("promoCodes")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    shippingFull.clear();
                    discountFull.clear();
                    for (DocumentSnapshot doc : snapshot) {
                        Voucher v = parseVoucher(doc);
                        if (v != null) {
                            if (v.getType() == Voucher.Type.SHIPPING) {
                                shippingFull.add(v);
                            } else {
                                discountFull.add(v);
                            }
                        }
                    }
                    sortVouchers(shippingFull);
                    sortVouchers(discountFull);
                    updateLists();
                });
    }

    private void sortVouchers(List<Voucher> list) {
        list.sort((v1, v2) -> {
            boolean m1 = isTierMatch(v1);
            boolean m2 = isTierMatch(v2);
            if (m1 && !m2) return -1;
            if (!m1 && m2) return 1;
            return 0;
        });
    }

    private boolean isTierMatch(Voucher v) {
        String req = v.getRequiredTier();
        if (req == null || req.isEmpty() || req.equalsIgnoreCase("Member")) return true;
        return userTier.equalsIgnoreCase(req);
    }

    private Voucher parseVoucher(DocumentSnapshot doc) {
        Voucher v = new Voucher();
        v.setId(doc.getId());
        String code = doc.getString("code");
        if (code == null) code = doc.getId();
        v.setCode(code);
        v.setTitle(code);
        v.setDescription(doc.getString("description"));
        
        String reqTier = doc.getString("requiredTier");
        if (reqTier == null) reqTier = doc.getString("tier");
        if (code.toUpperCase().startsWith("VIP")) reqTier = "VIP";
        v.setRequiredTier(reqTier);
        
        com.google.firebase.Timestamp expiry = doc.getTimestamp("expiryDate");
        if (expiry != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            v.setExpiryDate(sdf.format(expiry.toDate()));
        } else {
            v.setExpiryDate("31/12/2027");
        }

        String upperCode = code.toUpperCase();
        if (upperCode.contains("SHIP") || upperCode.contains("FREE")) {
            v.setType(Voucher.Type.SHIPPING);
        } else {
            v.setType(Voucher.Type.DISCOUNT);
        }
        return v;
    }

    private void bindDataWithSpentAmount(long spent) {
        boolean isVip = spent >= MUC_VIP;
        NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

        tvSpentAmount.setText(String.format("%s VND", vnFormat.format(spent)));
        tvMemberBadge.setText(isVip ? "VIP" : "Member");
        
        if (isVip) {
            tvNextRankInfo.setText(R.string.loyalty_vip_achieved);
            pbLoyalty.setProgress(100);
            tvCurrentRankTitle.setText(R.string.loyalty_rank_vip_label);
            ivCheckMember.setVisibility(View.GONE);
            cardMember.setBackgroundResource(R.drawable.bg_card_white);
            tvVipTitle.setText(R.string.loyalty_rank_vip_label);
            ivLockVip.setVisibility(View.GONE);
            ivCheckVip.setVisibility(View.VISIBLE);
            cardVip.setBackgroundResource(R.drawable.bg_rank_card_current);
            cardVip.setElevation(2f);
        } else {
            long conLai = MUC_VIP - spent;
            tvNextRankInfo.setText(getString(R.string.loyalty_next_rank_hint, vnFormat.format(conLai)));
            pbLoyalty.setProgress((int) ((spent * 100) / MUC_VIP));
            tvCurrentRankTitle.setText(getString(R.string.loyalty_current_rank_label));
            ivCheckMember.setVisibility(View.VISIBLE);
            cardMember.setBackgroundResource(R.drawable.bg_rank_card_current);
            tvVipTitle.setText(R.string.loyalty_rank_vip);
            ivLockVip.setVisibility(View.VISIBLE);
            ivCheckVip.setVisibility(View.GONE);
            cardVip.setBackgroundResource(R.drawable.bg_rank_card_locked);
            cardVip.setElevation(0f);
        }
    }
}
