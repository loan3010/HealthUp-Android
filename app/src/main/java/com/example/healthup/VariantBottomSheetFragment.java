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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VariantBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnVariantSelectedListener {
        void onConfirm(ProductVariant variant, int quantity);
    }

    private Product product;
    private OnVariantSelectedListener listener;
    private ProductVariant selectedVariant;
    private Map<String, ProductVariant> selectedVariantsMap = new java.util.LinkedHashMap<>();
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

    public static VariantBottomSheetFragment newInstance(Product product, boolean isBuyNow, OnVariantSelectedListener listener) {
        VariantBottomSheetFragment fragment = new VariantBottomSheetFragment();
        fragment.product = product;
        fragment.isBuyNow = isBuyNow;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        if (!grouped.isEmpty()) {
            for (Map.Entry<String, List<ProductVariant>> entry : grouped.entrySet()) {
                View groupView = getLayoutInflater().inflate(R.layout.layout_variant_group, layoutGroups, false);
                TextView tvLabel = groupView.findViewById(R.id.tv_group_label);
                ChipGroup cg = groupView.findViewById(R.id.chip_group_variants);

                tvLabel.setText(entry.getKey());
                cg.removeAllViews();

                List<ProductVariant> variants = entry.getValue();
                for (ProductVariant v : variants) {
                    Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_variant_chip, cg, false);
                    chip.setText(v.getName());
                    chip.setOnClickListener(view -> selectVariantInGroup(entry.getKey(), cg, v));
                    cg.addView(chip);
                }
                layoutGroups.addView(groupView);
                if (!variants.isEmpty()) selectVariantInGroup(entry.getKey(), cg, variants.get(0));
            }
        }

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
            }
        });

        btnPlus.setOnClickListener(v -> {
            int maxStock = (selectedVariant != null) ? selectedVariant.getStock() : product.getStockCount();
            if (quantity < maxStock) {
                quantity++;
                tvQuantity.setText(String.valueOf(quantity));
            } else {
                Toast.makeText(getContext(), "Đã đạt giới hạn kho hàng", Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (product.hasResolvableVariants() && selectedVariant == null) {
                Toast.makeText(getContext(), "Vui lòng chọn phân loại", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onConfirm(selectedVariant, quantity);
            }
            dismiss();
        });
    }

    private void selectVariantInGroup(String groupName, ChipGroup group, ProductVariant variant) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            boolean isThis = chip.getText().toString().equals(variant.getName());
            updateVariantChipStyle(chip, isThis);
            if (isThis) chip.setChecked(true);
        }

        selectedVariantsMap.put(groupName, variant);

        ProductVariant bestVariant = null;

        if (selectedVariantsMap.containsKey("Khối lượng")) {
            ProductVariant v = selectedVariantsMap.get("Khối lượng");
            if (v != null && v.getPrice() > 0 && v.getPrice() != product.getPrice()) {
                bestVariant = v;
            }
        }

        if (bestVariant == null) {
            for (ProductVariant v : selectedVariantsMap.values()) {
                if (v.getPrice() != product.getPrice() && v.getPrice() > 0) {
                    bestVariant = v;
                    break;
                }
            }
        }

        if (bestVariant == null && !selectedVariantsMap.isEmpty()) {
            bestVariant = selectedVariantsMap.values().iterator().next();
        }

        selectedVariant = bestVariant;
        refreshPreviewImage();
        updateDisplay();
    }

    private void updateVariantChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark));
            chip.setChipStrokeWidth(1f);
            chip.setChipStrokeColorResource(android.R.color.darker_gray);
        }
    }

    private void refreshPreviewImage() {
        if (ivImage == null || product == null) return;
        String imageUrl = resolvePreviewImageUrl();
        ImageLoadHelper.loadInto(ivImage, imageUrl);
    }

    @Nullable
    private String resolvePreviewImageUrl() {
        if (selectedVariant != null && !TextUtils.isEmpty(selectedVariant.getImageUrl())) {
            return selectedVariant.getImageUrl();
        }
        for (ProductVariant v : selectedVariantsMap.values()) {
            if (v != null && !TextUtils.isEmpty(v.getImageUrl())) {
                return v.getImageUrl();
            }
        }
        List<String> productImages = product.getImages();
        if (productImages != null) {
            for (String img : productImages) {
                if (!TextUtils.isEmpty(img)) return img;
            }
        }
        return product.getImageUrl();
    }

    private void updateDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        double price = (selectedVariant != null) ? selectedVariant.getPrice() : product.getPrice();
        int stock = (selectedVariant != null) ? selectedVariant.getStock() : product.getStockCount();

        tvPrice.setText(formatter.format(price) + "đ");
        tvStock.setText("Kho: " + stock);

        StringBuilder label = new StringBuilder("Phân loại: ");
        if (selectedVariantsMap.isEmpty()) {
            label.append("Chưa chọn");
        } else {
            boolean first = true;
            for (Map.Entry<String, ProductVariant> entry : selectedVariantsMap.entrySet()) {
                if (!first) label.append(", ");
                label.append(entry.getValue().getName());
                first = false;
            }
        }
        tvSelectedName.setText(label.toString());

        if (quantity > stock && stock > 0) {
            quantity = stock;
            tvQuantity.setText(String.valueOf(quantity));
        } else if (stock == 0) {
            quantity = 0;
            tvQuantity.setText("0");
            btnConfirm.setEnabled(false);
            btnConfirm.setText("Hết hàng");
        } else {
            btnConfirm.setEnabled(true);
            btnConfirm.setText(isBuyNow ? "Mua ngay" : "Thêm vào giỏ hàng");
        }
    }
}
