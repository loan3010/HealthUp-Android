package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.healthup.databinding.BottomSheetProductOptionsBinding;
import com.example.models.Product;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProductOptionsBottomSheetFragment extends BottomSheetDialogFragment {
    private BottomSheetProductOptionsBinding binding;
    private Product product;
    private String actionType; // "add" or "buy"
    private int quantity = 1;
    private final Map<String, Product.ProductVariant> selectedByGroup = new LinkedHashMap<>();
    private final DecimalFormat df = new DecimalFormat("#,###đ");

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
        refreshPriceAndStock();

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            com.example.healthup.util.ImageLoadHelper.loadInto(
                    binding.ivProductSmall, product.getImages().get(0));
        }

        setupDynamicChips();
        refreshPriceAndStock();
    }

    private void refreshPriceAndStock() {
        Product.ProductVariant resolved = product.resolveComboVariant(selectedByGroup);
        double price = product.resolveUnitPrice(resolved);
        binding.tvPriceSmall.setText(df.format(price));

        int stock = resolved != null ? Math.max(0, resolved.getStock()) : product.getAvailableStock();
        boolean inStock = stock > 0;
        binding.tvStock.setText(getString(R.string.stock_prefix, stock));

        binding.btnAction.setEnabled(inStock);
        binding.btnAction.setAlpha(inStock ? 1f : 0.45f);
        if (!inStock) {
            binding.btnAction.setText(R.string.out_of_stock);
        } else {
            boolean buy = "buy".equals(actionType);
            binding.btnAction.setText(buy ? getString(R.string.buy_now) : getString(R.string.add_to_cart));
        }
    }

    private void setupDynamicChips() {
        selectedByGroup.clear();
        setupWeightChips();
        setupFlavorChips();
        setupPackagingChips();
    }

    private void setupWeightChips() {
        if (product.getWeights() != null && !product.getWeights().isEmpty()) {
            binding.tvWeightLabel.setVisibility(View.VISIBLE);
            binding.cgWeight.setVisibility(View.VISIBLE);
            binding.cgWeight.removeAllViews();
            for (Object w : product.getWeights()) {
                addChipToGroup(binding.cgWeight, "Khối lượng", Product.extractOptionLabel(w));
            }
        } else {
            binding.tvWeightLabel.setVisibility(View.GONE);
            binding.cgWeight.setVisibility(View.GONE);
        }
    }

    private void setupFlavorChips() {
        if (product.getFlavors() != null && !product.getFlavors().isEmpty()) {
            binding.tvFlavorLabel.setVisibility(View.VISIBLE);
            binding.cgFlavor.setVisibility(View.VISIBLE);
            binding.cgFlavor.removeAllViews();
            for (Object f : product.getFlavors()) {
                addChipToGroup(binding.cgFlavor, "Hương vị", Product.extractOptionLabel(f));
            }
        } else {
            binding.tvFlavorLabel.setVisibility(View.GONE);
            binding.cgFlavor.setVisibility(View.GONE);
        }
    }

    private void setupPackagingChips() {
        if (product.getPackagingTypes() != null && !product.getPackagingTypes().isEmpty()) {
            binding.tvPackagingLabel.setVisibility(View.VISIBLE);
            binding.cgPackaging.setVisibility(View.VISIBLE);
            binding.cgPackaging.removeAllViews();
            for (Object p : product.getPackagingTypes()) {
                addChipToGroup(binding.cgPackaging, "Loại đóng gói", Product.extractOptionLabel(p));
            }
        } else {
            binding.tvPackagingLabel.setVisibility(View.GONE);
            binding.cgPackaging.setVisibility(View.GONE);
        }
    }

    private void addChipToGroup(ChipGroup group, String groupName, String text) {
        Chip chip = new Chip(getContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setClickable(true);
        group.addView(chip);

        // Select the first chip by default if none selected
        if (group.getCheckedChipId() == View.NO_ID) {
            chip.setChecked(true);
            rememberSelection(groupName, text);
        }

        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                rememberSelection(groupName, text);
                refreshPriceAndStock();
            }
        });
    }

    private void rememberSelection(String groupName, String label) {
        Product.ProductVariant found = product.findVariantByName(label);
        if (found == null) {
            found = new Product.ProductVariant();
            found.setName(label);
        }
        selectedByGroup.put(groupName, found);
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
            if (!product.isInStock()) {
                Toast.makeText(getContext(), R.string.out_of_stock, Toast.LENGTH_SHORT).show();
                return;
            }
            String msg = "buy".equals(actionType) ? "Tiến hành thanh toán " : "Đã thêm vào giỏ hàng ";
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
