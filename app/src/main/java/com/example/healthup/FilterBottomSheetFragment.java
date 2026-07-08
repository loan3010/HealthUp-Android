package com.example.healthup;


import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;


import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class FilterBottomSheetFragment extends BottomSheetDialogFragment {


    public interface OnFilterAppliedListener {
        void onFilterApplied(String category, String sort, double minPrice, double maxPrice, float rating);
    }


    private static final float PRICE_MAX = 1000000f;
    private static final float PRICE_STEP = 5000f;


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


    private final List<String> categories = Arrays.asList(
            "Tất cả", "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"
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


            if (maxPrice > PRICE_MAX) maxPrice = PRICE_MAX;
            if (minPrice > PRICE_MAX) minPrice = PRICE_MAX;
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


    // FIX (yêu cầu: "mới click vào Bộ lọc thì không thấy nút Áp dụng đâu"):
    // BottomSheetDialogFragment mặc định mở ở trạng thái COLLAPSED (chỉ hiện 1 phần theo
    // peekHeight), người dùng phải kéo lên mới thấy hết nội dung kể cả khi layout đã ghim
    // nút Áp dụng ở đáy. Ép trạng thái EXPANDED ngay khi dialog xuất hiện để toàn bộ bottom
    // sheet (bao gồm nút Áp dụng) hiển thị đầy đủ ngay từ đầu, không cần kéo.
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
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
        priceSlider.setValues(0f, 0f);


        priceSlider.setValueFrom(0f);
        priceSlider.setValueTo(PRICE_MAX);
        priceSlider.setStepSize(PRICE_STEP);


        float safeMin = roundToStep(minPrice);
        float safeMax = roundToStep(maxPrice);
        if (safeMax <= safeMin) {
            safeMax = Math.min(PRICE_MAX, safeMin + PRICE_STEP);
        }
        minPrice = safeMin;
        maxPrice = safeMax;


        priceSlider.setValues(safeMin, safeMax);
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
                priceSlider.setValues(200000f, PRICE_MAX);
            }
            updateApplyButton(getView());
        };


        view.findViewById(R.id.btn_price_under_100).setOnClickListener(priceQuickAction);
        view.findViewById(R.id.btn_price_100_200).setOnClickListener(priceQuickAction);
        view.findViewById(R.id.btn_price_above_200).setOnClickListener(priceQuickAction);
    }


    private float roundToStep(double value) {
        float v = (float) value;
        if (v < 0) v = 0;
        if (v > PRICE_MAX) v = PRICE_MAX;
        return Math.round(v / PRICE_STEP) * PRICE_STEP;
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
                .getFilteredProductsQuery(selectedCategory)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (isAdded() && getView() != null) {
                        List<Product> filtered = FirestoreManager.getInstance()
                                .processProductSnapshots(snapshots, selectedSort, minPrice, maxPrice, minRating);
                        MaterialButton btnApply = view.findViewById(R.id.btn_apply);
                        if (btnApply != null) {
                            btnApply.setText(getString(R.string.filter_apply_count, filtered.size()));
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
        maxPrice = PRICE_MAX;
        minRating = 0;


        if (chipGroupSort != null) chipGroupSort.check(R.id.chip_popular);
        priceSlider.setValues(0f, PRICE_MAX);
        rgRating.clearCheck();
        categoryAdapter.setSelectedCategory(selectedCategory);
        updatePriceTexts();
        updateApplyButton(getView());
    }


    public void setFilterListener(OnFilterAppliedListener listener) {
        this.listener = listener;
    }


    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<String> items;
        private String selectedCategory;
        private final OnCategorySelectedListener listener;


        interface OnCategorySelectedListener {
            void onSelected(String category);
        }


        CategoryAdapter(List<String> items, String selected, OnCategorySelectedListener listener) {
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
            String categoryName = items.get(position);
            holder.tvName.setText(categoryName);
            holder.tvCount.setVisibility(View.GONE);
            holder.checkBox.setChecked(categoryName.equals(selectedCategory));


            View.OnClickListener clickListener = v -> {
                selectedCategory = categoryName;
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