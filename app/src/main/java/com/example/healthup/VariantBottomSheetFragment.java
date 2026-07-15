package com.example.healthup;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.Product;
import com.example.models.Product.ProductVariant;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VariantBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnVariantSelectedListener {
        void onConfirm(ProductVariant variant, int quantity, Map<String, String> selections);
    }

    private Product product;
    private OnVariantSelectedListener listener;
    private ProductVariant selectedVariant;
    private final Map<String, ProductVariant> selectedVariantsMap = new LinkedHashMap<>();
    private int quantity = 1;
    private boolean isBuyNow = false;

    private ImageView ivImage;
    private TextView tvPrice, tvStock, tvSelectedName, tvQuantity;
    private LinearLayout layoutGroups;
    private MaterialButton btnConfirm;
    private ImageButton btnMinus, btnPlus, btnClose;

    public static VariantBottomSheetFragment newInstance(Product product, OnVariantSelectedListener listener) {
        return newInstance(product, false, listener);
    }

    public static VariantBottomSheetFragment newInstance(Product product, boolean isBuyNow,
                                                         OnVariantSelectedListener listener) {
        VariantBottomSheetFragment fragment = new VariantBottomSheetFragment();
        fragment.product = product;
        fragment.isBuyNow = isBuyNow;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_variant_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivImage = view.findViewById(R.id.iv_variant_image);
        tvPrice = view.findViewById(R.id.tv_variant_price);
        tvStock = view.findViewById(R.id.tv_variant_stock);
        tvSelectedName = view.findViewById(R.id.tv_variant_selected_name);
        tvQuantity = view.findViewById(R.id.tv_quantity);
        layoutGroups = view.findViewById(R.id.layout_variant_groups);
        btnConfirm = view.findViewById(R.id.btn_confirm_variant);
        btnMinus = view.findViewById(R.id.btn_minus_qty);
        btnPlus = view.findViewById(R.id.btn_plus_qty);
        btnClose = view.findViewById(R.id.btn_close_variant);

        setupUI();
    }

    private void setupUI() {
        if (product == null) return;

        refreshPreviewImage();
        updateDisplay();

        Map<String, List<ProductVariant>> grouped = product.getGroupedVariants();
        layoutGroups.removeAllViews();
        selectedVariantsMap.clear();

        if (!grouped.isEmpty()) {
            for (Map.Entry<String, List<ProductVariant>> entry : grouped.entrySet()) {
                View groupView = getLayoutInflater().inflate(R.layout.layout_variant_group, layoutGroups, false);
                TextView tvLabel = groupView.findViewById(R.id.tv_group_label);
                ChipGroup cg = groupView.findViewById(R.id.chip_group_variants);

                tvLabel.setText(entry.getKey());
                cg.removeAllViews();
                cg.setTag(entry.getKey());

                List<ProductVariant> options = entry.getValue();
                for (ProductVariant v : options) {
                    Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_variant_chip, cg, false);
                    chip.setText(v.getName());
                    chip.setTag(v);
                    chip.setOnClickListener(view -> {
                        if (!chip.isEnabled()) {
                            Toast.makeText(getContext(),
                                    "Tổ hợp này không có hoặc hết hàng", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectVariantInGroup(entry.getKey(), cg, v);
                    });
                    cg.addView(chip);
                }
                layoutGroups.addView(groupView);
            }
            // Auto-pick first available path across dimensions
            autoSelectFirstAvailable(grouped);
            refreshChipAvailability();
        } else {
            selectedVariant = null;
            updateDisplay();
        }

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
            }
        });

        btnPlus.setOnClickListener(v -> {
            int maxStock = resolveDisplayStock();
            if (quantity < maxStock) {
                quantity++;
                tvQuantity.setText(String.valueOf(quantity));
            } else {
                Toast.makeText(getContext(), "Đã đạt giới hạn kho hàng", Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            ProductVariant toConfirm = product.resolveComboVariant(selectedVariantsMap);
            if (toConfirm == null) {
                toConfirm = selectedVariant;
            }
            if (!product.getGroupedVariants().isEmpty()) {
                if (toConfirm == null || !toConfirm.isEnabled()) {
                    Toast.makeText(getContext(), "Vui lòng chọn phân loại còn bán", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (toConfirm.getStock() <= 0) {
                    Toast.makeText(getContext(), "Phân loại này đã hết hàng", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (listener != null) {
                Map<String, String> selections = new HashMap<>();
                for (Map.Entry<String, ProductVariant> entry : selectedVariantsMap.entrySet()) {
                    selections.put(entry.getKey(), entry.getValue().getName());
                }
                listener.onConfirm(toConfirm, quantity, selections);
            }
            dismiss();
        });
    }

    private void autoSelectFirstAvailable(@NonNull Map<String, List<ProductVariant>> grouped) {
        Map<String, String> trial = new LinkedHashMap<>();
        for (Map.Entry<String, List<ProductVariant>> entry : grouped.entrySet()) {
            String dimName = entry.getKey();
            ProductVariant picked = null;
            for (ProductVariant option : entry.getValue()) {
                if (product.isOptionAvailable(dimName, option.getName(), trial)) {
                    picked = option;
                    break;
                }
            }
            if (picked == null && !entry.getValue().isEmpty()) {
                picked = entry.getValue().get(0);
            }
            if (picked != null) {
                trial.put(dimName, picked.getName());
                selectedVariantsMap.put(dimName, picked);
            }
        }
        applySelectionStyles();
        selectedVariant = product.resolveComboVariant(selectedVariantsMap);
        refreshPreviewImage();
        updateDisplay();
    }

    private void selectVariantInGroup(String groupName, ChipGroup group, ProductVariant variant) {
        selectedVariantsMap.put(groupName, variant);
        applySelectionStyles();
        refreshChipAvailability();

        // If current path became invalid for another dim, clear/re-pick that dim.
        Map<String, String> current = currentSelectionLabels();
        for (Map.Entry<String, ProductVariant> entry : new LinkedHashMap<>(selectedVariantsMap).entrySet()) {
            if (entry.getKey().equals(groupName)) continue;
            if (!product.isOptionAvailable(entry.getKey(), entry.getValue().getName(),
                    withoutKey(current, entry.getKey()))) {
                // Drop invalid sibling and pick first available
                List<ProductVariant> options = product.getGroupedVariants().get(entry.getKey());
                ProductVariant replacement = null;
                if (options != null) {
                    Map<String, String> base = withoutKey(current, entry.getKey());
                    base.put(groupName, variant.getName());
                    for (ProductVariant opt : options) {
                        if (product.isOptionAvailable(entry.getKey(), opt.getName(), base)) {
                            replacement = opt;
                            break;
                        }
                    }
                }
                if (replacement != null) {
                    selectedVariantsMap.put(entry.getKey(), replacement);
                } else {
                    selectedVariantsMap.remove(entry.getKey());
                }
            }
        }

        applySelectionStyles();
        refreshChipAvailability();
        selectedVariant = product.resolveComboVariant(selectedVariantsMap);
        refreshPreviewImage();
        updateDisplay();
    }

    @NonNull
    private Map<String, String> currentSelectionLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, ProductVariant> e : selectedVariantsMap.entrySet()) {
            if (e.getValue() != null && e.getValue().getName() != null) {
                map.put(e.getKey(), e.getValue().getName());
            }
        }
        return map;
    }

    @NonNull
    private Map<String, String> withoutKey(@NonNull Map<String, String> source, @NonNull String key) {
        Map<String, String> copy = new LinkedHashMap<>(source);
        copy.remove(key);
        return copy;
    }

    private void applySelectionStyles() {
        for (int g = 0; g < layoutGroups.getChildCount(); g++) {
            View groupView = layoutGroups.getChildAt(g);
            ChipGroup cg = groupView.findViewById(R.id.chip_group_variants);
            if (cg == null) continue;
            String dimName = cg.getTag() instanceof String ? (String) cg.getTag() : null;
            ProductVariant selected = dimName != null ? selectedVariantsMap.get(dimName) : null;
            String selectedName = selected != null ? selected.getName() : null;
            for (int i = 0; i < cg.getChildCount(); i++) {
                Chip chip = (Chip) cg.getChildAt(i);
                boolean isThis = selectedName != null
                        && selectedName.equals(chip.getText().toString());
                if (chip.isEnabled()) {
                    updateVariantChipStyle(chip, isThis);
                }
                chip.setChecked(isThis);
            }
        }
    }

    /** Grey out options that have no enabled SKU under current other selections (Shopee-style). */
    private void refreshChipAvailability() {
        Map<String, String> current = currentSelectionLabels();
        for (int g = 0; g < layoutGroups.getChildCount(); g++) {
            View groupView = layoutGroups.getChildAt(g);
            ChipGroup cg = groupView.findViewById(R.id.chip_group_variants);
            if (cg == null) continue;
            String dimName = cg.getTag() instanceof String ? (String) cg.getTag() : null;
            if (dimName == null) continue;
            Map<String, String> others = withoutKey(current, dimName);
            for (int i = 0; i < cg.getChildCount(); i++) {
                Chip chip = (Chip) cg.getChildAt(i);
                String label = chip.getText().toString();
                boolean available = product.isOptionAvailable(dimName, label, others);
                chip.setEnabled(available);
                chip.setAlpha(available ? 1f : 0.35f);
                if (!available) {
                    chip.setChecked(false);
                    chip.setChipBackgroundColorResource(R.color.bg_chip_filter);
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                }
            }
        }
    }

    private void updateVariantChipStyle(Chip chip, boolean isSelected) {
        if (!chip.isEnabled()) return;
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_default);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.bg_chip_filter);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark));
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setChipStrokeColorResource(R.color.primary_default);
        }
    }

    private void refreshPreviewImage() {
        if (ivImage == null || product == null) return;
        ImageLoadHelper.loadInto(ivImage, resolvePreviewImageUrl());
    }

    @Nullable
    private String resolvePreviewImageUrl() {
        if (selectedVariant != null && !TextUtils.isEmpty(selectedVariant.getImageUrl())) {
            return selectedVariant.getImageUrl();
        }
        List<String> productImages = product.getImages();
        if (productImages != null) {
            for (String img : productImages) {
                if (!TextUtils.isEmpty(img)) return img;
            }
        }
        return product.getImageUrl();
    }

    private int resolveDisplayStock() {
        if (selectedVariant != null) {
            return Math.max(0, selectedVariant.getStock());
        }
        if (!product.getGroupedVariants().isEmpty()) {
            return 0; // chưa resolve SKU — không hiện tồn tổng gây hiểu nhầm
        }
        return product.getAvailableStock();
    }

    private void updateDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        boolean hasGroups = !product.getGroupedVariants().isEmpty();
        double price = product.resolveUnitPrice(selectedVariant);
        int stock = resolveDisplayStock();

        tvPrice.setText(formatter.format(price) + "đ");
        if (hasGroups && selectedVariant == null) {
            tvStock.setText("Kho: —");
        } else {
            tvStock.setText("Kho: " + (stock > 0 ? stock : getString(R.string.out_of_stock)));
        }

        if (!hasGroups) {
            tvSelectedName.setText("Không có phân loại");
        } else if (selectedVariantsMap.isEmpty()) {
            tvSelectedName.setText("Phân loại: Chưa chọn");
        } else {
            StringBuilder label = new StringBuilder("Phân loại: ");
            boolean first = true;
            for (ProductVariant v : selectedVariantsMap.values()) {
                String name = v.getName();
                if (name != null && !name.isEmpty()) {
                    if (!first) label.append(", ");
                    label.append(name);
                    first = false;
                }
            }
            tvSelectedName.setText(label.toString());
        }

        boolean productSellable = product.isInStock();
        boolean canBuy = !hasGroups
                ? productSellable && stock > 0
                : selectedVariant != null && selectedVariant.isEnabled() && selectedVariant.getStock() > 0;

        if (canBuy) {
            if (quantity > stock) {
                quantity = Math.max(1, stock);
            }
            if (quantity < 1) quantity = 1;
            tvQuantity.setText(String.valueOf(quantity));
            btnConfirm.setEnabled(true);
            btnConfirm.setAlpha(1f);
            btnConfirm.setText(isBuyNow ? getString(R.string.buy_now) : "Thêm vào giỏ hàng");
        } else {
            quantity = hasGroups ? 1 : 0;
            tvQuantity.setText(String.valueOf(Math.max(quantity, 0)));
            btnConfirm.setEnabled(false);
            btnConfirm.setAlpha(0.45f);
            if (!productSellable) {
                btnConfirm.setText(R.string.out_of_stock);
            } else {
                btnConfirm.setText(hasGroups && selectedVariant == null
                        ? "Chọn phân loại"
                        : getString(R.string.out_of_stock));
            }
        }
    }
}
