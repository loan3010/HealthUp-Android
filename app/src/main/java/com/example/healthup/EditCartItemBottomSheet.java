package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.CartItem;
import com.example.models.Product;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.example.healthup.util.ImageLoadHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
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
    private FlexboxLayout groupWeight, groupFlavor, groupPackage;
    private TextView tvLabelWeight, tvLabelFlavor, tvLabelPackage;
    private TextView tvPrice;
    private Product loadedProduct;

    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

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

        groupWeight = rootView.findViewById(R.id.groupWeight);
        groupFlavor = rootView.findViewById(R.id.groupFlavor);
        groupPackage = rootView.findViewById(R.id.groupPackage);

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

        ImageLoadHelper.loadInto(imgProduct, item.getImageUrl());

        return rootView;
    }

    private void loadProductOptions() {
        // Luôn load ảnh từ CartItem trước
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
                            // Chỉ cập nhật ảnh từ Product nếu CartItem không có ảnh
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
        if ("p1".equals(item.getProductId())) {
            updateUISection(tvLabelWeight, groupWeight, Arrays.asList("250g", "500g", "1kg"), selectedWeight, v -> selectedWeight = v);
            updateUISection(tvLabelFlavor, groupFlavor, Arrays.asList("Vị Socola", "Vị Mật Ong", "Nguyên Bản"), selectedFlavor, v -> selectedFlavor = v);
            updateUISection(tvLabelPackage, groupPackage, Arrays.asList("Túi zip", "Hũ thủy tinh"), selectedPackage, v -> selectedPackage = v);
        } else if ("p2".equals(item.getProductId())) {
            updateUISection(tvLabelWeight, groupWeight, Arrays.asList("500ml", "1000ml"), selectedWeight, v -> selectedWeight = v);
        }
    }

    private void updateOptionsUI(Product product) {
        loadedProduct = product;
        updateUISection(tvLabelWeight, groupWeight, convertToStringList(product.getWeights()), selectedWeight, v -> {
            selectedWeight = v;
            updatePriceDisplay();
        });
        updateUISection(tvLabelFlavor, groupFlavor, convertToStringList(product.getFlavors()), selectedFlavor, v -> selectedFlavor = v);
        updateUISection(tvLabelPackage, groupPackage, convertToStringList(product.getPackagingTypes()), selectedPackage, v -> selectedPackage = v);
    }

    private void updatePriceDisplay() {
        if (tvPrice == null) return;
        tvPrice.setText("đ " + currencyFormat.format(resolveSelectedPrice()));
    }

    private double resolveSelectedPrice() {
        if (loadedProduct != null) {
            return loadedProduct.getPriceForOption(selectedWeight);
        }
        return item.getPrice();
    }

    private void updateUISection(TextView label, FlexboxLayout group, List<String> options, String current, OnOptionSelected callback) {
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

    private void buildOptionGroup(FlexboxLayout container, List<String> options, String currentValue, OnOptionSelected callback) {
        container.removeAllViews();
        for (String rawOption : options) {
            // Fix: Trích xuất nhãn từ cấu trúc Map nếu cần (ví dụ: {label=100g})
            String displayLabel = extractLabel(rawOption);
            
            Chip chip = new Chip(requireContext());
            chip.setText(displayLabel);
            chip.setCheckable(true);
            
            // So sánh dựa trên giá trị gốc (rawOption) để giữ logic đồng bộ với Firestore
            boolean isSelected = rawOption.equals(currentValue) || displayLabel.equals(currentValue);
            chip.setChecked(isSelected);
            
            // Cập nhật style
            if (isSelected) {
                chip.setChipBackgroundColorResource(R.color.green_button);
                chip.setTextColor(getResources().getColor(R.color.white));
            } else {
                chip.setChipBackgroundColorResource(R.color.track_gray);
                chip.setTextColor(getResources().getColor(R.color.text_dark));
            }

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                for (int i = 0; i < container.getChildCount(); i++) {
                    Chip child = (Chip) container.getChildAt(i);
                    child.setChecked(false);
                    child.setChipBackgroundColorResource(R.color.track_gray);
                    child.setTextColor(getResources().getColor(R.color.text_dark));
                }
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.green_button);
                chip.setTextColor(getResources().getColor(R.color.white));
                // Trả về giá trị hiển thị để cập nhật lên UI
                callback.onSelected(displayLabel);
            });

            container.addView(chip);
        }
    }

    private String extractLabel(String input) {
        if (input == null) return "";
        if (input.contains("label=")) {
            // Thử trích xuất từ chuỗi dạng {outOfStock=false, label=100g}
            try {
                int start = input.indexOf("label=") + 6;
                int end = input.indexOf(",", start);
                if (end == -1) end = input.indexOf("}", start);
                if (end != -1) return input.substring(start, end).trim();
            } catch (Exception ignored) {}
        }
        return input;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
