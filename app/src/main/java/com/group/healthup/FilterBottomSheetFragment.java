package com.group.healthup;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.group.healthup.firebase.FirestoreManager;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnFilterAppliedListener {
        void onFilterApplied(String category, String sort, double minPrice, double maxPrice, float rating);
    }

    private OnFilterAppliedListener listener;
    private String selectedCategory, selectedSort;
    private double minPrice, maxPrice;
    private float minRating;

    private ChipGroup chipGroupSort;
    private RecyclerView rvCategories;
    private RangeSlider priceSlider;
    private TextView tvMinPrice, tvMaxPrice;
    private RadioGroup rgRating;
    private CategoryAdapter categoryAdapter;

    private final List<CategoryItem> categories = Arrays.asList(
            new CategoryItem("Hạt dinh dưỡng", 24),
            new CategoryItem("Granola", 18),
            new CategoryItem("Trái cây sấy", 32),
            new CategoryItem("Đồ ăn vặt", 45),
            new CategoryItem("Trà thảo mộc", 12),
            new CategoryItem("Combo", 8)
    );

    public static FilterBottomSheetFragment newInstance(String category, String sort, double min, double max, float rating) {
        FilterBottomSheetFragment fragment = new FilterBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("category", category);
        args.putString("sort", sort);
        args.putDouble("min", min);
        args.putDouble("max", max);
        args.putFloat("rating", rating);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            selectedCategory = getArguments().getString("category");
            selectedSort = getArguments().getString("sort");
            minPrice = getArguments().getDouble("min");
            maxPrice = getArguments().getDouble("max");
            minRating = getArguments().getFloat("rating");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View view) {
        chipGroupSort = view.findViewById(R.id.chip_group_sort);
        rvCategories = view.findViewById(R.id.rv_filter_categories);
        priceSlider = view.findViewById(R.id.price_slider);
        tvMinPrice = view.findViewById(R.id.tv_min_price);
        tvMaxPrice = view.findViewById(R.id.tv_max_price);
        rgRating = view.findViewById(R.id.rg_rating);

        setupSortChips();
        setupCategoryList();
        setupPriceSlider(view);
        setupRatingGroup();

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> resetFilters());
        
        MaterialButton btnApply = view.findViewById(R.id.btn_apply);
        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFilterApplied(selectedCategory, selectedSort, minPrice, maxPrice, minRating);
                }
                dismiss();
            });
        }

        updateApplyButton(view);
    }

    private void setupSortChips() {
        if (chipGroupSort == null) return;
        for (int i = 0; i < chipGroupSort.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupSort.getChildAt(i);
            if (chip.getText().toString().equalsIgnoreCase(selectedSort)) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSort = chip.getText().toString();
                    updateApplyButton(getView());
                }
            });
        }
    }

    private void setupCategoryList() {
        categoryAdapter = new CategoryAdapter(categories, selectedCategory, category -> {
            selectedCategory = category;
            updateApplyButton(getView());
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCategories.setAdapter(categoryAdapter);
    }

    private void setupPriceSlider(View view) {
        priceSlider.setValues((float) minPrice, (float) maxPrice);
        updatePriceTexts();

        priceSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            minPrice = values.get(0);
            maxPrice = values.get(1);
            updatePriceTexts();
            updateApplyButton(getView());
        });

        View.OnClickListener priceQuickAction = v -> {
            int id = v.getId();
            if (id == R.id.btn_price_under_100) {
                priceSlider.setValues(0f, 100000f);
            } else if (id == R.id.btn_price_100_200) {
                priceSlider.setValues(100000f, 200000f);
            } else if (id == R.id.btn_price_above_200) {
                priceSlider.setValues(200000f, 10000000f);
            }
            updateApplyButton(getView());
        };

        view.findViewById(R.id.btn_price_under_100).setOnClickListener(priceQuickAction);
        view.findViewById(R.id.btn_price_100_200).setOnClickListener(priceQuickAction);
        view.findViewById(R.id.btn_price_above_200).setOnClickListener(priceQuickAction);
    }

    private void updatePriceTexts() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvMinPrice.setText(String.format("%sđ", formatter.format(minPrice)));
        tvMaxPrice.setText(String.format("%sđ", formatter.format(maxPrice)));
    }

    private void setupRatingGroup() {
        if (minRating >= 5) rgRating.check(R.id.rb_5_stars);
        else if (minRating >= 4) rgRating.check(R.id.rb_4_stars);

        rgRating.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_5_stars) minRating = 5.0f;
            else if (checkedId == R.id.rb_4_stars) minRating = 4.0f;
            else minRating = 0;
            updateApplyButton(getView());
        });
    }

    private void updateApplyButton(View view) {
        if (view == null) return;
        FirestoreManager.getInstance()
                .getFilteredProducts(selectedCategory, selectedSort, minPrice, maxPrice, minRating)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (isAdded() && getView() != null) {
                        MaterialButton btnApply = view.findViewById(R.id.btn_apply);
                        if (btnApply != null) {
                            btnApply.setText(getString(R.string.filter_apply_count, snapshots.size()));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded() && getView() != null) {
                        Log.e("FilterBottomSheet", "Error counting results: " + e.getMessage());
                        MaterialButton btnApply = view.findViewById(R.id.btn_apply);
                        if (btnApply != null) btnApply.setText(getString(R.string.filter_apply));
                    }
                });
    }

    private void resetFilters() {
        selectedCategory = "Tất cả";
        selectedSort = "Phổ biến";
        minPrice = 0;
        maxPrice = 10000000;
        minRating = 0;
        
        if (chipGroupSort != null) chipGroupSort.check(R.id.chip_popular);
        priceSlider.setValues(0f, 10000000f);
        rgRating.clearCheck();
        categoryAdapter.setSelectedCategory(selectedCategory);
        updatePriceTexts();
        updateApplyButton(getView());
    }

    public void setFilterListener(OnFilterAppliedListener listener) {
        this.listener = listener;
    }

    private static class CategoryItem {
        String name;
        int count;
        CategoryItem(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<CategoryItem> items;
        private String selectedCategory;
        private final OnCategorySelectedListener listener;

        interface OnCategorySelectedListener {
            void onSelected(String category);
        }

        CategoryAdapter(List<CategoryItem> items, String selected, OnCategorySelectedListener listener) {
            this.items = items;
            this.selectedCategory = selected;
            this.listener = listener;
        }

        void setSelectedCategory(String category) {
            this.selectedCategory = category;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_filter_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvCount.setText(String.valueOf(item.count));
            holder.checkBox.setChecked(item.name.equals(selectedCategory));
            
            View.OnClickListener clickListener = v -> {
                selectedCategory = item.name;
                listener.onSelected(selectedCategory);
                notifyDataSetChanged();
            };
            holder.itemView.setOnClickListener(clickListener);
            holder.checkBox.setOnClickListener(clickListener);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView tvName, tvCount;
            ViewHolder(View v) {
                super(v);
                checkBox = v.findViewById(R.id.cb_category);
                tvName = v.findViewById(R.id.tv_category_name);
                tvCount = v.findViewById(R.id.tv_product_count);
            }
        }
    }
}
