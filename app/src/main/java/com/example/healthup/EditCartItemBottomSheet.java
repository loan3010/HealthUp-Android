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
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.NumberFormat;
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
        View view = inflater.inflate(R.layout.bottom_sheet_edit_cart_item, container, false);

        ImageView imgProduct = view.findViewById(R.id.imgProduct);
        TextView tvName = view.findViewById(R.id.tvName);
        TextView tvPrice = view.findViewById(R.id.tvPrice);
        TextView tvStockWarning = view.findViewById(R.id.tvStockWarning);
        TextView tvQuantity = view.findViewById(R.id.tvQuantity);
        View btnDecrease = view.findViewById(R.id.btnDecrease);
        View btnIncrease = view.findViewById(R.id.btnIncrease);
        View btnConfirm = view.findViewById(R.id.btnConfirm);

        FlexboxLayout groupWeight = view.findViewById(R.id.groupWeight);
        FlexboxLayout groupFlavor = view.findViewById(R.id.groupFlavor);
        FlexboxLayout groupPackage = view.findViewById(R.id.groupPackage);

        tvName.setText(item.getName());
        tvPrice.setText("đ " + currencyFormat.format(item.getPrice()));
        if (item.getStock() > 0 && item.getStock() <= 3) {
            tvStockWarning.setVisibility(View.VISIBLE);
            tvStockWarning.setText("Chỉ còn " + item.getStock() + " sản phẩm");
        }
        tvQuantity.setText(String.valueOf(quantity));

        // TODO: thay bằng danh sách biến thể thật lấy từ Firestore (product.variants)
        buildOptionGroup(groupWeight, Arrays.asList("250g", "400g", "1kg"), selectedWeight,
                value -> selectedWeight = value);
        buildOptionGroup(groupFlavor, Arrays.asList("Nguyên bản", "Socola", "Mật ong"), selectedFlavor,
                value -> selectedFlavor = value);
        buildOptionGroup(groupPackage, Arrays.asList("Túi zip", "Hũ thủy tinh"), selectedPackage,
                value -> selectedPackage = value);

        btnIncrease.setOnClickListener(v -> {
            if (item.getStock() > 0 && quantity + 1 > item.getStock()) return;
            quantity++;
            tvQuantity.setText(String.valueOf(quantity));
        });

        btnDecrease.setOnClickListener(v -> {
            if (quantity <= 1) return;
            quantity--;
            tvQuantity.setText(String.valueOf(quantity));
        });

        btnConfirm.setOnClickListener(v -> {
            listener.onConfirm(selectedWeight, selectedFlavor, selectedPackage, quantity);
            dismiss();
        });

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(item.getImageUrl()).into(imgProduct);
        }

        return view;
    }

    private interface OnOptionSelected {
        void onSelected(String value);
    }

    private void buildOptionGroup(FlexboxLayout container, List<String> options,
                                  String currentValue, OnOptionSelected callback) {
        container.removeAllViews();
        for (String option : options) {
            TextView chip = new TextView(getContext());
            chip.setText(option);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackgroundResource(R.drawable.bg_option_chip);
            chip.setSelected(option.equals(currentValue));
            chip.setTextColor(getResources().getColor(
                    option.equals(currentValue) ? R.color.green_button : R.color.text_dark));

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), dp(8));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    child.setSelected(false);
                    ((TextView) child).setTextColor(getResources().getColor(R.color.text_dark));
                }
                chip.setSelected(true);
                chip.setTextColor(getResources().getColor(R.color.green_button));
                callback.onSelected(option);
            });

            container.addView(chip);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}