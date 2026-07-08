package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.CartItem;
import com.example.models.Product;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.example.healthup.util.ImageLoadHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EditCartItemBottomSheet extends BottomSheetDialogFragment {

    public interface OnConfirmListener {
        void onConfirm(String weight, String flavor, String packageType, int quantity, double price);
    }

    private final CartItem item;
    private final OnConfirmListener listener;

    private String selectedWeight;
    private String selectedFlavor;
    private String selectedPackage;
    private int quantity;

    private FirebaseFirestore db;
    private View rootView;
    private ChipGroup cgWeight, cgFlavor, cgPackage;
    private TextView tvLabelWeight, tvLabelFlavor, tvLabelPackage;
    private TextView tvPrice;
    private Product loadedProduct;

    // FIX: dùng cùng định dạng "#,###đ" (đ ở CUỐI) giống hệt popup "Thêm vào giỏ hàng"
    private final DecimalFormat currencyFormat = new DecimalFormat("#,###đ");

    public EditCartItemBottomSheet(CartItem item, OnConfirmListener listener) {
        this.item = item;
        this.listener = listener;
        this.selectedWeight = extractLabel(item.getWeight());
        this.selectedFlavor = extractLabel(item.getFlavor());
        this.selectedPackage = extractLabel(item.getPackageType());
        this.quantity = item.getQuantity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.bottom_sheet_edit_cart_item, container, false);

        db = FirebaseFirestore.getInstance();

        ImageView imgProduct = rootView.findViewById(R.id.imgProduct);
        TextView tvName = rootView.findViewById(R.id.tvName);
        tvPrice = rootView.findViewById(R.id.tvPrice);
        TextView tvStockWarning = rootView.findViewById(R.id.tvStockWarning);
        TextView tvQuantity = rootView.findViewById(R.id.tvQuantity);
        View btnDecrease = rootView.findViewById(R.id.btnDecrease);
        View btnIncrease = rootView.findViewById(R.id.btnIncrease);
        View btnConfirm = rootView.findViewById(R.id.btnConfirm);
        View btnClose = rootView.findViewById(R.id.btnCloseEditCart);

        cgWeight = rootView.findViewById(R.id.cgWeight);
        cgFlavor = rootView.findViewById(R.id.cgFlavor);
        cgPackage = rootView.findViewById(R.id.cgPackage);

        tvLabelWeight = rootView.findViewById(R.id.tvLabelWeight);
        tvLabelFlavor = rootView.findViewById(R.id.tvLabelFlavor);
        tvLabelPackage = rootView.findViewById(R.id.tvLabelPackage);

        tvName.setText(item.getName());
        updatePriceDisplay();
        tvQuantity.setText(String.valueOf(quantity));

        loadProductOptions();

        btnIncrease.setOnClickListener(v -> {
            quantity++;
            tvQuantity.setText(String.valueOf(quantity));
        });

        btnDecrease.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
            }
        });

        btnConfirm.setOnClickListener(v -> {
            listener.onConfirm(selectedWeight, selectedFlavor, selectedPackage, quantity, resolveSelectedPrice());
            dismiss();
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        ImageLoadHelper.loadInto(imgProduct, item.getImageUrl());

        return rootView;
    }

    private void loadProductOptions() {
        ImageView imgProduct = rootView.findViewById(R.id.imgProduct);
        ImageLoadHelper.loadInto(imgProduct, item.getImageUrl());

        if (item.getProductId() == null) {
            showFallbackOptions();
            return;
        }

        db.collection("products").document(item.getProductId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Product p = Product.fromDocument(doc);
                        if (p != null) {
                            loadedProduct = p;
                            if (item.getImageUrl() == null || item.getImageUrl().isEmpty()) {
                                String productImage = p.getImageUrl();
                                if (productImage != null && !productImage.isEmpty()) {
                                    item.setImageUrl(productImage);
                                    ImageLoadHelper.loadInto(imgProduct, productImage);
                                }
                            }
                            updateOptionsUI(p);
                            updatePriceDisplay();
                        }
                    } else {
                        showFallbackOptions();
                    }
                })
                .addOnFailureListener(e -> showFallbackOptions());
    }

    private void showFallbackOptions() {
        updateUISection(tvLabelWeight, cgWeight, Arrays.asList("250g", "500g", "1kg"), selectedWeight, v -> selectedWeight = v);
        updateUISection(tvLabelFlavor, cgFlavor, Arrays.asList("Vị Socola", "Vị Mật Ong", "Nguyên Bản"), selectedFlavor, v -> selectedFlavor = v);
        updateUISection(tvLabelPackage, cgPackage, Arrays.asList("Túi zip", "Hũ thủy tinh"), selectedPackage, v -> selectedPackage = v);
    }

    private void updateOptionsUI(Product product) {
        loadedProduct = product;
        updateUISection(tvLabelWeight, cgWeight, convertToStringList(product.getWeights()), selectedWeight, v -> {
            selectedWeight = v;
            updatePriceDisplay();
        });
        updateUISection(tvLabelFlavor, cgFlavor, convertToStringList(product.getFlavors()), selectedFlavor, v -> selectedFlavor = v);
        updateUISection(tvLabelPackage, cgPackage, convertToStringList(product.getPackagingTypes()), selectedPackage, v -> selectedPackage = v);
    }

    private void updatePriceDisplay() {
        if (tvPrice == null) return;
        tvPrice.setText(currencyFormat.format(resolveSelectedPrice()));
    }

    private double resolveSelectedPrice() {
        if (loadedProduct != null) {
            return loadedProduct.getPriceForOption(selectedWeight);
        }
        return item.getPrice();
    }

    private void updateUISection(TextView label, ChipGroup group, List<String> options, String current, OnOptionSelected callback) {
        if (options == null || options.isEmpty()) {
            label.setVisibility(View.GONE);
            group.setVisibility(View.GONE);
            return;
        }
        label.setVisibility(View.VISIBLE);
        group.setVisibility(View.VISIBLE);
        buildOptionGroup(group, options, current, callback);
    }

    private List<String> convertToStringList(List<Object> input) {
        List<String> result = new ArrayList<>();
        if (input == null) return result;
        for (Object obj : input) {
            String label = Product.extractOptionLabel(obj);
            if (!label.isEmpty()) result.add(label);
        }
        return result;
    }

    private interface OnOptionSelected {
        void onSelected(String value);
    }

    private void buildOptionGroup(ChipGroup container, List<String> options, String currentValue, OnOptionSelected callback) {
        container.removeAllViews();
        String normalizedCurrent = normalize(currentValue);

        for (String rawOption : options) {
            String displayLabel = extractLabel(rawOption);

            Chip chip = new Chip(requireContext());
            chip.setText(displayLabel);
            chip.setCheckable(true);
            chip.setClickable(true);

            boolean isSelected = normalize(rawOption).equals(normalizedCurrent)
                    || normalize(displayLabel).equals(normalizedCurrent);
            chip.setChecked(isSelected);

            chip.setOnClickListener(v -> {
                for (int i = 0; i < container.getChildCount(); i++) {
                    Chip child = (Chip) container.getChildAt(i);
                    child.setChecked(false);
                }
                chip.setChecked(true);
                callback.onSelected(displayLabel);
            });

            container.addView(chip);
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private String extractLabel(String input) {
        if (input == null) return "";
        if (input.contains("label=")) {
            try {
                int start = input.indexOf("label=") + 6;
                int end = input.indexOf(",", start);
                if (end == -1) end = input.indexOf("}", start);
                if (end != -1) return input.substring(start, end).trim();
            } catch (Exception ignored) {}
        }
        return input;
    }
}