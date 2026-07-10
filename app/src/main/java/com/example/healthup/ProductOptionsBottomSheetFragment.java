package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.BottomSheetProductOptionsBinding;
import com.example.models.Product;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.text.DecimalFormat;

public class ProductOptionsBottomSheetFragment extends BottomSheetDialogFragment {
    private BottomSheetProductOptionsBinding binding;
    private Product product;
    private String actionType; // "add" or "buy"
    private int quantity = 1;

    public static ProductOptionsBottomSheetFragment newInstance(Product product, String actionType) {
        ProductOptionsBottomSheetFragment fragment = new ProductOptionsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("product", product);
        args.putString("action_type", actionType);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetProductOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getArguments() != null) {
            product = (Product) getArguments().getSerializable("product");
            actionType = getArguments().getString("action_type");
        }

        if (product == null) {
            dismiss();
            return;
        }

        displayProductInfo();
        setupListeners();
    }

    private void displayProductInfo() {
        DecimalFormat df = new DecimalFormat("#,###đ");
        binding.tvPriceSmall.setText(df.format(product.getPrice()));
        binding.tvStock.setText("Kho: " + (product.getStock() > 0 ? product.getStock() : "Còn hàng"));
        
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            com.example.healthup.util.ImageLoadHelper.loadInto(
                    binding.ivProductSmall, product.getImages().get(0));
        }

        setupDynamicChips();

        binding.btnAction.setText(actionType.equals("buy") ? "Mua ngay" : "Thêm vào Giỏ hàng");
    }

    private void setupDynamicChips() {
        // Setup Weights
        if (product.getWeights() != null && !product.getWeights().isEmpty()) {
            binding.tvWeightLabel.setVisibility(View.VISIBLE);
            binding.cgWeight.setVisibility(View.VISIBLE);
            binding.cgWeight.removeAllViews();
            for (Object w : product.getWeights()) {
                addChipToGroup(binding.cgWeight, Product.extractOptionLabel(w));
            }
        } else {
            binding.tvWeightLabel.setVisibility(View.GONE);
            binding.cgWeight.setVisibility(View.GONE);
        }

        // Setup Flavors
        if (product.getFlavors() != null && !product.getFlavors().isEmpty()) {
            binding.tvFlavorLabel.setVisibility(View.VISIBLE);
            binding.cgFlavor.setVisibility(View.VISIBLE);
            binding.cgFlavor.removeAllViews();
            for (Object f : product.getFlavors()) {
                addChipToGroup(binding.cgFlavor, Product.extractOptionLabel(f));
            }
        } else {
            binding.tvFlavorLabel.setVisibility(View.GONE);
            binding.cgFlavor.setVisibility(View.GONE);
        }

        // Setup Packaging
        if (product.getPackagingTypes() != null && !product.getPackagingTypes().isEmpty()) {
            binding.tvPackagingLabel.setVisibility(View.VISIBLE);
            binding.cgPackaging.setVisibility(View.VISIBLE);
            binding.cgPackaging.removeAllViews();
            for (Object p : product.getPackagingTypes()) {
                addChipToGroup(binding.cgPackaging, Product.extractOptionLabel(p));
            }
        } else {
            binding.tvPackagingLabel.setVisibility(View.GONE);
            binding.cgPackaging.setVisibility(View.GONE);
        }
    }

    private void addChipToGroup(com.google.android.material.chip.ChipGroup group, String text) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setClickable(true);
        // Style the chip to match your theme if needed
        group.addView(chip);
        
        // Select the first chip by default if none selected
        if (group.getCheckedChipId() == View.NO_ID) {
            chip.setChecked(true);
        }
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.btnPlus.setOnClickListener(v -> {
            quantity++;
            binding.tvQuantity.setText(String.valueOf(quantity));
        });

        binding.btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                binding.tvQuantity.setText(String.valueOf(quantity));
            }
        });

        binding.btnAction.setOnClickListener(v -> {
            String msg = actionType.equals("buy") ? "Tiến hành thanh toán " : "Đã thêm vào giỏ hàng ";
            Toast.makeText(getContext(), msg + quantity + " sản phẩm", Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
