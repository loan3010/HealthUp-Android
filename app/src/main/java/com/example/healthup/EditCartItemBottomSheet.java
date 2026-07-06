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
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EditCartItemBottomSheet extends BottomSheetDialogFragment {

    public interface OnConfirmListener {
        void onConfirm(String weight, String flavor, String packageType, int quantity);
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

    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

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
        TextView tvName = rootView.findViewById(R.id.tvName);
        TextView tvPrice = rootView.findViewById(R.id.tvPrice);
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
        tvPrice.setText("đ " + currencyFormat.format(item.getPrice()));
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
            listener.onConfirm(selectedWeight, selectedFlavor, selectedPackage, quantity);
            dismiss();
        });

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(item.getImageUrl()).into(imgProduct);
        }

        return rootView;
    }

    private void loadProductOptions() {
        if (item.getProductId() == null) {
            showFallbackOptions();
            return;
        }

        db.collection("products").document(item.getProductId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) updateOptionsUI(p);
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
        updateUISection(tvLabelWeight, groupWeight, convertToStringList(product.getWeights()), selectedWeight, v -> selectedWeight = v);
        updateUISection(tvLabelFlavor, groupFlavor, convertToStringList(product.getFlavors()), selectedFlavor, v -> selectedFlavor = v);
        updateUISection(tvLabelPackage, groupPackage, convertToStringList(product.getPackagingTypes()), selectedPackage, v -> selectedPackage = v);
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

    private List<String> convertToStringList(Object input) {
        List<String> result = new ArrayList<>();
        if (input instanceof List) {
            for (Object obj : (List<?>) input) result.add(String.valueOf(obj));
        }
        return result;
    }

    private interface OnOptionSelected {
        void onSelected(String value);
    }

    private void buildOptionGroup(FlexboxLayout container, List<String> options, String currentValue, OnOptionSelected callback) {
        container.removeAllViews();
        for (String option : options) {
            Chip chip = new Chip(requireContext());
            chip.setText(option);
            chip.setCheckable(true);
            boolean isSelected = option.equals(currentValue);
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
                callback.onSelected(option);
            });

            container.addView(chip);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
