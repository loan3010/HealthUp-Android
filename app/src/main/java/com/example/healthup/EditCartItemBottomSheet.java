package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditCartItemBottomSheet extends BottomSheetDialogFragment {

    public interface OnConfirmListener {
        void onConfirm(String weight, String flavor, String packageType, int quantity, double price, String variantId, String variantName);
    }

    private final CartItem item;
    private final OnConfirmListener listener;

    private String selectedWeight;
    private String selectedFlavor;
    private String selectedPackage;
    private int quantity;

    private FirebaseFirestore db;
    private View rootView;
    private LinearLayout layoutGroups;
    private TextView tvPrice, tvSelectedOptions, tvStockWarning;
    private Product loadedProduct;
    private boolean hasVariantGroups;

    private final DecimalFormat currencyFormat = new DecimalFormat("#,###đ");

    public EditCartItemBottomSheet(CartItem item, OnConfirmListener listener) {
        this.item = item;
        this.listener = listener;
        this.selectedWeight = item.getWeight();
        this.selectedFlavor = item.getFlavor();
        this.selectedPackage = item.getPackageType();
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
        tvStockWarning = rootView.findViewById(R.id.tvStockWarning);
        TextView tvQuantity = rootView.findViewById(R.id.tvQuantity);
        View btnDecrease = rootView.findViewById(R.id.btnDecrease);
        View btnIncrease = rootView.findViewById(R.id.btnIncrease);
        View btnConfirm = rootView.findViewById(R.id.btnConfirm);
        View btnClose = rootView.findViewById(R.id.btnCloseEditCart);

        layoutGroups = rootView.findViewById(R.id.layout_variant_groups);

        updatePriceDisplay();
        updateSelectedSummary();
        tvQuantity.setText(String.valueOf(quantity));

        loadProductOptions();

        btnIncrease.setOnClickListener(v -> {
            quantity++;
            tvQuantity.setText(String.valueOf(quantity));
            updatePriceDisplay();
        });

        btnDecrease.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
                updatePriceDisplay();
            }
        });

        btnConfirm.setOnClickListener(v -> {
            updateVariantImagePreview();
            Product.ProductVariant selectedSku = resolveSelectedVariant();
            String vId = selectedSku != null ? selectedSku.getId() : null;
            String vName = selectedSku != null ? selectedSku.getName() : null;
            listener.onConfirm(selectedWeight, selectedFlavor, selectedPackage, quantity, resolveSelectedPrice(), vId, vName);
            dismiss();
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        ImageLoadHelper.loadInto(imgProduct, item.getImageUrl());

        return rootView;
    }

    private void loadProductOptions() {
        if (item.getProductId() == null) {
            showNoVariantOptions();
            return;
        }

        db.collection("products").document(item.getProductId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Product p = Product.fromDocument(doc);
                        if (p != null) {
                            updateOptionsUI(p);
                            return;
                        }
                    }
                    showNoVariantOptions();
                })
                .addOnFailureListener(e -> showNoVariantOptions());
    }

    /** No hard-coded fake chips — match product-detail sheet when Firestore has no variants. */
    private void showNoVariantOptions() {
        hasVariantGroups = false;
        if (layoutGroups != null) {
            layoutGroups.removeAllViews();
        }
        int stock = loadedProduct != null ? loadedProduct.getAvailableStock() : item.getStock();
        updateStockDisplay(Math.max(0, stock));
        updatePriceDisplay();
        updateSelectedSummary();
    }

    private void updateOptionsUI(Product product) {
        loadedProduct = product;

        // Tự động khôi phục các lựa chọn từ variantName nếu có trường lẻ đang bị null
        if (selectedWeight == null || selectedFlavor == null || selectedPackage == null) {
            String vn = item.getVariantName();
            if (vn != null && !vn.isEmpty()) {
                Map<String, List<Product.ProductVariant>> allGroups = product.getGroupedVariants();
                for (Map.Entry<String, List<Product.ProductVariant>> entry : allGroups.entrySet()) {
                    for (Product.ProductVariant v : entry.getValue()) {
                        String vName = v.getName();
                        if (vName != null && vn.toLowerCase().contains(vName.toLowerCase())) {
                            String gn = entry.getKey().toLowerCase();
                            if (gn.contains("khối lượng") || gn.contains("weight")) {
                                if (selectedWeight == null) selectedWeight = vName;
                            } else if (gn.contains("hương vị") || gn.contains("flavor")) {
                                if (selectedFlavor == null) selectedFlavor = vName;
                            } else if (gn.contains("đóng gói") || gn.contains("package") || gn.contains("quy cách")) {
                                if (selectedPackage == null) selectedPackage = vName;
                            }
                        }
                    }
                }
            }
        }

        layoutGroups.removeAllViews();

        Map<String, List<Product.ProductVariant>> grouped = product.getGroupedVariants();
        if (grouped.isEmpty()) {
            showNoVariantOptions();
            return;
        }

        hasVariantGroups = true;
        for (Map.Entry<String, List<Product.ProductVariant>> entry : grouped.entrySet()) {
            View groupView = getLayoutInflater().inflate(R.layout.layout_variant_group, layoutGroups, false);
            TextView tvLabel = groupView.findViewById(R.id.tv_group_label);
            ChipGroup cg = groupView.findViewById(R.id.chip_group_variants);

            String groupName = entry.getKey();
            tvLabel.setText(groupName);

            String currentValue = "";
            String normalizedName = groupName.toLowerCase();
            if (normalizedName.contains("khối lượng") || normalizedName.contains("weight")) currentValue = selectedWeight;
            else if (normalizedName.contains("hương vị") || normalizedName.contains("flavor")) currentValue = selectedFlavor;
            else if (normalizedName.contains("đóng gói") || normalizedName.contains("package") || normalizedName.contains("quy cách")) currentValue = selectedPackage;
            else if (normalizedName.contains("phân loại")) {
                currentValue = selectedWeight != null ? selectedWeight : (selectedFlavor != null ? selectedFlavor : selectedPackage);
            }

            List<String> options = new ArrayList<>();
            for (Product.ProductVariant v : entry.getValue()) {
                options.add(v.getName());
            }

            buildOptionGroup(cg, options, currentValue, v -> {
                if (normalizedName.contains("khối lượng") || normalizedName.contains("weight")
                        || normalizedName.contains("phân loại")) {
                    selectedWeight = v;
                    // Flat "Phân loại" group — clear other dims so combo resolve stays single-SKU.
                    if (normalizedName.contains("phân loại")) {
                        selectedFlavor = null;
                        selectedPackage = null;
                    }
                } else if (normalizedName.contains("hương vị") || normalizedName.contains("flavor")) {
                    selectedFlavor = v;
                } else if (normalizedName.contains("đóng gói") || normalizedName.contains("package")
                        || normalizedName.contains("quy cách")) {
                    selectedPackage = v;
                } else {
                    // Unknown dimension label: still track as primary selection for price lookup.
                    selectedWeight = v;
                }

                updateStockDisplay(resolveSelectedStock());
                updatePriceDisplay();
                updateSelectedSummary();
                updateVariantImagePreview();
            });

            layoutGroups.addView(groupView);
        }
        updateStockDisplay(resolveSelectedStock());
        updatePriceDisplay();
        updateSelectedSummary();
        updateVariantImagePreview();
    }

    private void updateStockDisplay(int stock) {
        if (tvStockWarning == null) return;
        if (stock > 0) {
            tvStockWarning.setText("Kho: " + stock);
        } else {
            tvStockWarning.setText("Kho: Hết hàng");
        }
    }

    private int resolveSelectedStock() {
        Product.ProductVariant resolved = resolveSelectedVariant();
        if (resolved != null) {
            return Math.max(0, resolved.getStock());
        }
        if (loadedProduct == null) {
            return Math.max(0, item.getStock());
        }
        if (!hasVariantGroups) {
            return Math.max(0, loadedProduct.getAvailableStock());
        }
        // Variants exist but none selected yet — do not show product total as "selected" stock.
        return 0;
    }

    private void updateVariantImagePreview() {
        if (rootView == null || loadedProduct == null) return;
        ImageView imgProduct = rootView.findViewById(R.id.imgProduct);
        String imageUrl = null;
        Product.ProductVariant resolved = resolveSelectedVariant();
        if (resolved != null && resolved.getImageUrl() != null && !resolved.getImageUrl().isEmpty()) {
            imageUrl = resolved.getImageUrl();
        }
        if (imageUrl == null) {
            imageUrl = loadedProduct.getImageUrl();
        }
        if (imageUrl != null && !imageUrl.isEmpty()) {
            item.setImageUrl(imageUrl);
            ImageLoadHelper.loadInto(imgProduct, imageUrl);
        }
    }

    private void updatePriceDisplay() {
        if (tvPrice == null) return;
        tvPrice.setText(currencyFormat.format(resolveSelectedPrice() * quantity));
    }

    private void updateSelectedSummary() {
        if (tvSelectedOptions == null) return;
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        if (selectedWeight != null && !selectedWeight.isEmpty()) parts.add(selectedWeight);
        if (selectedFlavor != null && !selectedFlavor.isEmpty()) parts.add(selectedFlavor);
        if (selectedPackage != null && !selectedPackage.isEmpty()) parts.add(selectedPackage);

        if (!hasVariantGroups && parts.isEmpty()) {
            tvSelectedOptions.setText("Không có phân loại");
        } else if (parts.isEmpty()) {
            tvSelectedOptions.setText("Phân loại: Chưa chọn");
        } else {
            tvSelectedOptions.setText("Phân loại: " + String.join(", ", parts));
        }
    }

    @Nullable
    private Product.ProductVariant resolveSelectedVariant() {
        if (loadedProduct == null) return null;
        Map<String, Product.ProductVariant> selectedByGroup = new java.util.LinkedHashMap<>();
        Map<String, List<Product.ProductVariant>> grouped = loadedProduct.getGroupedVariants();
        for (Map.Entry<String, List<Product.ProductVariant>> entry : grouped.entrySet()) {
            String groupName = entry.getKey();
            String normalized = groupName != null ? groupName.toLowerCase(Locale.ROOT) : "";
            String label = null;
            if (normalized.contains("khối lượng") || normalized.contains("weight")
                    || normalized.contains("phân loại")) {
                label = selectedWeight;
            } else if (normalized.contains("hương vị") || normalized.contains("flavor")) {
                label = selectedFlavor;
            } else if (normalized.contains("đóng gói") || normalized.contains("package")
                    || normalized.contains("quy cách")) {
                label = selectedPackage;
            } else {
                label = selectedWeight != null ? selectedWeight
                        : (selectedFlavor != null ? selectedFlavor : selectedPackage);
            }
            if (label == null || label.trim().isEmpty()) continue;
            Product.ProductVariant match = null;
            if (entry.getValue() != null) {
                for (Product.ProductVariant opt : entry.getValue()) {
                    if (opt != null && label.equalsIgnoreCase(opt.getName())) {
                        match = opt;
                        break;
                    }
                }
            }
            if (match == null) {
                match = loadedProduct.findVariantByName(label);
            }
            if (match != null) {
                selectedByGroup.put(groupName, match);
            }
        }
        if (!selectedByGroup.isEmpty()) {
            Product.ProductVariant combo = loadedProduct.resolveComboVariant(selectedByGroup);
            if (combo != null) return combo;
        }
        if (selectedWeight != null && !selectedWeight.trim().isEmpty()) {
            return loadedProduct.findVariantByName(selectedWeight);
        }
        if (selectedFlavor != null && !selectedFlavor.trim().isEmpty()) {
            return loadedProduct.findVariantByName(selectedFlavor);
        }
        if (selectedPackage != null && !selectedPackage.trim().isEmpty()) {
            return loadedProduct.findVariantByName(selectedPackage);
        }
        return null;
    }

    private double resolveSelectedPrice() {
        if (loadedProduct != null) {
            return loadedProduct.resolveUnitPrice(resolveSelectedVariant());
        }
        return item.getPrice();
    }

    private interface OnOptionSelected {
        void onSelected(String value);
    }

    private void buildOptionGroup(ChipGroup container, List<String> options, String currentValue, OnOptionSelected callback) {
        container.removeAllViews();
        String normalizedCurrent = normalize(currentValue);

        for (String rawOption : options) {
            String displayLabel = extractLabel(rawOption);
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
            chip.setChipBackgroundColorResource(R.color.primary_default);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.bg_chip_filter);
            chip.setTextColor(getResources().getColor(R.color.text_dark));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setChipStrokeColorResource(R.color.primary_default);
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private String extractLabel(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            String[] keys = {"label=", "name=", "variantName=", "title="};
            for (String key : keys) {
                if (s.contains(key)) {
                    int start = s.indexOf(key) + key.length();
                    int end = s.indexOf(",", start);
                    if (end == -1) end = s.indexOf("}", start);
                    if (end != -1) {
                        String result = s.substring(start, end).trim();
                        if (!result.isEmpty() && !"null".equalsIgnoreCase(result)) {
                            return result;
                        }
                    }
                }
            }
        }
        return input;
    }
}