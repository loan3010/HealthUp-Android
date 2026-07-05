package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.healthup.databinding.BottomSheetFilterBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Category;
import com.example.models.Product;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {
    private BottomSheetFilterBinding binding;
    private OnFilterApplyListener listener;
    private final List<String> selectedCategories = new ArrayList<>();
    private List<Product> allProductsForCounting = new ArrayList<>();

    public interface OnFilterApplyListener {
        void onApply(List<String> categories, String sortType, float minPrice, float maxPrice, float minRating);
    }

    public void setOnFilterApplyListener(OnFilterApplyListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetFilterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.sliderPrice.setStepSize(5000f);
        loadCategories();

        binding.btnApply.setOnClickListener(v -> {
            String sortType = getSelectedSortType();
            float minRating = getSelectedMinRating();

            updateSliderFromInputs();
            List<Float> values = binding.sliderPrice.getValues();
            if (listener != null) {
                listener.onApply(selectedCategories, sortType, values.get(0), values.get(1), minRating);
            }
            dismiss();
        });

        binding.btnPriceUnder100.setOnClickListener(v -> {
            binding.sliderPrice.setValues(0f, 100000f);
            syncInputsFromSlider();
            updateLiveCount();
        });
        binding.btnPrice100To200.setOnClickListener(v -> {
            binding.sliderPrice.setValues(100000f, 200000f);
            syncInputsFromSlider();
            updateLiveCount();
        });
        binding.btnPriceOver200.setOnClickListener(v -> {
            binding.sliderPrice.setValues(200000f, 1000000f);
            syncInputsFromSlider();
            updateLiveCount();
        });

        binding.btnResetAll.setOnClickListener(v -> {
            binding.cgSort.clearCheck();
            binding.cgRating.clearCheck();
            binding.sliderPrice.setValues(0f, 1000000f);
            binding.etMinPrice.setText("0");
            binding.etMaxPrice.setText("1.000.000");
            
            for (int i = 0; i < binding.containerCategories.getChildCount(); i++) {
                View child = binding.containerCategories.getChildAt(i);
                if (child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        View inner = vg.getChildAt(j);
                        if (inner instanceof CheckBox) {
                            ((CheckBox) inner).setChecked(false);
                        }
                    }
                }
            }
            selectedCategories.clear();
            updateLiveCount();
        });

        binding.tvReset.setOnClickListener(v -> binding.btnResetAll.performClick());

        binding.sliderPrice.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                syncInputsFromSlider();
                updateLiveCount();
            }
        });

        binding.cgSort.setOnCheckedStateChangeListener((group, checkedIds) -> updateLiveCount());
        binding.cgRating.setOnCheckedStateChangeListener((group, checkedIds) -> updateLiveCount());

        View.OnFocusChangeListener priceFocusListener = (v, hasFocus) -> {
            if (!hasFocus) {
                updateSliderFromInputs();
                updateLiveCount();
            }
        };

        binding.etMinPrice.setOnFocusChangeListener(priceFocusListener);
        binding.etMaxPrice.setOnFocusChangeListener(priceFocusListener);
    }

    private String getSelectedSortType() {
        if (binding.chipNewest.isChecked()) return "newest";
        if (binding.chipPriceLowHigh.isChecked()) return "price_asc";
        if (binding.chipPriceHighLow.isChecked()) return "price_desc";
        if (binding.chipFavorite.isChecked()) return "favorite";
        return "popular";
    }

    private float getSelectedMinRating() {
        if (binding.chipRating5.isChecked()) return 5.0f;
        if (binding.chipRating4.isChecked()) return 4.0f;
        return 0f;
    }

    private void updateLiveCount() {
        if (allProductsForCounting == null || allProductsForCounting.isEmpty()) {
            return;
        }

        float minRating = getSelectedMinRating();
        List<Float> prices = binding.sliderPrice.getValues();
        float minP = prices.get(0);
        float maxP = prices.get(1);

        int count = 0;
        for (Product p : allProductsForCounting) {
            // Hỗ trợ đa danh mục
            boolean matchCat = selectedCategories.isEmpty();
            if (!matchCat) {
                List<String> productCats = p.getCategories();
                for (String selCat : selectedCategories) {
                    if (productCats.contains(selCat)) {
                        matchCat = true;
                        break;
                    }
                }
            }

            boolean matchPrice = p.getPrice() >= minP && p.getPrice() <= maxP;
            boolean matchRating = p.getRating() >= minRating;

            if (matchCat && matchPrice && matchRating) {
                count++;
            }
        }
        updateApplyButtonCount(count);
    }

    private void syncInputsFromSlider() {
        List<Float> values = binding.sliderPrice.getValues();
        binding.etMinPrice.setText(String.format("%,.0f", values.get(0)));
        binding.etMaxPrice.setText(String.format("%,.0f", values.get(1)));
    }

    private void updateSliderFromInputs() {
        try {
            String minStr = binding.etMinPrice.getText().toString().replaceAll("[^0-9]", "");
            String maxStr = binding.etMaxPrice.getText().toString().replaceAll("[^0-9]", "");

            float min = minStr.isEmpty() ? 0 : Float.parseFloat(minStr);
            float max = maxStr.isEmpty() ? 1000000 : Float.parseFloat(maxStr);

            if (min < 0) min = 0;
            if (max > 1000000) max = 1000000;
            if (min > max) {
                float temp = min;
                min = max;
                max = temp;
            }

            binding.sliderPrice.setValues(min, max);
            syncInputsFromSlider();
        } catch (Exception e) {
            syncInputsFromSlider();
        }
    }

    private void loadCategories() {
        String[] defaultCats = {"Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"};
        List<Category> categoryList = new ArrayList<>();
        for (int i = 0; i < defaultCats.length; i++) {
            categoryList.add(new Category(String.valueOf(i), defaultCats[i], ""));
        }

        FirestoreManager.getInstance().getNewProducts(500, prodTask -> {
            Map<String, Integer> counts = new HashMap<>();
            allProductsForCounting.clear();
            if (prodTask.isSuccessful() && prodTask.getResult() != null) {
                for (QueryDocumentSnapshot doc : prodTask.getResult()) {
                    try {
                        Product p = doc.toObject(Product.class);
                        p.setId(doc.getId());
                        allProductsForCounting.add(p);
                        
                        List<String> pCats = p.getCategories();
                        for (String c : pCats) {
                            counts.put(c, counts.getOrDefault(c, 0) + 1);
                        }
                    } catch (Exception e) {}
                }
            }
            
            updateLiveCount();

            if (binding != null && binding.containerCategories != null) {
                binding.containerCategories.removeAllViews();
                for (Category cat : categoryList) {
                    android.widget.RelativeLayout rl = new android.widget.RelativeLayout(getContext());
                    rl.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    rl.setPadding(0, 8, 0, 8);

                    CheckBox cb = new CheckBox(getContext());
                    cb.setText(cat.getName());
                    cb.setId(View.generateViewId());
                    android.widget.RelativeLayout.LayoutParams cbParams = new android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cbParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                    cb.setLayoutParams(cbParams);

                    TextView tvCount = new TextView(getContext());
                    int count = counts.getOrDefault(cat.getName(), 0);
                    tvCount.setText(String.valueOf(count));
                    if (isAdded()) {
                        tvCount.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
                    }
                    android.widget.RelativeLayout.LayoutParams tvParams = new android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tvParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
                    tvParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                    tvCount.setLayoutParams(tvParams);

                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            if (!selectedCategories.contains(cat.getName()))
                                selectedCategories.add(cat.getName());
                        } else {
                            selectedCategories.remove(cat.getName());
                        }
                        updateLiveCount();
                    });

                    rl.addView(cb);
                    rl.addView(tvCount);
                    binding.containerCategories.addView(rl);
                }
            }
        });
    }

    private void updateApplyButtonCount(int count) {
        if (binding != null) {
            binding.btnApply.setText("Áp dụng (" + count + ")");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
