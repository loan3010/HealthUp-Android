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
import com.example.healthup.util.LocaleHelper;
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
        }


        bindViews(view);
        applySystemBarInsets(view);
        setupListeners();
        hydrateSelectedItemImages();
        renderProductList();
        renderVouchers();
        loadDefaultAddress();
        loadVouchers(); 
        
        // ✅ THÊM: Đồng bộ lại giao diện vận chuyển và tính toán tiền ngay khi view được tạo lại
        updateShippingSelection();

        return view;
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
            if (v == null || itemsTotal < v.getMinOrderAmount()) continue;

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


    private double calculateSaving(Voucher v, double itemsTotal) {
        double val = v.getDiscountAmount();

        // ✅ KIỂM TRA ĐIỀU KIỆN VẬN CHUYỂN
        if (v.getType() == Voucher.Type.SHIPPING) {
            String desc = (v.getDescription() != null) ? v.getDescription().toLowerCase() : "";
            String code = (v.getCode() != null) ? v.getCode().toLowerCase() : "";
            
            // Nếu là mã chỉ dành cho Giao Nhanh (Fast)
            boolean isFastVoucher = desc.contains("giao nhanh") || code.contains("fast") || desc.contains("2 giờ");
            boolean isFastShippingSelected = (shippingFee > 25000); // 45k là fast, 21k là standard

            if (isFastVoucher && !isFastShippingSelected) {
                return 0; // Mã không áp dụng cho phương thức giao hàng hiện tại
            }
            
            // Nếu là mã % (VD: FreeShip 100%)
            if (val > 0 && val <= 100) {
                return (val / 100.0) * shippingFee;
            }
        } else {
            // Mã giảm giá sản phẩm %
            if (val > 0 && val <= 100) {
                return (val / 100.0) * itemsTotal;
            }
        }

        return val;
    }


    private void requestPaymentPermission(String providerName, int rbId) {
        if (authorizedMethods.contains(rbId)) return;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cấp quyền truy cập " + providerName)
                .setMessage("Để thực hiện thanh toán qua " + providerName + ", HealthUp cần quyền truy cập thông tin định danh để bảo mật giao dịch.")
                .setPositiveButton("Cho phép", (dialog, which) -> {
                    authorizedMethods.add(rbId);
                    Toast.makeText(getContext(), "Đã cấp quyền truy cập " + providerName, Toast.LENGTH_SHORT).show();
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
            rowVoucherNoSelect.setVisibility(View.GONE);
            layoutVoucherApplied.setVisibility(View.VISIBLE);
            
            // ✅ Hiển thị tóm tắt tất cả các mã đã áp dụng tự động, lọc bỏ null
            StringBuilder sb = new StringBuilder();
            for (Voucher v : selectedVouchers) {
                String code = v.getCode();
                if (code != null && !code.equalsIgnoreCase("null")) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(code);
                }
            }
            
            tvAppliedVoucherTitle.setText(sb.length() > 0 ? sb.toString() : "Mã giảm giá đã áp dụng");
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


    private void applySystemBarInsets(View view) {
        View footer = view.findViewById(R.id.footer);
        if (footer == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(footer, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return windowInsets;
        });
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
            com.example.healthup.util.CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PhoneVerificationFragment())
                    .addToBackStack(null)
                    .commit();
            return;
        }


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
            orderItems.add(new com.example.models.OrderItem(
                    ci.getProductId(),
                    ci.getVariantId(),
                    ci.getName(),
                    ci.getVariantLabel(),
                    ci.getPrice(),
                    ci.getOriginalPrice(),
                    ci.getQuantity(),
                    ci.getImageUrl()
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
        } else {
            if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Thanh toán khi nhận hàng (COD)";
            else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "Ví MoMo";
            else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "Ví ZaloPay";
            else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "Ví VNPAY";
            else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Thẻ Tín dụng / Ghi nợ";
        }

        order.setPaymentMethod(paymentDisplay);
        order.setStatus(com.example.models.Order.STATUS_PENDING);
        order.setCreatedAt(new java.util.Date()); // Use java.util.Date


        com.google.firebase.firestore.DocumentReference orderRef = db.collection("orders").document();
        order.setId(orderRef.getId());


        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnSuccessListener(cartSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }


                    db.collection("users").document(userId).get()
                            .addOnSuccessListener(userDoc -> {
                                if (!isAdded()) return;


                                WriteBatch batch = db.batch();
                                batch.set(orderRef, order);


                                // FIX (yêu cầu #3 - "đặt hàng thành công nhưng không có thông báo"):
                                // Trước đây sau khi đặt hàng thành công, code chỉ hiện dialog chúc mừng
                                // (showSuccessDialog) mà KHÔNG hề ghi document vào
                                // users/{uid}/notifications -> NotificationsFragment không có gì để
                                // đọc, nên trang Thông báo luôn trống đối với đơn hàng vừa đặt.
                                DocumentReference notificationRef = db.collection("users").document(userId)
                                        .collection("notifications").document();
                                Map<String, Object> notification = new HashMap<>();
                                notification.put("type", "ORDER_SHIPPING");
                                if ("en".equals(currentLang)) {
                                    notification.put("title", "Order successful");
                                    notification.put("body", "Order #" + order.getOrderCode()
                                            + " has been placed successfully. Total amount: " + formatVnd(finalAmount) + ".");
                                } else {
                                    notification.put("title", "Đặt hàng thành công");
                                    notification.put("body", "Đơn hàng #" + order.getOrderCode()
                                            + " đã được đặt thành công. Tổng tiền: " + formatVnd(finalAmount) + ".");
                                }
                                notification.put("refId", orderRef.getId());
                                notification.put("createdAt", FieldValue.serverTimestamp());
                                notification.put("read", false);
                                batch.set(notificationRef, notification);


                                Map<String, Object> userUpdates = new HashMap<>();
                                userUpdates.put("spentAmount",
                                        com.google.firebase.firestore.FieldValue.increment(finalAmount));


                                long currentSpent = readSpentAmount(userDoc);
                                if (currentSpent + (long) finalAmount >= 5_000_000L) {
                                    userUpdates.put("tier", "VIP");
                                }


                                batch.set(
                                        db.collection("users").document(userId),
                                        userUpdates,
                                        SetOptions.merge()
                                );


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
                                    if (isAdded()) {
                                        btnPlaceOrder.setEnabled(true);
                                        btnPlaceOrder.setText("Đặt hàng");
                                        Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                if (isAdded()) {
                                    btnPlaceOrder.setEnabled(true);
                                    btnPlaceOrder.setText("Đặt hàng");
                                    Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        btnPlaceOrder.setEnabled(true);
                        btnPlaceOrder.setText("Đặt hàng");
                        Toast.makeText(getContext(), "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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
            OrderHistoryFragment fragment = new OrderHistoryFragment();
            Bundle args = new Bundle();
            args.putInt("initial_tab", 1); // Chuyển đến tab "Chờ xác nhận"
            fragment.setArguments(args);
            loadFragment(fragment);
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