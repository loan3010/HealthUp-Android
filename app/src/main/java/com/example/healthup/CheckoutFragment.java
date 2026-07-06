package com.example.healthup;

import android.os.Bundle;
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

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        Serializable data = getArguments() != null ? getArguments().getSerializable("selected_items") : null;
        if (data instanceof List) {
            selectedItems = (List<CartItem>) data;
        }

        bindViews(view);
        setupListeners();
        renderProductList();
        renderVouchers();
        renderAddress(); // Hiển thị địa chỉ ngay khi load (nếu có)
        calculateSummary();

        return view;
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
            if (checkedId == R.id.rbVnpay) selectedPaymentMethod = "vnpay";
            else if (checkedId == R.id.rbCard) selectedPaymentMethod = "card";
            else if (checkedId == R.id.rbZaloPay) selectedPaymentMethod = "zalopay";
            else if (checkedId == R.id.rbMomo) selectedPaymentMethod = "momo";
            else selectedPaymentMethod = "cod";
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
        bundle.putBoolean("has_visited", true); // Đánh dấu là đã vào rồi để tránh auto-reset
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
            if (v.getType() == Voucher.Type.SHIPPING) {
                shippingDiscount = Math.min(v.getDiscountAmount(), shippingFee);
            } else {
                totalDiscount += v.getDiscountAmount();
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

        // Check đăng nhập
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Chưa đăng nhập -> Chuyển sang màn hình Xác minh số điện thoại
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PhoneVerificationFragment())
                    .addToBackStack(null)
                    .commit();
        } else {
            // Đã đăng nhập -> Cho phép đặt hàng
            Toast.makeText(getContext(), "Đặt hàng thành công! (demo)", Toast.LENGTH_SHORT).show();
        }
    }
}
