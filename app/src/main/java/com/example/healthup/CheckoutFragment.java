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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.CheckoutProductAdapter;
import com.example.models.Address;
import com.example.models.CartItem;
import com.example.models.Voucher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutFragment extends Fragment {

    private List<CartItem> selectedItems = new ArrayList<>();
    private Address selectedAddress;
    private List<Voucher> selectedVouchers = new ArrayList<>();
    private String selectedPaymentMethod = "cod";

    private double shippingFee = 21000;
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
        setupListeners();
        hydrateSelectedItemImages();
        renderProductList();
        renderVouchers();
        loadDefaultAddress();
        loadVouchers(); // ✅ Tự động lấy voucher từ Firebase
        calculateSummary();

        return view;
    }

    private void loadVouchers() {
        FirebaseManager.getInstance().getVouchers().addOnSuccessListener(snapshot -> {
            if (snapshot == null || snapshot.isEmpty()) return;
            
            double itemsTotal = getItemsTotal();
            Voucher bestShipping = null;
            double maxShipSaving = 0;
            
            Voucher bestDiscount = null;
            double maxDiscountSaving = 0;

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Voucher v = parseVoucherFromDoc(doc);
                if (v == null) continue;
                
                // Kiểm tra điều kiện áp dụng
                if (itemsTotal < v.getMinOrderAmount()) continue;

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

            // Chỉ tự động chọn nếu danh sách hiện tại đang trống (lần đầu vào)
            if (selectedVouchers.isEmpty()) {
                if (bestShipping != null) {
                    bestShipping.setSelected(true);
                    selectedVouchers.add(bestShipping);
                }
                if (bestDiscount != null) {
                    bestDiscount.setSelected(true);
                    selectedVouchers.add(bestDiscount);
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

    private Voucher parseVoucherFromDoc(DocumentSnapshot doc) {
        Boolean active = doc.getBoolean("isActive");
        if (active != null && !active) return null;

        Voucher v = new Voucher();
        v.setId(doc.getId());
        v.setCode(doc.getString("code"));
        v.setTitle(v.getCode());
        v.setDescription(doc.getString("description"));
        
        // Trích xuất minOrderValue an toàn
        Double minVal = doc.getDouble("minOrderValue");
        v.setMinOrderAmount(minVal != null ? minVal : 0);
        
        // Nhận diện loại giảm giá (Percent ưu tiên)
        Double percent = doc.getDouble("discountPercent");
        if (percent != null && percent > 0) {
            v.setDiscountAmount(percent);
        } else {
            Double amount = doc.getDouble("discountAmount");
            v.setDiscountAmount(amount != null ? amount : 0);
        }

        String code = (v.getCode() != null ? v.getCode() : "").toUpperCase();
        if (code.contains("SHIP") || code.contains("FREE")) {
            v.setType(Voucher.Type.SHIPPING);
        } else {
            v.setType(Voucher.Type.DISCOUNT);
        }

        return v;
    }

    private double calculateSaving(Voucher v, double itemsTotal) {
        double val = v.getDiscountAmount();
        // Giả định nếu giá trị < 100 thì đó là % (ví dụ 5, 10, 15...)
        if (val > 0 && val < 100) {
            if (v.getType() == Voucher.Type.SHIPPING) {
                return (val / 100.0) * shippingFee;
            } else {
                return (val / 100.0) * itemsTotal;
            }
        }
        return val;
    }

    private void requestPaymentPermission(String providerName, int rbId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cấp quyền truy cập " + providerName)
                .setMessage("Để thực hiện thanh toán qua " + providerName + ", HealthUp cần quyền truy cập thông tin định danh để bảo mật giao dịch.")
                .setPositiveButton("Cho phép", (dialog, which) -> {
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
            renderVouchers();
            calculateSummary();
        });

        rowShopNote.setOnClickListener(v -> toggleShopNote());

        layoutShippingStandard.setOnClickListener(v -> {
            shippingFee = 21000;
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
                .whereEqualTo("default", true)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        selectedAddress = queryDocumentSnapshots.getDocuments().get(0).toObject(Address.class);
                        if (selectedAddress != null) {
                            selectedAddress.setId(queryDocumentSnapshots.getDocuments().get(0).getId());
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
        boolean isStandard = shippingFee == 21000;
        layoutShippingStandard.setBackgroundResource(isStandard ? R.drawable.bg_shipping_selected : R.drawable.bg_shipping_unselected);
        layoutShippingFast.setBackgroundResource(isStandard ? R.drawable.bg_shipping_unselected : R.drawable.bg_shipping_selected);
        calculateSummary();
    }

    private void openVoucherList() {
        PromoCouponFragment fragment = new PromoCouponFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable("selected_vouchers", (Serializable) selectedVouchers);
        bundle.putDouble("order_total", getItemsTotal()); // ✅ Truyền tổng tiền để kiểm tra điều kiện mã
        bundle.putBoolean("has_visited", !selectedVouchers.isEmpty()); // Chỉ coi là đã thăm nếu thực sự đã có chọn mã
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
            
            Voucher mainVch = selectedVouchers.get(0);
            for (Voucher v : selectedVouchers) if (v.getType() != Voucher.Type.SHIPPING) mainVch = v;
            
            tvAppliedVoucherTitle.setText(mainVch.getTitle());
            if (selectedVouchers.size() > 1) {
                tvAppliedVoucherDesc.setText("Đã áp dụng " + selectedVouchers.size() + " mã khuyến mãi");
            } else {
                tvAppliedVoucherDesc.setText(mainVch.getDescription());
            }
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
        double totalDiscount = 0;
        shippingDiscount = 0;

        for (Voucher v : selectedVouchers) {
            double saving = v.getDiscountAmount();
            // Nếu giá trị < 100 thì tính theo %
            if (saving > 0 && saving < 100) {
                if (v.getType() == Voucher.Type.SHIPPING) {
                    saving = (saving / 100.0) * shippingFee;
                } else {
                    saving = (saving / 100.0) * itemsTotal;
                }
            }

            if (v.getType() == Voucher.Type.SHIPPING) {
                shippingDiscount += Math.min(saving, shippingFee);
            } else {
                totalDiscount += saving;
            }
        }

        double grandTotal = Math.max(0, itemsTotal + shippingFee - shippingDiscount - totalDiscount);

        tvTotalItemPrice.setText(formatVnd(itemsTotal));
        tvShippingFee.setText(formatVnd(shippingFee));
        tvShippingDiscount.setText("-" + formatVnd(shippingDiscount));
        tvVoucherDiscount.setText("-" + formatVnd(totalDiscount));
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
            tvRecipientInfo.setText("Chưa có địa chỉ");
            tvAddressDetail.setText("Bấm để chọn địa chỉ giao hàng");
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
        btnPlaceOrder.setText("Đang xử lý...");

        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        double itemsTotal = getItemsTotal();
        double totalDiscount = 0;
        double currentShippingDiscount = 0;
        for (Voucher v : selectedVouchers) {
            if (v.getType() == Voucher.Type.SHIPPING) {
                currentShippingDiscount = Math.min(v.getDiscountAmount(), shippingFee);
            } else {
                totalDiscount += v.getDiscountAmount();
            }
        }
        double finalAmount = Math.max(0, itemsTotal + shippingFee - currentShippingDiscount - totalDiscount);

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
        order.setShippingFee(shippingFee - currentShippingDiscount);
        order.setDiscountAmount(totalDiscount);
        order.setTotalPrice(finalAmount);
        
        // Map payment method code to display name
        String paymentDisplay = selectedPaymentMethod;
        if ("cod".equals(selectedPaymentMethod)) paymentDisplay = "Thanh toán khi nhận hàng (COD)";
        else if ("momo".equals(selectedPaymentMethod)) paymentDisplay = "Ví MoMo";
        else if ("zalopay".equals(selectedPaymentMethod)) paymentDisplay = "Ví ZaloPay";
        else if ("vnpay".equals(selectedPaymentMethod)) paymentDisplay = "Ví VNPAY";
        else if ("card".equals(selectedPaymentMethod)) paymentDisplay = "Thẻ Tín dụng / Ghi nợ";
        
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
