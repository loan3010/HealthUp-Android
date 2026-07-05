package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.healthup.databinding.BottomSheetFilterBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Category;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {
    private BottomSheetFilterBinding binding;
    private OnFilterApplyListener listener;
    private final List<String> selectedCategories = new ArrayList<>();

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
        loadCategories();

        binding.btnApply.setOnClickListener(v -> {
            String sortType = "popular";
            if (binding.chipNewest.isChecked()) sortType = "newest";
            else if (binding.chipPriceLowHigh.isChecked()) sortType = "price_asc";
            else if (binding.chipPriceHighLow.isChecked()) sortType = "price_desc";

            float minRating = 0;
            if (binding.chipRating5.isChecked()) minRating = 5.0f;
            else if (binding.chipRating4Plus.isChecked()) minRating = 4.0f;
            else if (binding.chipRating3Plus.isChecked()) minRating = 3.0f;

            List<Float> values = binding.sliderPrice.getValues();
            if (listener != null) {
                listener.onApply(selectedCategories, sortType, values.get(0), values.get(1), minRating);
            }
            dismiss();
        });

        binding.btnResetAll.setOnClickListener(v -> {
            binding.cgSort.clearCheck();
            binding.cgRating.clearCheck();
            binding.sliderPrice.setValues(0f, 1000000f);
            for (int i = 0; i < binding.containerCategories.getChildCount(); i++) {
                View child = binding.containerCategories.getChildAt(i);
                if (child instanceof CheckBox) {
                    ((CheckBox) child).setChecked(false);
                }
            }
            selectedCategories.clear();
        });

        binding.tvReset.setOnClickListener(v -> binding.btnResetAll.performClick());

        binding.sliderPrice.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            binding.tvMinPrice.setText(String.format("%,.0fđ", values.get(0)));
            binding.tvMaxPrice.setText(String.format("%,.0fđ", values.get(1)));
        });
    }

    private void loadCategories() {
        FirestoreManager.getInstance().getCategories(catTask -> {
            List<Category> categoryList = new ArrayList<>();
            if (catTask.isSuccessful() && catTask.getResult() != null && !catTask.getResult().isEmpty()) {
                for (QueryDocumentSnapshot doc : catTask.getResult()) {
                    categoryList.add(doc.toObject(Category.class));
                }
            } else {
                // Fallback: 6 default categories if Firestore is empty
                String[] defaultCats = {"Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"};
                for (int i = 0; i < defaultCats.length; i++) {
                    categoryList.add(new Category(String.valueOf(i), defaultCats[i], ""));
                }
            }

            // Fetch products to count them by category
            FirestoreManager.getInstance().getNewProducts(500, prodTask -> {
                Map<String, Integer> counts = new HashMap<>();
                if (prodTask.isSuccessful() && prodTask.getResult() != null) {
                    for (QueryDocumentSnapshot doc : prodTask.getResult()) {
                        String cat = doc.getString("cat");
                        if (cat != null) {
                            counts.put(cat, counts.getOrDefault(cat, 0) + 1);
                        }
                    }
                }

                if (binding != null && binding.containerCategories != null) {
                    binding.containerCategories.removeAllViews();
                    for (Category cat : categoryList) {
                        CheckBox cb = new CheckBox(getContext());
                        int count = counts.getOrDefault(cat.getName(), 0);
                        cb.setText(cat.getName() + " (" + count + ")");
                        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                if (!selectedCategories.contains(cat.getName()))
                                    selectedCategories.add(cat.getName());
                            } else {
                                selectedCategories.remove(cat.getName());
                            }
                        });
                        binding.containerCategories.addView(cb);
                    }
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
