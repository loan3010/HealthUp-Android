package com.example.healthup;


import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.models.Voucher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
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


    private List<CartItem> selectedItems = new ArrayList<>();
    private Address selectedAddress;
    private List<Voucher> selectedVouchers = new ArrayList<>();
    private String selectedPaymentMethod = "cod";
    private final Set<Integer> authorizedMethods = new HashSet<>();
    private boolean isManualSelection = false;
    private List<DocumentSnapshot> cachedVoucherDocs = null;
    private String userTier = "Member";


    private double shippingFee = 0; // ✅ Mặc định Giao tiêu chuẩn là 0đ
    private double shippingDiscount = 0;


    private TextView tvRecipientInfo, tvAddressDetail, tvVoucherInfo;
    private TextView tvTotalItemPrice, tvShippingFee, tvShippingDiscount, tvVoucherDiscount, tvGrandTotal, tvFooterTotal;
    private TextView tvAppliedVoucherTitle, tvAppliedVoucherDesc;
    private View rowAddress, rowVoucherNoSelect, layoutVoucherApplied;
    private TextView btnRemoveVoucher, btnViewAllVoucher;
    private View layoutShippingStandard, layoutShippingFast;
    private RecyclerView rvCheckoutProducts;
    private Button btnPlaceOrder;
    private RadioGroup radioGroupPayment;
    private CheckBox cbAgreeTerms;


    // Lời nhắn cho shop
    private View rowShopNote;
    private TextView tvShopNotePreview;
    private ImageView ivShopNoteArrow;
    private EditText etShopNote;
    private boolean isShopNoteExpanded = false;


    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nhận kết quả chọn địa chỉ từ AddressBookFragment
        getParentFragmentManager().setFragmentResultListener("address_result", this, (requestKey, result) -> {
            Address address = (Address) result.getSerializable("selected_address");
            if (address != null) {
                selectedAddress = address;
                renderAddress();
            }
        });


        // Nhận kết quả chọn voucher
        getParentFragmentManager().setFragmentResultListener("voucher_result", this, (requestKey, result) -> {
            List<Voucher> vouchers = (List<Voucher>) result.getSerializable("selected_vouchers");
            if (vouchers != null) {
                this.selectedVouchers = new ArrayList<>(vouchers);
                this.isManualSelection = true; // ✅ Đánh dấu người dùng đã tự chọn
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
            
            // ✅ Read selected vouchers from Cart
            Serializable voucherData = getArguments().getSerializable("selected_vouchers");
            if (voucherData instanceof List) {
                selectedVouchers = (List<Voucher>) voucherData;
                isManualSelection = !selectedVouchers.isEmpty();
            }
        }


        bindViews(view);
        applyHeaderWindowInsets(view); // ✅ Đồng bộ header với Cart/Profile
        setupListeners();
        hydrateSelectedItemImages();
        renderProductList();
        renderVouchers();
        loadDefaultAddress();
        loadUserTierAndVouchers(); 
        
        // Đồng bộ giao diện vận chuyển
        updateShippingSelection();

        return view;
    }

    private void applyHeaderWindowInsets(View view) {
        View header = view.findViewById(R.id.header);
        if (header == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Chỉ dùng systemBars.top, không cộng thêm padding dư thừa để tránh bị "tụt xuống"
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }


    private void loadUserTierAndVouchers() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
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
            if (isAdded()) {
                calculateSummary();
            }
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
                if (saving > maxShipSaving) {
                    maxShipSaving = saving;
                    bestShipping = v;
                }
            } else {
                if (saving > maxDiscountSaving) {
                    maxDiscountSaving = saving;
                    bestDiscount = v;
                }
            }
        }

        selectedVouchers.clear();
        if (bestShipping != null && maxShipSaving > 0) {
            bestShipping.setSelected(true);
            selectedVouchers.add(bestShipping);
        }
        if (bestDiscount != null && maxDiscountSaving > 0) {
            bestDiscount.setSelected(true);
            selectedVouchers.add(bestDiscount);
        }
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
        
        String desc = doc.getString("description");
        v.setDescription(desc);

        v.setMinOrderAmount(getDouble(doc, "minOrderValue"));

        // ✅ ĐỌC HẠNG YÊU CẦU: Ưu tiên requiredTier, fallback sang tier
        String reqTier = doc.getString("requiredTier");
        if (reqTier == null) reqTier = doc.getString("tier");
        
        // ✅ ÉP BUỘC VIP NẾU TÊN MÃ BẮT ĐẦU BẰNG VIP
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
        
        // Nếu vẫn bằng 0, thử lấy phần trăm
        double percent = getDouble(doc, "discountPercent");
        if (percent > 0) amount = percent;

        // ✅ FALLBACK: Nếu vẫn là 0, thử tách số từ mô tả (Dành cho mã FREESHIP20K)
        if (amount == 0 && desc != null) {
            amount = extractNumberFromDesc(desc);
        }
        
        v.setDiscountAmount(amount);

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
            // Tìm các chuỗi số như 20.000 hoặc 20000
            String cleaned = desc.replaceAll("[^0-9]", " ");
            String[] parts = cleaned.trim().split("\\s+");
            for (String p : parts) {
                if (p.length() >= 2) {
                    double val = Double.parseDouble(p);
                    if (val > 100) return val; // Ưu tiên số tiền lớn (20000)
                    if (val > 0) return val;   // Hoặc số % (5, 10)
                }
            }
        } catch (Exception e) {}
        return 0;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception e) {}
        }
        return 0;
    }


    private boolean isTierMatch(Voucher v) {
        String req = v.getRequiredTier();
        if (req == null || req.isEmpty() || req.equalsIgnoreCase("Member")) return true;
        return userTier.equalsIgnoreCase(req);
    }


    private double calculateSaving(Voucher v, double itemsTotal) {
        // ✅ KIỂM TRA ĐIỀU KIỆN ĐƠN TỐI THIỂU VÀ TIER
        if (itemsTotal < v.getMinOrderAmount() || !isTierMatch(v)) {
            return 0;
        }

        double val = v.getDiscountAmount();
        String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
        String code = (v.getCode() != null) ? v.getCode().toLowerCase() : "";

        if (v.getType() == Voucher.Type.SHIPPING) {
            // ✅ Chỉ giảm 100% nếu giá trị mã là 100 (%), không tự ý giảm theo tên mã nữa
            if (val == 100) {
                return shippingFee;
            }

            // Kiểm tra mã chỉ dành cho Giao nhanh
            boolean isFastVoucher = desc.contains("giao nhanh") || code.contains("fast") || desc.contains("2 giờ");
            boolean isFastShippingSelected = (shippingFee > 25000); 
            if (isFastVoucher && !isFastShippingSelected) return 0;

            // Nếu giá trị mã nhỏ (VD: 10, 20, 50) thì coi là % phí ship
            if (val > 0 && val < 100) {
                return (val / 100.0) * shippingFee;
            }
            
            // ✅ Nếu giá trị mã lớn (VD: 20000, 21000) thì trả về đúng số tiền đó
            return val;
        } else {
            // Giảm giá sản phẩm
            if (val > 0 && val <= 100) {
                return (val / 100.0) * itemsTotal;
            }
            return val;
        }
    }


    private void requestPaymentPermission(String providerName, int rbId) {
        if (authorizedMethods.contains(rbId)) return;

        String message;
        if (rbId == R.id.rbLinkedBank) {
            message = "Cho phép cấp quyền liên kết thẻ ngân hàng.";
        } else if (rbId == R.id.rbCard) {
            message = "Cho phép cấp quyền thẻ ATM nội địa/Thẻ tín dụng.";
        } else {
            message = "Để thực hiện thanh toán qua " + providerName + ", HealthUp cần quyền truy cập thông tin định danh để bảo mật giao dịch.";
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cấp quyền truy cập " + providerName)
                .setMessage(message)
                .setPositiveButton("Cho phép", (dialog, which) -> {
                    authorizedMethods.add(rbId);
                    
                    String toastMsg;
                    if (rbId == R.id.rbLinkedBank) {
                        toastMsg = "Đã cho phép cấp quyền liên kết thẻ ngân hàng";
                    } else if (rbId == R.id.rbCard) {
                        toastMsg = "Đã cho phép cấp quyền thẻ ATM nội địa/Thẻ tín dụng";
                    } else {
                        toastMsg = "Đã cấp quyền truy cập " + providerName;
                    }
                    Toast.makeText(getContext(), toastMsg, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Từ chối", (dialog, which) -> {
                    Toast.makeText(getContext(), "Bạn cần cấp quyền để sử dụng phương thức này", Toast.LENGTH_SHORT).show();
                    radioGroupPayment.check(R.id.rbCod);
                })
                .show();
    }


    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());


        tvRecipientInfo = view.findViewById(R.id.tvRecipientInfo);
        tvAddressDetail = view.findViewById(R.id.tvAddressDetail);
        tvVoucherInfo = view.findViewById(R.id.tvVoucherInfo);


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


        rvCheckoutProducts = view.findViewById(R.id.rvCheckoutProducts);
        btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder);
        radioGroupPayment = view.findViewById(R.id.radioGroupPayment);
        cbAgreeTerms = view.findViewById(R.id.cbAgreeTerms);


        rowShopNote = view.findViewById(R.id.rowShopNote);
        tvShopNotePreview = view.findViewById(R.id.tvShopNotePreview);
        ivShopNoteArrow = view.findViewById(R.id.ivShopNoteArrow);
        etShopNote = view.findViewById(R.id.etShopNote);


        rvCheckoutProducts.setLayoutManager(new LinearLayoutManager(getContext()));
    }


    private void setupListeners() {
        rowAddress.setOnClickListener(v -> openAddressBook());
        rowVoucherNoSelect.setOnClickListener(v -> openVoucherList());
        btnViewAllVoucher.setOnClickListener(v -> openVoucherList());


        btnRemoveVoucher.setOnClickListener(v -> {
            selectedVouchers.clear();
            isManualSelection = true; // ✅ Đánh dấu là khách tự bỏ mã, không tự áp lại nữa
            renderVouchers();
            calculateSummary();
        });


        rowShopNote.setOnClickListener(v -> toggleShopNote());


        layoutShippingStandard.setOnClickListener(v -> {
            shippingFee = 0; // ✅ Giao tiêu chuẩn = 0đ
            updateShippingSelection();
        });


        layoutShippingFast.setOnClickListener(v -> {
            shippingFee = 45000;
            updateShippingSelection();
        });


        btnPlaceOrder.setOnClickListener(v -> placeOrder());


        radioGroupPayment.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCod) {
                selectedPaymentMethod = "cod";
                return;
            }

            // Xử lý tất cả các phương thức Online
            String provider = "Ví điện tử / Thẻ";
            if (checkedId == R.id.rbVnpay) {
                selectedPaymentMethod = "vnpay";
                provider = "VNPAY";
            } else if (checkedId == R.id.rbCard) {
                selectedPaymentMethod = "card";
                provider = "Thẻ ngân hàng";
            } else if (checkedId == R.id.rbZaloPay) {
                selectedPaymentMethod = "zalopay";
                provider = "ZaloPay";
            } else if (checkedId == R.id.rbMomo) {
                selectedPaymentMethod = "momo";
                provider = "MoMo";
            } else if (checkedId == R.id.rbLinkedBank) {
                selectedPaymentMethod = "linked_bank";
                provider = "Tài khoản ngân hàng liên kết";
            }

            requestPaymentPermission(provider, checkedId);
        });
    }


    private void loadDefaultAddress() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String effectiveUserId = (user != null) ? user.getUid() : "guest_user";

        FirebaseFirestore.getInstance()
                .collection("users").document(effectiveUserId)
                .collection("addresses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // 1. Tìm địa chỉ mặc định
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Address addr = doc.toObject(Address.class);
                            if (addr != null) {
                                addr.setId(doc.getId());
                                if (addr.isDefault()) {
                                    selectedAddress = addr;
                                    break;
                                }
                            }
                        }

                        // 2. Nếu không có mặc định, lấy địa chỉ đầu tiên
                        if (selectedAddress == null) {
                            DocumentSnapshot firstDoc = queryDocumentSnapshots.getDocuments().get(0);
                            selectedAddress = firstDoc.toObject(Address.class);
                            if (selectedAddress != null) {
                                selectedAddress.setId(firstDoc.getId());
                            }
                        }
                        renderAddress();
                    } else {
                        renderAddress();
                    }
                });
    }


    private void toggleShopNote() {
        isShopNoteExpanded = !isShopNoteExpanded;
        etShopNote.setVisibility(isShopNoteExpanded ? View.VISIBLE : View.GONE);
        ivShopNoteArrow.setRotation(isShopNoteExpanded ? 180f : 0f);
        if (isShopNoteExpanded) {
            etShopNote.requestFocus();
        } else {
            String note = etShopNote.getText().toString().trim();
            tvShopNotePreview.setText(note.isEmpty() ? "Để lại lời nhắn" : note);
        }
    }


    private void updateShippingSelection() {
        boolean isStandard = (shippingFee <= 0);
        layoutShippingStandard.setBackgroundResource(isStandard ? R.drawable.bg_shipping_selected : R.drawable.bg_shipping_unselected);
        layoutShippingFast.setBackgroundResource(isStandard ? R.drawable.bg_shipping_unselected : R.drawable.bg_shipping_selected);
        
        // ✅ TỰ ĐỘNG CẬP NHẬT LẠI MÃ SHIP TỐI ƯU KHI ĐỔI PHƯƠNG THỨC (nếu khách chưa tự chọn mã)
        if (!isManualSelection) {
            performAutoSelection();
        }
        
        renderVouchers();
        calculateSummary();
    }

    private void openVoucherList() {
        PromoCouponFragment fragment = new PromoCouponFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable("selected_vouchers", (Serializable) selectedVouchers);
        bundle.putDouble("order_total", getItemsTotal());
        bundle.putDouble("shipping_fee", shippingFee); // ✅ Truyền phí ship sang
        bundle.putBoolean("has_visited", !selectedVouchers.isEmpty());
        fragment.setArguments(bundle);

        requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit();
    }

    private void renderVouchers() {
        if (selectedVouchers.isEmpty()) {
            rowVoucherNoSelect.setVisibility(View.VISIBLE);
            layoutVoucherApplied.setVisibility(View.GONE);
        } else {
            // ✅ KIỂM TRA ĐIỀU KIỆN ÁP MÃ THỰC TẾ
            double itemsTotal = getItemsTotal();
            boolean hasEligible = false;
            for (Voucher v : selectedVouchers) {
                if (itemsTotal >= v.getMinOrderAmount() && isTierMatch(v)) {
                    hasEligible = true;
                    break;
                }
            }

            if (!hasEligible) {
                // ✅ HIỂN THỊ THÔNG BÁO THEO YÊU CẦU: Không đủ điều kiện
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
            
            // HIỂN THỊ THEO YÊU CẦU TẠI THANH TOÁN
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
                if (!isFastShippingSelected) {
                    sb.append("Đã áp dụng mã vận chuyển");
                } else {
                    double shipSaving = 0;
                    for (Voucher v : selectedVouchers) {
                        if (v.getType() == Voucher.Type.SHIPPING && itemsTotal >= v.getMinOrderAmount() && isTierMatch(v)) {
                            shipSaving += calculateSaving(v, itemsTotal);
                        }
                    }
                    double finalShipSaving = Math.min(shipSaving, shippingFee);
                    sb.append("Giảm vận chuyển ").append(currencyFormat.format(finalShipSaving)).append("đ");
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

    private void hydrateSelectedItemImages() {
        for (CartItem item : selectedItems) {
            hydrateImageUrl(item);
        }
    }

    private void hydrateImageUrl(CartItem item) {
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            return;
        }
        if (item.getProduct() != null && item.getProduct().getImageUrl() != null) {
            item.setImageUrl(item.getProduct().getImageUrl());
        }
    }

    private void renderProductList() {
        rvCheckoutProducts.setAdapter(new CheckoutProductAdapter(selectedItems));
    }

    private double getItemsTotal() {
        double total = 0;
        for (CartItem item : selectedItems) total += item.getPrice() * item.getQuantity();
        return total;
    }

    private String formatVnd(double amount) {
        return currencyFormat.format(amount) + "đ";
    }

    private void calculateSummary() {
        double itemsTotal = getItemsTotal();
        double totalItemSaving = 0;
        double totalShipSaving = 0;

        for (Voucher v : selectedVouchers) {
            double saving = calculateSaving(v, itemsTotal);
            if (v.getType() == Voucher.Type.SHIPPING) {
                totalShipSaving += saving;
            } else {
                totalItemSaving += saving;
            }
        }

        // ✅ GIẢM PHÍ SHIP: Chỉ trừ tối đa bằng phí ship hiện tại
        double finalShipDiscount = Math.min(totalShipSaving, shippingFee);

        // ✅ GIẢM TIỀN HÀNG: Chỉ trừ tối đa bằng tổng tiền hàng
        double finalItemDiscount = Math.min(totalItemSaving, itemsTotal);

        double grandTotal = Math.max(0, (itemsTotal - finalItemDiscount) + (shippingFee - finalShipDiscount));

        tvTotalItemPrice.setText(formatVnd(itemsTotal));
        tvShippingFee.setText(formatVnd(shippingFee));
        tvShippingDiscount.setText("-" + formatVnd(finalShipDiscount));
        tvVoucherDiscount.setText("-" + formatVnd(finalItemDiscount));
        tvGrandTotal.setText(formatVnd(grandTotal));
        tvFooterTotal.setText(formatVnd(grandTotal));
    }


    private void openAddressBook() {
        AddressBookFragment fragment = new AddressBookFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean("select_mode", true);
        fragment.setArguments(bundle);


        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
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
        if (selectedAddress == null) {
            Toast.makeText(getContext(), "Vui lòng chọn địa chỉ giao hàng", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedItems.isEmpty()) {
            Toast.makeText(getContext(), "Không có sản phẩm để đặt hàng", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cbAgreeTerms.isChecked()) {
            Toast.makeText(getContext(), "Vui lòng đồng ý với Điều khoản", Toast.LENGTH_SHORT).show();
            return;
        }


        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PhoneVerificationFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }

        PhoneVerifiedHelper.requireForCheckout(new PhoneVerifiedHelper.Callback() {
            @Override
            public void onVerified() {
                if (!isAdded()) {
                    return;
                }
                continuePlaceOrder(user);
            }

            @Override
            public void onNeedPhoneVerification() {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(getContext(), R.string.checkout_need_phone_verified, Toast.LENGTH_LONG).show();
                CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems);
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new PhoneVerificationFragment())
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(getContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void continuePlaceOrder(@NonNull FirebaseUser user) {
        btnPlaceOrder.setEnabled(false);
        String currentLang = LocaleHelper.getLanguage(requireContext());
        btnPlaceOrder.setText(currentLang.equals("en") ? "Processing..." : "Đang xử lý...");


        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();


        double itemsTotal = getItemsTotal();
        double totalItemSaving = 0;
        double totalShipSaving = 0;

        for (Voucher v : selectedVouchers) {
            double saving = calculateSaving(v, itemsTotal);
            if (v.getType() == Voucher.Type.SHIPPING) {
                totalShipSaving += saving;
            } else {
                totalItemSaving += saving;
            }
        }

        double finalShipDiscount = Math.min(totalShipSaving, shippingFee);
        double finalItemDiscount = Math.min(totalItemSaving, itemsTotal);
        double finalAmount = Math.max(0, (itemsTotal - finalItemDiscount) + (shippingFee - finalShipDiscount));


        List<com.example.models.OrderItem> orderItems = new ArrayList<>();
        for (CartItem ci : selectedItems) {
            String imageUrl = ci.getImageUrl();
            if ((imageUrl == null || imageUrl.isEmpty()) && ci.getProduct() != null) {
                imageUrl = ci.getProduct().getImageUrl();
            }
            orderItems.add(new com.example.models.OrderItem(
                    ci.getProductId(),
                    ci.getVariantId(),
                    ci.getName(),
                    ci.getVariantLabel(),
                    ci.getPrice(),
                    ci.getOriginalPrice(),
                    ci.getQuantity(),
                    imageUrl
            ));
        }


        com.example.models.Order order = new com.example.models.Order();
        order.setOrderCode("ORD" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setItems(orderItems);
        order.setAddress(selectedAddress);
        order.setSubtotal(itemsTotal);
        order.setShippingFee(shippingFee - finalShipDiscount);
        order.setDiscountAmount(finalItemDiscount);
        order.setTotalPrice(finalAmount);

        // Map payment method code to display name
        String paymentDisplay = selectedPaymentMethod;
        if ("en".equals(currentLang)) {
            if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Cash on Delivery (COD)";
            else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "MoMo Wallet";
            else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "ZaloPay Wallet";
            else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "VNPAY Wallet";
            else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Credit / Debit Card";
            else if ("linked_bank".equals(selectedPaymentMethod)) paymentDisplay = "Linked Bank Account";
        } else {
            if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Thanh toán khi nhận hàng (COD)";
            else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "Ví MoMo";
            else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "Ví ZaloPay";
            else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "Ví VNPAY";
            else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Thẻ Tín dụng / Ghi nợ";
            else if ("linked_bank".equals(selectedPaymentMethod)) paymentDisplay = "Tài khoản ngân hàng liên kết";
        }

        order.setPaymentMethod(paymentDisplay);
        order.setStatus(com.example.models.Order.STATUS_PENDING);
        order.setCreatedAt(new java.util.Date()); // Use java.util.Date


        com.google.firebase.firestore.DocumentReference orderRef = db.collection("orders").document();
        order.setId(orderRef.getId());

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
        if (cartSnapshot == null || item == null) {
            return null;
        }


        for (QueryDocumentSnapshot doc : cartSnapshot) {
            if (TextUtils.equals(item.getProductId(), doc.getString("productId"))
                    && TextUtils.equals(item.getVariantId(), doc.getString("variantId"))) {
                return doc.getReference();
            }
        }


        if (!TextUtils.isEmpty(item.getId())) {
            for (QueryDocumentSnapshot doc : cartSnapshot) {
                if (TextUtils.equals(item.getId(), doc.getId())) {
                    return doc.getReference();
                }
            }
        }


        return null;
    }


    private void showSuccessDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_order_success, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();


        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }


        dialogView.findViewById(R.id.btnTrackOrder).setOnClickListener(v -> {
            dialog.dismiss();
            
            // 1. Xoá sạch stack cũ (Giỏ hàng, Thanh toán...) để về gốc
            getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

            // 2. Chuyển sang tab Tài khoản (Profile)
            MainActivity main = (MainActivity) requireActivity();
            View bottomNav = main.findViewById(R.id.bottom_navigation);
            if (bottomNav instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
                ((com.google.android.material.bottomnavigation.BottomNavigationView) bottomNav).setSelectedItemId(R.id.nav_profile);
                
                // 3. Đè OrderHistoryFragment lên trên ProfileFragment để khi back thì về Profile
                OrderHistoryFragment fragment = new OrderHistoryFragment();
                Bundle args = new Bundle();
                args.putInt("initial_tab", 1); 
                fragment.setArguments(args);
                
                main.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null) 
                    .commit();
            }
        });


        dialogView.findViewById(R.id.btnContinueShopping).setOnClickListener(v -> {
            dialog.dismiss();
            // Xóa toàn bộ stack để quay về trạng thái gốc
            getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

            // Chuyển tab BottomNavigation sang Trang chủ
            View navView = requireActivity().findViewById(R.id.bottom_navigation);
            if (navView instanceof com.google.android.material.bottomnavigation.BottomNavigationView) {
                ((com.google.android.material.bottomnavigation.BottomNavigationView) navView).setSelectedItemId(R.id.nav_home);
            }
        });


        dialog.show();
    }


    private void loadFragment(androidx.fragment.app.Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}