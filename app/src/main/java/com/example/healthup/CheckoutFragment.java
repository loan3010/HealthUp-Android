package com.example.healthup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.CheckoutProductAdapter;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.PhoneVerifiedHelper;
import com.example.healthup.util.StockManager;
import com.example.healthup.util.TranslationManager;
import com.example.models.Address;
import com.example.models.CartItem;
import com.example.models.PaymentAccount;
import com.example.models.Voucher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CheckoutFragment extends Fragment {

    private final ActivityResultLauncher<Intent> paymentLinkLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    PaymentAccount acc = (PaymentAccount) result.getData().getSerializableExtra("payment_account");
                    if (acc != null) {
                        applyLinkedAccountSelection(acc);
                    }
                }
            });

    private List<CartItem> selectedItems = new ArrayList<>();
    private Address selectedAddress;
    private List<Voucher> selectedVouchers = new ArrayList<>();
    private String selectedPaymentMethod = "cod";
    private int lastValidPaymentRbId = R.id.rbCod;
    private boolean isManualSelection = false;
    private final List<PaymentAccount> userLinkedMethods = new ArrayList<>();
    private List<DocumentSnapshot> cachedVoucherDocs = null;
    private String userTier = "Member";
    private FirebaseFirestore db;
    private String userId;

    private double shippingFee = 21000;
    private double shippingDiscount = 0;

    private TextView tvRecipientInfo, tvAddressDetail, tvVoucherInfo;
    private TextView tvTotalItemPrice, tvShippingFee, tvShippingDiscount, tvVoucherDiscount, tvGrandTotal, tvFooterTotal;
    private TextView tvAppliedVoucherTitle, tvAppliedVoucherDesc, tvAgreeTerms;
    private View rowAddress, rowVoucherNoSelect, layoutVoucherApplied;
    private TextView btnRemoveVoucher, btnViewAllVoucher;
    private View layoutShippingStandard, layoutShippingFast;
    private TextView tvShippingStandardTitle, tvShippingStandardPrice, tvShippingStandardInfo;
    private TextView tvShippingFastTitle, tvShippingFastPrice, tvShippingFastInfo;
    private RecyclerView rvCheckoutProducts;
    private Button btnPlaceOrder;
    private RadioGroup radioGroupPayment;
    private CheckBox cbAgreeTerms;

    private View rowShopNote;
    private TextView tvShopNotePreview;
    private ImageView ivShopNoteArrow;
    private EditText etShopNote;
    private boolean isShopNoteExpanded = false;

    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener("address_result", this, (requestKey, result) -> {
            Address address = (Address) result.getSerializable("selected_address");
            if (address != null) {
                selectedAddress = address;
                renderAddress();
            }
        });

        getParentFragmentManager().setFragmentResultListener("voucher_result", this, (requestKey, result) -> {
            List<Voucher> vouchers = (List<Voucher>) result.getSerializable("selected_vouchers");
            if (vouchers != null) {
                this.selectedVouchers = new ArrayList<>(vouchers);
                this.isManualSelection = true;
                renderVouchers();
                calculateSummary();
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_checkout, container, false);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = (user != null) ? user.getUid() : null;

        if (getArguments() != null) {
            Serializable data;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data = getArguments().getSerializable("selected_items", Serializable.class);
            } else {
                data = getArguments().getSerializable("selected_items");
            }
            if (data instanceof List) {
                selectedItems = (List<CartItem>) data;
            }
            
            Serializable voucherData = getArguments().getSerializable("selected_vouchers");
            if (voucherData instanceof List) {
                selectedVouchers = (List<Voucher>) voucherData;
                isManualSelection = !selectedVouchers.isEmpty();
            }
        }

        bindViews(view);
        applyHeaderWindowInsets(view);
        setupListeners();
        hydrateSelectedItemImages();
        renderProductList();
        renderVouchers();
        loadDefaultAddress();
        loadUserTierAndVouchers(); 
        loadUserLinkedMethods();
        
        updateShippingSelection();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserLinkedMethods();
    }

    private void applyHeaderWindowInsets(View view) {
        View header = view.findViewById(R.id.header);
        if (header == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }

    private void loadUserLinkedMethods() {
        if (userId == null) return;
        db.collection("users").document(userId).collection("paymentMethods")
                .get()
                .addOnSuccessListener(snapshot -> {
                    userLinkedMethods.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot) {
                            PaymentAccount acc = doc.toObject(PaymentAccount.class);
                            if (acc != null) {
                                acc.setId(doc.getId());
                                userLinkedMethods.add(acc);
                            }
                        }
                    }
                });
    }

    private void loadUserTierAndVouchers() {
        if (userId != null) {
            db.collection("users").document(userId)
                    .get().addOnSuccessListener(doc -> {
                        String tier = doc.getString("tier");
                        if (tier != null) userTier = tier;
                        loadVouchers();
                    }).addOnFailureListener(e -> loadVouchers());
        } else {
            loadVouchers();
        }
    }

    private void loadVouchers() {
        FirebaseManager.getInstance().getVouchers().addOnSuccessListener(snapshot -> {
            if (snapshot != null) {
                cachedVoucherDocs = snapshot.getDocuments();
                if (!isManualSelection) {
                    performAutoSelection();
                }
            }
            if (isAdded()) {
                renderVouchers();
                calculateSummary();
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) calculateSummary();
        });
    }

    private void performAutoSelection() {
        if (cachedVoucherDocs == null || cachedVoucherDocs.isEmpty()) return;

        double itemsTotal = getItemsTotal();
        Voucher bestShipping = null;
        double maxShipSaving = 0;
        Voucher bestDiscount = null;
        double maxDiscountSaving = 0;

        for (DocumentSnapshot doc : cachedVoucherDocs) {
            Voucher v = parseVoucherFromDoc(doc);
            if (v == null || itemsTotal < v.getMinOrderAmount() || !isTierMatch(v)) continue;

            double saving = calculateSaving(v, itemsTotal);
            if (v.getType() == Voucher.Type.SHIPPING) {
                if (saving > maxShipSaving) { maxShipSaving = saving; bestShipping = v; }
            } else {
                if (saving > maxDiscountSaving) { maxDiscountSaving = saving; bestDiscount = v; }
            }
        }

        selectedVouchers.clear();
        if (bestShipping != null) { bestShipping.setSelected(true); selectedVouchers.add(bestShipping); }
        if (bestDiscount != null) { bestDiscount.setSelected(true); selectedVouchers.add(bestDiscount); }
    }

    private Voucher parseVoucherFromDoc(DocumentSnapshot doc) {
        Boolean active = doc.getBoolean("isActive");
        if (active != null && !active) return null;
        Voucher v = new Voucher();
        v.setId(doc.getId());
        String code = doc.getString("code");
        if (code == null || code.isEmpty()) code = doc.getId();
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
        v.setDiscountAmount(amount);
        String upperCode = code.toUpperCase();
        if (upperCode.contains("SHIP") || upperCode.contains("FREE")) v.setType(Voucher.Type.SHIPPING);
        else if (upperCode.contains("CASHBACK") || upperCode.contains("TIER")) v.setType(Voucher.Type.CASHBACK);
        else v.setType(Voucher.Type.DISCOUNT);
        return v;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) { try { return Double.parseDouble((String) val); } catch (Exception e) {} }
        return 0;
    }

    private boolean isTierMatch(Voucher v) {
        String req = v.getRequiredTier();
        if (req == null || req.isEmpty() || req.equalsIgnoreCase("Member")) return true;
        return userTier.equalsIgnoreCase(req);
    }

    private double calculateSaving(Voucher v, double itemsTotal) {
        return calculateSaving(v, itemsTotal, shippingFee);
    }

    private double calculateSaving(Voucher v, double itemsTotal, double currentShippingFee) {
        if (itemsTotal < v.getMinOrderAmount() || !isTierMatch(v)) return 0;
        double val = v.getDiscountAmount();
        String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
        if (v.getType() == Voucher.Type.SHIPPING) {
            if (val == 100) return currentShippingFee;
            if (desc.contains("giao nhanh") && currentShippingFee <= 25000) return 0;
            return (val > 0 && val < 100) ? (val / 100.0) * currentShippingFee : val;
        } else {
            return (val > 0 && val <= 100) ? (val / 100.0) * itemsTotal : val;
        }
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        tvRecipientInfo = view.findViewById(R.id.tvRecipientInfo);
        tvAddressDetail = view.findViewById(R.id.tvAddressDetail);
        tvTotalItemPrice = view.findViewById(R.id.tvTotalItemPrice);
        tvShippingFee = view.findViewById(R.id.tvShippingFee);
        tvShippingDiscount = view.findViewById(R.id.tvShippingDiscount);
        tvVoucherDiscount = view.findViewById(R.id.tvVoucherDiscount);
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal);
        tvFooterTotal = view.findViewById(R.id.tvFooterTotal);
        tvAppliedVoucherTitle = view.findViewById(R.id.tvAppliedVoucherTitle);
        tvAppliedVoucherDesc = view.findViewById(R.id.tvAppliedVoucherDesc);
        btnRemoveVoucher = view.findViewById(R.id.btnRemoveVoucher);
        btnViewAllVoucher = view.findViewById(R.id.btnViewAllVoucher);
        rowAddress = view.findViewById(R.id.rowAddress);
        rowVoucherNoSelect = view.findViewById(R.id.rowVoucherNoSelect);
        layoutVoucherApplied = view.findViewById(R.id.layoutVoucherApplied);
        layoutShippingStandard = view.findViewById(R.id.layoutShippingStandard);
        layoutShippingFast = view.findViewById(R.id.layoutShippingFast);
        
        tvShippingStandardTitle = view.findViewById(R.id.tvShippingStandardTitle);
        tvShippingStandardPrice = view.findViewById(R.id.tvShippingStandardPrice);
        tvShippingStandardInfo = view.findViewById(R.id.tvShippingStandardInfo);
        tvShippingFastTitle = view.findViewById(R.id.tvShippingFastTitle);
        tvShippingFastPrice = view.findViewById(R.id.tvShippingFastPrice);
        tvShippingFastInfo = view.findViewById(R.id.tvShippingFastInfo);

        rvCheckoutProducts = view.findViewById(R.id.rvCheckoutProducts);
        btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder);
        radioGroupPayment = view.findViewById(R.id.radioGroupPayment);
        cbAgreeTerms = view.findViewById(R.id.cbAgreeTerms);
        tvAgreeTerms = view.findViewById(R.id.tvAgreeTerms);
        rowShopNote = view.findViewById(R.id.rowShopNote);
        tvShopNotePreview = view.findViewById(R.id.tvShopNotePreview);
        ivShopNoteArrow = view.findViewById(R.id.ivShopNoteArrow);
        etShopNote = view.findViewById(R.id.etShopNote);
        rvCheckoutProducts.setLayoutManager(new LinearLayoutManager(getContext()));

        applyFooterWindowInsets(view);
        setupDeliveryDates();
    }

    private void applyFooterWindowInsets(View view) {
        View footer = view.findViewById(R.id.footer);
        if (footer == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(footer, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return windowInsets;
        });
    }

    private void setupDeliveryDates() {
        if (tvShippingStandardInfo == null) return;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("d 'Tháng' M", new Locale("vi", "VN"));
        
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 2);
        String start = sdf.format(calendar.getTime());
        
        // Cần reset lại calendar về ngày hiện tại rồi mới cộng 5 để chính xác là 5 ngày từ ngày đặt hàng
        calendar = java.util.Calendar.getInstance(); 
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 5);
        String end = sdf.format(calendar.getTime());
        
        tvShippingStandardInfo.setText("Đảm bảo nhận hàng từ " + start + " -\n" + end);
    }

    private void setupListeners() {
        setupTermsLink();
        rowAddress.setOnClickListener(v -> openAddressBook());
        rowVoucherNoSelect.setOnClickListener(v -> openVoucherList());
        btnViewAllVoucher.setOnClickListener(v -> openVoucherList());
        btnRemoveVoucher.setOnClickListener(v -> {
            selectedVouchers.clear();
            isManualSelection = true;
            renderVouchers();
            calculateSummary();
        });
        rowShopNote.setOnClickListener(v -> toggleShopNote());
        layoutShippingStandard.setOnClickListener(v -> { shippingFee = 21000; updateShippingSelection(); });
        layoutShippingFast.setOnClickListener(v -> { shippingFee = 45000; updateShippingSelection(); });
        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        setupPaymentListener();
    }

    private void setupTermsLink() {
        if (tvAgreeTerms == null) return;
        String fullText = "Đồng ý với Điều khoản";
        String linkPart = "Điều khoản";
        
        SpannableString ss = new SpannableString(fullText);
        int start = fullText.indexOf(linkPart);
        int end = start + linkPart.length();
        
        if (start >= 0) {
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new PolicyFragment())
                            .addToBackStack(null)
                            .commit();
                }
                
                @Override
                public void updateDrawState(@NonNull android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(Color.parseColor("#36873A")); // green_button color
                    ds.setUnderlineText(false);
                }
            };
            ss.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        tvAgreeTerms.setText(ss);
        tvAgreeTerms.setMovementMethod(LinkMovementMethod.getInstance());
        tvAgreeTerms.setHighlightColor(Color.TRANSPARENT);
    }

    private void setupPaymentListener() {
        radioGroupPayment.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCod) {
                selectedPaymentMethod = "cod";
                lastValidPaymentRbId = R.id.rbCod;
                return;
            }
            handlePaymentSelection(checkedId);
        });
    }

    private void handlePaymentSelection(int checkedId) {
        String targetType = null;
        if (checkedId == R.id.rbVnpay) targetType = PaymentAccount.TYPE_VNPAY;
        else if (checkedId == R.id.rbCard) targetType = PaymentAccount.TYPE_CARD;
        else if (checkedId == R.id.rbZaloPay) targetType = PaymentAccount.TYPE_ZALOPAY;
        else if (checkedId == R.id.rbMomo) targetType = PaymentAccount.TYPE_MOMO;
        else if (checkedId == R.id.rbLinkedBank) targetType = PaymentAccount.TYPE_LINKED_BANK;

        if (targetType != null) {
            checkAndLinkPaymentMethod(targetType, checkedId);
        }
    }

    private void checkAndLinkPaymentMethod(String type, int rbId) {
        if (userId == null) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để sử dụng tính năng này", Toast.LENGTH_SHORT).show();
            revertPaymentSelection();
            return;
        }

        // 1. Kiểm tra trong danh sách đã load từ Server
        PaymentAccount found = null;
        for (PaymentAccount acc : userLinkedMethods) {
            if (type.equals(acc.getType())) { found = acc; break; }
            if (type.equals(PaymentAccount.TYPE_CARD) && PaymentAccount.TYPE_ATM.equals(acc.getType())) { found = acc; break; }
        }

        // 2. ✅ KIỂM TRA THÊM TRONG BỘ NHỚ MÁY (Local Cache)
        if (found == null) {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("payment_cache", android.content.Context.MODE_PRIVATE);
            if (prefs.getBoolean("linked_" + type, false)) {
                found = new PaymentAccount(type, prefs.getString("name_" + type, ""), prefs.getString("id_" + type, ""));
            } else if (type.equals(PaymentAccount.TYPE_CARD) && prefs.getBoolean("linked_" + PaymentAccount.TYPE_ATM, false)) {
                found = new PaymentAccount(PaymentAccount.TYPE_ATM, prefs.getString("name_" + PaymentAccount.TYPE_ATM, ""), prefs.getString("id_" + PaymentAccount.TYPE_ATM, ""));
            }
        }

        if (found == null) {
            // CHƯA CÓ -> Mới hiện xác nhận chuyển trang
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setMessage("Bạn chưa liên kết với phương thức này, bạn có muốn thiết lập liên kết không?")
                    .setPositiveButton("Thiết lập ngay", (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), PaymentInfoActivity.class);
                        intent.putExtra(PaymentInfoActivity.EXTRA_SELECT_MODE, true);
                        intent.putExtra(PaymentInfoActivity.EXTRA_TARGET_TYPE, type);
                        paymentLinkLauncher.launch(intent);
                    })
                    .setNegativeButton("Để sau", (dialog, which) -> revertPaymentSelection())
                    .setCancelable(false)
                    .show();
            
            revertPaymentSelection(); 
        } else {
            // ✅ ĐÃ CÓ -> Tích chọn ngay, không hỏi lại
            selectedPaymentMethod = found.getType();
            lastValidPaymentRbId = rbId;
            Toast.makeText(getContext(), "Sử dụng: " + found.getProviderName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void revertPaymentSelection() {
        radioGroupPayment.post(() -> {
            radioGroupPayment.setOnCheckedChangeListener(null);
            radioGroupPayment.check(lastValidPaymentRbId);
            setupPaymentListener();
        });
    }

    private void applyLinkedAccountSelection(PaymentAccount acc) {
        int rbId = R.id.rbCod;
        switch (acc.getType()) {
            case PaymentAccount.TYPE_ZALOPAY: rbId = R.id.rbZaloPay; break;
            case PaymentAccount.TYPE_MOMO: rbId = R.id.rbMomo; break;
            case PaymentAccount.TYPE_VNPAY: rbId = R.id.rbVnpay; break;
            case PaymentAccount.TYPE_ATM:
            case PaymentAccount.TYPE_CARD: rbId = R.id.rbCard; break;
            case PaymentAccount.TYPE_LINKED_BANK: rbId = R.id.rbLinkedBank; break;
        }
        final int finalRbId = rbId;
        radioGroupPayment.post(() -> {
            radioGroupPayment.check(finalRbId);
            selectedPaymentMethod = acc.getType();
            lastValidPaymentRbId = finalRbId;
        });
    }

    private void loadDefaultAddress() {
        String effectiveUserId = (userId != null) ? userId : "guest_user";
        db.collection("users").document(effectiveUserId).collection("addresses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Address addr = doc.toObject(Address.class);
                            if (addr != null) { addr.setId(doc.getId()); if (addr.isDefault()) { selectedAddress = addr; break; } }
                        }
                        if (selectedAddress == null) {
                            DocumentSnapshot first = snapshot.getDocuments().get(0);
                            selectedAddress = first.toObject(Address.class);
                            if (selectedAddress != null) selectedAddress.setId(first.getId());
                        }
                    }
                    renderAddress();
                });
    }

    private void toggleShopNote() {
        isShopNoteExpanded = !isShopNoteExpanded;
        etShopNote.setVisibility(isShopNoteExpanded ? View.VISIBLE : View.GONE);
        ivShopNoteArrow.setRotation(isShopNoteExpanded ? 180f : 0f);
        if (isShopNoteExpanded) etShopNote.requestFocus();
        else { String note = etShopNote.getText().toString().trim(); tvShopNotePreview.setText(note.isEmpty() ? "Để lại lời nhắn" : note); }
    }

    private void updateShippingSelection() {
        boolean isStandard = (shippingFee <= 21000);
        layoutShippingStandard.setBackgroundResource(isStandard ? R.drawable.bg_shipping_selected : R.drawable.bg_shipping_unselected);
        layoutShippingFast.setBackgroundResource(isStandard ? R.drawable.bg_shipping_unselected : R.drawable.bg_shipping_selected);

        int greenColor = Color.parseColor("#36873A");
        int darkColor = getResources().getColor(R.color.text_dark);

        tvShippingStandardTitle.setTextColor(isStandard ? greenColor : darkColor);
        tvShippingStandardTitle.setTypeface(null, isStandard ? Typeface.BOLD : Typeface.NORMAL);

        tvShippingFastTitle.setTextColor(!isStandard ? greenColor : darkColor);
        tvShippingFastTitle.setTypeface(null, !isStandard ? Typeface.BOLD : Typeface.NORMAL);

        if (!isManualSelection) performAutoSelection();
        renderVouchers();
        calculateSummary();
        updateShippingDisplayPrices();
    }

    private void updateShippingDisplayPrices() {
        double itemsTotal = getItemsTotal();
        
        // Tính toán cho Giao tiêu chuẩn (21.000)
        double stdFee = 21000;
        double stdSaving = 0;
        for (Voucher v : selectedVouchers) {
            if (v.getType() == Voucher.Type.SHIPPING) stdSaving += calculateSaving(v, itemsTotal, stdFee);
        }
        stdSaving = Math.min(stdSaving, stdFee);
        formatShippingPriceText(tvShippingStandardPrice, stdFee, stdSaving, shippingFee <= 21000);

        // Tính toán cho Giao nhanh (45.000)
        double fastFee = 45000;
        double fastSaving = 0;
        for (Voucher v : selectedVouchers) {
            if (v.getType() == Voucher.Type.SHIPPING) fastSaving += calculateSaving(v, itemsTotal, fastFee);
        }
        fastSaving = Math.min(fastSaving, fastFee);
        formatShippingPriceText(tvShippingFastPrice, fastFee, fastSaving, shippingFee > 25000);
    }

    private void formatShippingPriceText(TextView textView, double originalFee, double saving, boolean isSelected) {
        int greenColor = Color.parseColor("#36873A");
        int darkColor = getResources().getColor(R.color.text_dark);
        int grayColor = getResources().getColor(R.color.text_gray);

        if (saving <= 0) {
            textView.setText(formatVnd(originalFee));
            textView.setTextColor(isSelected ? greenColor : darkColor);
            textView.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            return;
        }

        String originalStr = formatVnd(originalFee);
        String discountedStr = (originalFee - saving <= 0) ? "Miễn Phí" : formatVnd(originalFee - saving);
        String combined = originalStr + " " + discountedStr;

        SpannableString spannable = new SpannableString(combined);
        // Gạch ngang giá gốc
        spannable.setSpan(new StrikethroughSpan(), 0, originalStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(grayColor), 0, originalStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // Màu cho giá mới
        spannable.setSpan(new ForegroundColorSpan(greenColor), originalStr.length() + 1, combined.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        textView.setText(spannable);
        textView.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void openVoucherList() {
        PromoCouponFragment fragment = new PromoCouponFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable("selected_vouchers", new ArrayList<>(selectedVouchers));
        bundle.putDouble("order_total", getItemsTotal());
        bundle.putDouble("shipping_fee", shippingFee);
        bundle.putBoolean("has_visited", !selectedVouchers.isEmpty());
        fragment.setArguments(bundle);
        requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit();
    }

    private void renderVouchers() {
        if (selectedVouchers.isEmpty()) {
            rowVoucherNoSelect.setVisibility(View.VISIBLE);
            layoutVoucherApplied.setVisibility(View.GONE);
        } else {
            double itemsTotal = getItemsTotal();
            boolean hasEligible = false;
            for (Voucher v : selectedVouchers) if (itemsTotal >= v.getMinOrderAmount() && isTierMatch(v)) { hasEligible = true; break; }

            if (!hasEligible) {
                rowVoucherNoSelect.setVisibility(View.GONE);
                layoutVoucherApplied.setVisibility(View.VISIBLE);
                tvAppliedVoucherTitle.setText("Chưa đủ điều kiện áp mã");
                tvAppliedVoucherTitle.setTextColor(getResources().getColor(R.color.red_price));
                tvAppliedVoucherDesc.setText("Đơn hàng chưa đủ điều kiện để áp mã, bạn cần mua thêm");
                return;
            }

            rowVoucherNoSelect.setVisibility(View.GONE);
            layoutVoucherApplied.setVisibility(View.VISIBLE);
            tvAppliedVoucherTitle.setTextColor(getResources().getColor(R.color.green_button));
            
            boolean hasShipping = false;
            double itemDiscount = 0;
            boolean isFastShippingSelected = (shippingFee > 25000); 

            for (Voucher v : selectedVouchers) {
                if (itemsTotal >= v.getMinOrderAmount() && isTierMatch(v)) {
                    if (v.getType() == Voucher.Type.SHIPPING) hasShipping = true;
                    else itemDiscount += calculateSaving(v, itemsTotal);
                }
            }

            StringBuilder sb = new StringBuilder();
            if (hasShipping) {
                if (!isFastShippingSelected) sb.append("Đã áp dụng mã vận chuyển");
                else {
                    double shipSaving = 0;
                    for (Voucher v : selectedVouchers) if (v.getType() == Voucher.Type.SHIPPING && itemsTotal >= v.getMinOrderAmount() && isTierMatch(v)) shipSaving += calculateSaving(v, itemsTotal);
                    sb.append("Giảm vận chuyển ").append(currencyFormat.format(Math.min(shipSaving, shippingFee))).append("đ");
                }
            }
            if (itemDiscount > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Giảm sản phẩm ").append(currencyFormat.format(itemDiscount)).append("đ");
            }
            tvAppliedVoucherTitle.setText(sb.length() > 0 ? sb.toString() : "Đã áp dụng voucher");
            tvAppliedVoucherDesc.setText("Hệ thống đã tự động áp dụng mã hời nhất cho bạn");
        }
    }

    private void hydrateSelectedItemImages() { for (CartItem item : selectedItems) if (item.getImageUrl() == null || item.getImageUrl().isEmpty()) if (item.getProduct() != null && item.getProduct().getImageUrl() != null) item.setImageUrl(item.getProduct().getImageUrl()); }

    private void renderProductList() { rvCheckoutProducts.setAdapter(new CheckoutProductAdapter(selectedItems)); }

    private double getItemsTotal() { double total = 0; for (CartItem item : selectedItems) total += item.getPrice() * item.getQuantity(); return total; }

    private String formatVnd(double amount) { return currencyFormat.format(amount) + "đ"; }

    private void calculateSummary() {
        double itemsTotal = getItemsTotal();
        double totalItemSaving = 0, totalShipSaving = 0;
        for (Voucher v : selectedVouchers) {
            double saving = calculateSaving(v, itemsTotal);
            if (v.getType() == Voucher.Type.SHIPPING) totalShipSaving += saving; else totalItemSaving += saving;
        }
        double finalShipDiscount = Math.min(totalShipSaving, shippingFee);
        double finalItemDiscount = Math.min(totalItemSaving, itemsTotal);
        double grandTotal = Math.max(0, (itemsTotal - finalItemDiscount) + (shippingFee - finalShipDiscount));

        tvTotalItemPrice.setText(formatVnd(itemsTotal));
        tvShippingFee.setText(formatVnd(shippingFee));
        tvShippingDiscount.setText("-" + formatVnd(finalShipDiscount));
        tvVoucherDiscount.setText("-" + formatVnd(finalItemDiscount));
        tvGrandTotal.setText(formatVnd(grandTotal));
        tvFooterTotal.setText(formatVnd(grandTotal));
        updateShippingDisplayPrices();
    }

    private void openAddressBook() {
        AddressBookFragment fragment = new AddressBookFragment();
        Bundle bundle = new Bundle(); bundle.putBoolean("select_mode", true);
        fragment.setArguments(bundle);
        requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit();
    }

    private void renderAddress() {
        if (selectedAddress == null) {
            String currentLang = LocaleHelper.getLanguage(requireContext());
            tvRecipientInfo.setText(currentLang.equals("en") ? "No address" : "Chưa có địa chỉ");
            tvAddressDetail.setText(currentLang.equals("en") ? "Click to choose delivery address" : "Bấm để chọn địa chỉ giao hàng");
            return;
        }
        tvRecipientInfo.setText(selectedAddress.getRecipientName() + "   (" + selectedAddress.getPhone() + ")");
        tvAddressDetail.setText(selectedAddress.getFullAddress());
    }

    private void placeOrder() {
        if (selectedAddress == null) { Toast.makeText(getContext(), "Vui lòng chọn địa chỉ giao hàng", Toast.LENGTH_SHORT).show(); return; }
        if (selectedItems.isEmpty()) { Toast.makeText(getContext(), "Không có sản phẩm để đặt hàng", Toast.LENGTH_SHORT).show(); return; }
        if (!cbAgreeTerms.isChecked()) { Toast.makeText(getContext(), "Vui lòng đồng ý với Điều khoản", Toast.LENGTH_SHORT).show(); return; }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems);
            requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new PhoneVerificationFragment()).addToBackStack(null).commit();
            return;
        }

        PhoneVerifiedHelper.requireForCheckout(new PhoneVerifiedHelper.Callback() {
            @Override public void onVerified() { if (isAdded()) continuePlaceOrder(user); }
            @Override public void onNeedPhoneVerification() { if (isAdded()) { Toast.makeText(getContext(), R.string.checkout_need_phone_verified, Toast.LENGTH_LONG).show(); CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems); requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new PhoneVerificationFragment()).addToBackStack(null).commit(); } }
            @Override public void onError(@NonNull String message) { if (isAdded()) Toast.makeText(getContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void continuePlaceOrder(@NonNull FirebaseUser user) {
        btnPlaceOrder.setEnabled(false);
        String currentLang = LocaleHelper.getLanguage(requireContext());
        btnPlaceOrder.setText(currentLang.equals("en") ? "Processing..." : "Đang xử lý...");
        String userId = user.getUid();

        double itemsTotal = getItemsTotal();
        double totalItemSaving = 0, totalShipSaving = 0;
        for (Voucher v : selectedVouchers) {
            double saving = calculateSaving(v, itemsTotal);
            if (v.getType() == Voucher.Type.SHIPPING) totalShipSaving += saving; else totalItemSaving += saving;
        }
        double finalShipDiscount = Math.min(totalShipSaving, shippingFee), finalItemDiscount = Math.min(totalItemSaving, itemsTotal);
        double finalAmount = Math.max(0, (itemsTotal - finalItemDiscount) + (shippingFee - finalShipDiscount));

        List<com.example.models.OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : selectedItems) {
            String img = ci.getImageUrl(); if ((img == null || img.isEmpty()) && ci.getProduct() != null) img = ci.getProduct().getImageUrl();
            orderItems.add(new com.example.models.OrderItem(ci.getProductId(), ci.getVariantId(), ci.getName(), ci.getVariantLabel(), ci.getPrice(), ci.getOriginalPrice(), ci.getQuantity(), img));
        }

        com.example.models.Order order = new com.example.models.Order();
        order.setOrderCode("ORD" + System.currentTimeMillis()); order.setUserId(userId); order.setItems(orderItems); order.setAddress(selectedAddress);
        order.setSubtotal(itemsTotal); order.setShippingFee(shippingFee - finalShipDiscount); order.setDiscountAmount(finalItemDiscount); order.setTotalPrice(finalAmount);

        String paymentDisplay = selectedPaymentMethod;
        if ("en".equals(currentLang)) {
            if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Cash on Delivery (COD)";
            else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "MoMo Wallet";
            else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "ZaloPay Wallet";
            else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "VNPAY Wallet";
            else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Credit / Debit Card";
            else if ("atm".equals(selectedPaymentMethod)) paymentDisplay = "Domestic ATM Card";
            else if ("linked_bank".equals(selectedPaymentMethod)) paymentDisplay = "Linked Bank Account";
        } else {
            if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Thanh toán khi nhận hàng (COD)";
            else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "Ví MoMo";
            else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "Ví ZaloPay";
            else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "Ví VNPAY";
            else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Thẻ Tín dụng / Ghi nợ";
            else if ("atm".equals(selectedPaymentMethod)) paymentDisplay = "Thẻ ATM nội địa";
            else if ("linked_bank".equals(selectedPaymentMethod)) paymentDisplay = "Tài khoản ngân hàng liên kết";
        }

        order.setPaymentMethod(paymentDisplay); order.setStatus(com.example.models.Order.STATUS_PENDING); order.setCreatedAt(new java.util.Date());
        com.google.firebase.firestore.DocumentReference orderRef = db.collection("orders").document(); order.setId(orderRef.getId());

        final List<com.example.models.OrderItem> stockItems = orderItems;
        StockManager.deductStock(db, stockItems, new StockManager.StockCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                order.setStockDeducted(true);
                commitPlacedOrder(db, userId, orderRef, order, cartSnapshot -> placeOrderBatch(
                        db, userId, orderRef, order, cartSnapshot, currentLang, finalAmount));
            }

            @Override
            public void onInsufficientStock(@NonNull String productName, int available) {
                if (!isAdded()) return;
                resetPlaceOrderButton(currentLang);
                String msg = "en".equals(currentLang)
                        ? productName + " only has " + available + " left in stock."
                        : productName + " chỉ còn " + available + " sản phẩm trong kho.";
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                resetPlaceOrderButton(currentLang);
                Toast.makeText(getContext(), "Lỗi đặt hàng: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private interface CartSnapshotCallback {
        void onCartLoaded(QuerySnapshot cartSnapshot);
    }

    private void commitPlacedOrder(FirebaseFirestore db,
                                   String userId,
                                   DocumentReference orderRef,
                                   com.example.models.Order order,
                                   CartSnapshotCallback callback) {
        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnSuccessListener(cartSnapshot -> {
                    if (!isAdded()) return;
                    callback.onCartLoaded(cartSnapshot);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    StockManager.restoreStock(db, order.getItems(), new StockManager.StockCallback() {
                        @Override
                        public void onSuccess() { }

                        @Override
                        public void onInsufficientStock(@NonNull String productName, int available) { }

                        @Override
                        public void onError(@NonNull String message) { }
                    });
                    resetPlaceOrderButton(LocaleHelper.getLanguage(requireContext()));
                    Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void placeOrderBatch(FirebaseFirestore db,
                                 String userId,
                                 DocumentReference orderRef,
                                 com.example.models.Order order,
                                 QuerySnapshot cartSnapshot,
                                 String currentLang,
                                 double finalAmount) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!isAdded()) return;

                    WriteBatch batch = db.batch();
                    batch.set(orderRef, order);


                                // FIX (yêu cầu #3 - "đặt hàng thành công nhưng không có thông báo"):
                                DocumentReference notificationRef = db.collection("users").document(userId)
                                        .collection("notifications").document();
                                Map<String, Object> notification = new HashMap<>();
                                notification.put("type", "ORDER_UPDATE");
                                if ("en".equals(currentLang)) {
                                    notification.put("title", "Order successful");
                                    notification.put("body", "Order #" + order.getOrderCode()
                                            + " has been placed successfully. Total amount: " + formatVnd(finalAmount) + ".");
                                } else {
                                    notification.put("title", "Đặt hàng thành công");
                                    notification.put("body", "Đơn hàng #" + order.getOrderCode()
                                            + " đã được đặt thành công. Tổng tiền: " + formatVnd(finalAmount) + ".");
                                }
                                notification.put("message", notification.get("body"));
                                notification.put("refId", orderRef.getId());
                                notification.put("orderId", orderRef.getId());
                                notification.put("orderCode", order.getOrderCode());
                                notification.put("createdAt", FieldValue.serverTimestamp());
                                notification.put("read", false);
                                batch.set(notificationRef, notification);

                                DocumentReference adminNotifRef = db.collection("admin_notifications").document();
                                Map<String, Object> adminNotif = new HashMap<>();
                                adminNotif.put("type", "ORDER_UPDATE");
                                adminNotif.put("title", "Đơn hàng mới");
                                adminNotif.put("body", "Khách vừa đặt đơn #" + order.getOrderCode()
                                        + ". Tổng tiền: " + formatVnd(finalAmount) + ".");
                                adminNotif.put("message", adminNotif.get("body"));
                                adminNotif.put("orderId", orderRef.getId());
                                adminNotif.put("orderCode", order.getOrderCode());
                                adminNotif.put("buyerId", userId);
                                adminNotif.put("read", false);
                                adminNotif.put("createdAt", FieldValue.serverTimestamp());
                                batch.set(adminNotifRef, adminNotif);


                                // ✅ FIX: Hạng thành viên chỉ tính đơn đã giao.
                                // Gỡ bỏ việc tăng spentAmount và cập nhật tier ngay khi đặt hàng.


                                for (CartItem ci : selectedItems) {
                                    DocumentReference cartDocRef = resolveCartDocument(cartSnapshot, ci);
                                    if (cartDocRef != null) {
                                        batch.delete(cartDocRef);
                                    }
                                }


                    batch.commit().addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            showSuccessDialog();
                        }
                    }).addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        StockManager.restoreStock(db, order.getItems(), new StockManager.StockCallback() {
                            @Override
                            public void onSuccess() { }

                            @Override
                            public void onInsufficientStock(@NonNull String productName, int available) { }

                            @Override
                            public void onError(@NonNull String message) { }
                        });
                        resetPlaceOrderButton(currentLang);
                        Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    StockManager.restoreStock(db, order.getItems(), new StockManager.StockCallback() {
                        @Override
                        public void onSuccess() { }

                        @Override
                        public void onInsufficientStock(@NonNull String productName, int available) { }

                        @Override
                        public void onError(@NonNull String message) { }
                    });
                    resetPlaceOrderButton(currentLang);
                    Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void resetPlaceOrderButton(String currentLang) {
        btnPlaceOrder.setEnabled(true);
        btnPlaceOrder.setText("en".equals(currentLang) ? "Place order" : "Đặt hàng");
    }


    private long readSpentAmount(DocumentSnapshot document) {
        if (document == null || !document.exists()) return 0;
        Long spentLong = document.getLong("spentAmount");
        if (spentLong != null) return spentLong;
        Double spentDouble = document.getDouble("spentAmount");
        return spentDouble != null ? spentDouble.longValue() : 0;
    }

    private DocumentReference resolveCartDocument(QuerySnapshot cartSnapshot, CartItem item) {
        if (cartSnapshot == null || item == null) return null;
        for (QueryDocumentSnapshot doc : cartSnapshot) if (TextUtils.equals(item.getProductId(), doc.getString("productId")) && TextUtils.equals(item.getVariantId(), doc.getString("variantId"))) return doc.getReference();
        if (!TextUtils.isEmpty(item.getId())) for (QueryDocumentSnapshot doc : cartSnapshot) if (TextUtils.equals(item.getId(), doc.getId())) return doc.getReference();
        return null;
    }

    private void showSuccessDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_order_success, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme).setView(dialogView).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialogView.findViewById(R.id.btnTrackOrder).setOnClickListener(v -> {
            dialog.dismiss(); getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            MainActivity main = (MainActivity) requireActivity();
            com.google.android.material.bottomnavigation.BottomNavigationView nav = main.findViewById(R.id.bottom_navigation);
            if (nav != null) { nav.setSelectedItemId(R.id.nav_profile); OrderHistoryFragment frag = new OrderHistoryFragment(); Bundle args = new Bundle(); args.putInt("initial_tab", 1); frag.setArguments(args); main.getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, frag).addToBackStack(null).commit(); }
        });
        dialogView.findViewById(R.id.btnContinueShopping).setOnClickListener(v -> { dialog.dismiss(); getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE); MainActivity main = (MainActivity) requireActivity(); com.google.android.material.bottomnavigation.BottomNavigationView nav = main.findViewById(R.id.bottom_navigation); if (nav != null) nav.setSelectedItemId(R.id.nav_home); });
        dialog.show();
    }
}
