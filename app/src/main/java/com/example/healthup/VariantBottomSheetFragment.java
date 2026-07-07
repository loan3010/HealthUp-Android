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
    // FIX: thêm cờ để biết đang mở popup từ nút "Mua ngay" hay "Thêm vào giỏ hàng",
    // dùng để hiển thị đúng chữ trên nút xác nhận thay vì luôn cố định "Thêm vào giỏ hàng".
    private boolean isBuyNow = false;


    private ImageView ivProduct;
    private TextView tvPrice, tvStock, tvSelectedName, tvQuantity, tvVariantSectionLabel;
    private ChipGroup chipGroup;
    private MaterialButton btnConfirm;
    private ImageButton btnMinus, btnPlus, btnClose;


    /** Giữ overload cũ để không phải sửa các nơi đang gọi (mặc định là "Thêm vào giỏ hàng"). */
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


        ivProduct = view.findViewById(R.id.iv_variant_product);
        tvPrice = view.findViewById(R.id.tv_variant_price);
        tvStock = view.findViewById(R.id.tv_variant_stock);
        tvSelectedName = view.findViewById(R.id.tv_variant_selected_name);
        tvVariantSectionLabel = view.findViewById(R.id.tv_variant_section_label);
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


        // FIX ROOT CAUSE: trước đây LUÔN nối "images/products/" vào trước imageUrl, kể cả khi
        // imageUrl đã có sẵn tiền tố này (ví dụ "images/products/granola.png") -> đường dẫn bị
        // lặp thành "images/products/images/products/granola.png" -> Glide không tìm thấy file
        // -> ảnh hiển thị trống. Sửa lại theo đúng cách các Adapter khác trong app đang xử lý.
        String imageUrl = product.getImageUrl();
        Object loadTarget;
        if (imageUrl == null || imageUrl.isEmpty()) {
            loadTarget = R.drawable.ic_launcher_background;
        } else if (imageUrl.startsWith("http") || imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
            loadTarget = imageUrl;
        } else {
            String cleanPath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
            if (cleanPath.startsWith("images/")) {
                loadTarget = "file:///android_asset/" + cleanPath;
            } else {
                loadTarget = "file:///android_asset/images/products/" + cleanPath;
            }
        }
        Glide.with(this)
                .load(loadTarget)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(ivProduct);
        updateDisplay();


        // FIX: nếu sản phẩm không có variant, ẩn hẳn khối "Phân loại" thay vì để trống trơ trọi.
        if (product.hasResolvableVariants()) {
            if (tvVariantSectionLabel != null) tvVariantSectionLabel.setVisibility(View.VISIBLE);
            chipGroup.setVisibility(View.VISIBLE);
            chipGroup.removeAllViews();
            for (Product.ProductVariant variant : product.getResolvableVariants()) {
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
        } else {
            if (tvVariantSectionLabel != null) tvVariantSectionLabel.setVisibility(View.GONE);
            chipGroup.setVisibility(View.GONE);
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
            if (product.hasResolvableVariants() && selectedVariant == null) {
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
        // FIX: viết trực tiếp thay vì phụ thuộc hoàn toàn vào string resource để đảm bảo
        // dòng "Phân loại" không bao giờ hiển thị trống.
        tvSelectedName.setText("Phân loại: " + (selectedVariant != null ? selectedVariant.getName() : "Mặc định"));


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
            // FIX: hiển thị đúng "Mua ngay" hoặc "Thêm vào giỏ hàng" tùy theo nút mà người dùng
            // đã bấm ở trang Chi tiết sản phẩm, thay vì luôn cố định là "Thêm vào giỏ hàng".
            btnConfirm.setText(isBuyNow ? "Mua ngay" : "Thêm vào giỏ hàng");
        }
    }
}