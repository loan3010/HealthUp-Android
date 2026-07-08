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
    private TextView tvPrice, tvSelectedOptions;
    private Product loadedProduct;

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
        tvPrice = rootView.findViewById(R.id.tvPrice);
        tvSelectedOptions = rootView.findViewById(R.id.tvSelectedOptions);
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

        updatePriceDisplay();
        updateSelectedSummary();
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
        updateUISection(tvLabelWeight, cgWeight, Arrays.asList("250g", "500g", "1kg"), selectedWeight, v -> {
            selectedWeight = v;
            updateSelectedSummary();
        });
        updateUISection(tvLabelFlavor, cgFlavor, Arrays.asList("Vị Socola", "Vị Mật Ong", "Nguyên Bản"), selectedFlavor, v -> {
            selectedFlavor = v;
            updateSelectedSummary();
        });
        updateUISection(tvLabelPackage, cgPackage, Arrays.asList("Túi zip", "Hũ thủy tinh"), selectedPackage, v -> {
            selectedPackage = v;
            updateSelectedSummary();
        });
    }

    private void updateOptionsUI(Product product) {
        loadedProduct = product;
        updateUISection(tvLabelWeight, cgWeight, convertToStringList(product.getWeights()), selectedWeight, v -> {
            selectedWeight = v;
            updatePriceDisplay();
            updateSelectedSummary();
        });
        updateUISection(tvLabelFlavor, cgFlavor, convertToStringList(product.getFlavors()), selectedFlavor, v -> {
            selectedFlavor = v;
            updateSelectedSummary();
        });
        updateUISection(tvLabelPackage, cgPackage, convertToStringList(product.getPackagingTypes()), selectedPackage, v -> {
            selectedPackage = v;
            updateSelectedSummary();
        });
    }

    private void updatePriceDisplay() {
        if (tvPrice == null) return;
        tvPrice.setText(currencyFormat.format(resolveSelectedPrice()));
    }

    private void updateSelectedSummary() {
        if (tvSelectedOptions == null) return;
        List<String> parts = new ArrayList<>();
        if (selectedWeight != null && !selectedWeight.isEmpty()) parts.add(selectedWeight);
        if (selectedFlavor != null && !selectedFlavor.isEmpty()) parts.add(selectedFlavor);
        if (selectedPackage != null && !selectedPackage.isEmpty()) parts.add(selectedPackage);

        if (parts.isEmpty()) {
            tvSelectedOptions.setText("Phân loại: Chưa chọn");
        } else {
            tvSelectedOptions.setText("Phân loại: " + String.join(", ", parts));
        }
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

            // FIX: Inflate từ item_variant_chip.xml để lấy style giống popup gốc (green selected state)
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_variant_chip, container, false);
            chip.setText(displayLabel);
            chip.setCheckable(true);

            boolean isSelected = normalize(rawOption).equals(normalizedCurrent)
                    || normalize(displayLabel).equals(normalizedCurrent);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);

            chip.setOnClickListener(v -> {
                for (int i = 0; i < container.getChildCount(); i++) {
                    Chip child = (Chip) container.getChildAt(i);
                    child.setChecked(false);
                    updateChipStyle(child, false);
                }
                chip.setChecked(true);
                updateChipStyle(chip, true);
                callback.onSelected(displayLabel);
            });

            container.addView(chip);
        }
    }

    private void updateChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.bg_chip_filter);
            chip.setTextColor(getResources().getColor(R.color.text_dark));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setChipStrokeColorResource(R.color.primary_green);
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