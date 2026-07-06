package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.example.healthup.R;
import com.example.models.Product;
import java.text.NumberFormat;
import java.util.Locale;

public class VariantBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnVariantSelectedListener {
        void onConfirm(Product.ProductVariant variant, int quantity);
    }

    private Product product;
    private OnVariantSelectedListener listener;
    private Product.ProductVariant selectedVariant;
    private int quantity = 1;

    private ImageView ivProduct;
    private TextView tvPrice, tvStock, tvSelectedName, tvQuantity;
    private ChipGroup chipGroup;
    private MaterialButton btnConfirm;
    private ImageButton btnMinus, btnPlus, btnClose;

    public static VariantBottomSheetFragment newInstance(Product product, OnVariantSelectedListener listener) {
        VariantBottomSheetFragment fragment = new VariantBottomSheetFragment();
        fragment.product = product;
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

        ivProduct = view.findViewById(R.id.iv_variant_product);
        tvPrice = view.findViewById(R.id.tv_variant_price);
        tvStock = view.findViewById(R.id.tv_variant_stock);
        tvSelectedName = view.findViewById(R.id.tv_variant_selected_name);
        tvQuantity = view.findViewById(R.id.tv_quantity);
        chipGroup = view.findViewById(R.id.chip_group_variants_sheet);
        btnConfirm = view.findViewById(R.id.btn_confirm_variant);
        btnMinus = view.findViewById(R.id.btn_minus_qty);
        btnPlus = view.findViewById(R.id.btn_plus_qty);
        btnClose = view.findViewById(R.id.btn_close_variant);

        setupUI();
    }

    private void setupUI() {
        if (product == null) return;

        String imageUrl = product.getImageUrl();
        Object loadTarget = imageUrl;
        if (imageUrl != null && !imageUrl.startsWith("http") && !imageUrl.startsWith("file://") && !imageUrl.startsWith("content://")) {
            loadTarget = "file:///android_asset/images/products/" + imageUrl;
        }
        Glide.with(this).load(loadTarget).into(ivProduct);
        updateDisplay();

        if (product.getVariants() != null) {
            for (Product.ProductVariant variant : product.getVariants()) {
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_variant_chip, chipGroup, false);
                chip.setText(variant.getName());
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedVariant = variant;
                        updateDisplay();
                    }
                });
                chipGroup.addView(chip);
            }
            if (chipGroup.getChildCount() > 0) {
                ((Chip) chipGroup.getChildAt(0)).setChecked(true);
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
                Toast.makeText(getContext(), getString(R.string.max_stock_reached), Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (product.isHasVariants() && selectedVariant == null) {
                Toast.makeText(getContext(), getString(R.string.please_select_variant), Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onConfirm(selectedVariant, quantity);
            }
            dismiss();
        });
    }

    private void updateDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        double price = (selectedVariant != null) ? selectedVariant.getPrice() : product.getPrice();
        int stock = (selectedVariant != null) ? selectedVariant.getStock() : product.getStockCount();

        tvPrice.setText(formatter.format(price) + "đ");
        tvStock.setText(getString(R.string.stock_prefix, stock));
        tvSelectedName.setText(getString(R.string.variant_prefix, (selectedVariant != null ? selectedVariant.getName() : getString(R.string.default_variant))));
        
        if (quantity > stock && stock > 0) {
            quantity = stock;
            tvQuantity.setText(String.valueOf(quantity));
        } else if (stock == 0) {
            quantity = 0;
            tvQuantity.setText("0");
            btnConfirm.setEnabled(false);
            btnConfirm.setText(getString(R.string.out_of_stock));
        } else {
            btnConfirm.setEnabled(true);
            btnConfirm.setText(getString(R.string.add_to_cart));
        }
    }
}
