package com.example.healthup;








import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.example.healthup.R;
import com.example.healthup.ProductAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.GuestWishlistUiHelper;
import com.example.healthup.util.ReviewStatsHelper;
import com.example.models.Product;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;








public class ProductListFragment extends Fragment implements ProductAdapter.OnProductClickListener {








    private RecyclerView rvProducts;
    private ProductAdapter productAdapter;
    private List<Product> productList = new ArrayList<>();
    private EditText etSearch;
    private ExtendedFloatingActionButton fabFilter;
    private ChipGroup chipGroupCategories;
    private View layoutEmpty;
    private android.widget.ProgressBar progressBar;








    private List<String> categoryNames = new ArrayList<>();
    private String selectedCategory = "Tất cả";








    private String currentSort = "Phổ biến";
    private double minPrice = 0;
    private double maxPrice = 1000000;
    private float minRating = 0;








    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        initViews(view);








        if (getArguments() != null) {
            selectedCategory = getArguments().getString("category", "Tất cả");
        }








        setupRecyclerViews();
        fetchCategoriesFromFirestore();
        fetchProducts();
        return view;
    }








    private void initViews(View view) {
        rvProducts = view.findViewById(R.id.rv_products);
        etSearch = view.findViewById(R.id.et_search);
        fabFilter = view.findViewById(R.id.fab_filter);
        chipGroupCategories = view.findViewById(R.id.chip_group_categories);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        progressBar = view.findViewById(R.id.progressBar);








        if (layoutEmpty != null) {
            View btnClear = layoutEmpty.findViewById(R.id.btn_clear_filter);
            if (btnClear != null) btnClear.setOnClickListener(v -> resetFilters());
        }








        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });








        fabFilter.setOnClickListener(v -> showFilterBottomSheet());








        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLocal(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }








    private void setupRecyclerViews() {
        productAdapter = new ProductAdapter(productList, this);
        GuestWishlistUiHelper.applyTo(productAdapter);
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvProducts.setAdapter(productAdapter);
        rvProducts.setNestedScrollingEnabled(false);
    }








    private void fetchCategoriesFromFirestore() {
        categoryNames.clear();
        categoryNames.addAll(Arrays.asList("Tất cả", "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"));
        setupCategoryChips();


        FirestoreManager.getInstance().getFirestore().collection("categories").get().addOnSuccessListener(snapshots -> {
            if (!snapshots.isEmpty()) {
                // Có thể đồng bộ tên từ server ở đây nếu cần
            }
        });
    }








    private void setupCategoryChips() {
        chipGroupCategories.removeAllViews();
        for (String name : categoryNames) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroupCategories, false);
            chip.setText(name);
            chip.setCheckable(true);








            boolean isSelected = name.equals(selectedCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);








            chip.setOnClickListener(v -> {
                selectedCategory = name;
                refreshChipGroupUI();
                fetchProducts();
            });
            chipGroupCategories.addView(chip);
        }
    }








    private void refreshChipGroupUI() {
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCategories.getChildAt(i);
            boolean isSelected = chip.getText().toString().equals(selectedCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);
        }
    }








    private void updateChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(getResources().getColor(R.color.text_dark));
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(R.color.border_color);
        }
    }








    private void fetchProducts() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        rvProducts.setVisibility(View.GONE);


        Query query = FirestoreManager.getInstance().getFilteredProductsQuery(selectedCategory);








        query.get().addOnSuccessListener(snapshots -> {
            List<Product> result = FirestoreManager.getInstance()
                    .processProductSnapshots(snapshots, currentSort, minPrice, maxPrice, minRating);








            productList.clear();
            productList.addAll(result);
            productAdapter.updateData(new ArrayList<>(productList));
            applyWishlistState();
            enrichProductStats();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            updateEmptyState();
            // FIX (yêu cầu): hiển thị số lượng sản phẩm phù hợp với bộ lọc/sắp xếp hiện tại
            // ngay trên nút "Bộ lọc", cập nhật lại mỗi lần fetchProducts() chạy (cả lần đầu
            // vào trang lẫn sau khi bấm Áp dụng ở bottom sheet).
            updateFilterButtonLabel(productList.size());
        }).addOnFailureListener(e -> {
            Log.e("ProductList", "Error fetching products: " + e.getMessage());
            Toast.makeText(getContext(), "Lỗi tải sản phẩm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            updateEmptyState();
            updateFilterButtonLabel(0);
        });
    }








    // FIX (yêu cầu): cập nhật chữ trên nút "Bộ lọc" thành "Bộ lọc (N)" với N là số sản phẩm
    // đang khớp với danh mục/sắp xếp/khoảng giá/đánh giá hiện tại.
    private void updateFilterButtonLabel(int count) {
        if (fabFilter != null) {
            fabFilter.setText(String.format(Locale.getDefault(), "Bộ lọc (%d)", count));
        }
    }




    private void enrichProductStats() {
        ReviewStatsHelper.enrichProducts(productList, () -> {
            if (isAdded() && productAdapter != null) {
                productAdapter.notifyDataSetChanged();
            }
        });
    }

    // FIX (yêu cầu #4): xem giải thích chi tiết trong HomeFragment.applyWishlistToHomeLists().
    // Nguyên nhân giống hệt: applyFavoriteState() mutate product object đang được adapter giữ
    // tham chiếu, nên DiffUtil trong updateData() không phát hiện thay đổi -> đổi sang
    // notifyDataSetChanged() để chắc chắn RecyclerView vẽ lại đúng trạng thái trái tim.
    private void applyWishlistState() {
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            return;
        }
        WishlistManager.loadFavoriteIds(uid, ids -> {
            if (!isAdded()) {
                return;
            }
            WishlistManager.applyFavoriteState(productList, ids);
            productAdapter.notifyDataSetChanged();
        });
    }




    private void updateEmptyState() {
        if (productList.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            if (layoutEmpty != null) {
                layoutEmpty.setVisibility(View.VISIBLE);
            }
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        }
    }








    private void resetFilters() {
        selectedCategory = "Tất cả";
        currentSort = "Phổ biến";
        minPrice = 0;
        maxPrice = 1000000;
        minRating = 0;
        setupCategoryChips();
        fetchProducts();
    }








    private void filterLocal(String query) {
        List<Product> filtered = new ArrayList<>();
        for (Product p : productList) {
            if (p.getName().toLowerCase().contains(query.toLowerCase())) filtered.add(p);
        }
        productAdapter.updateData(filtered);
    }








    private void showFilterBottomSheet() {
        try {
            FilterBottomSheetFragment filterSheet = FilterBottomSheetFragment.newInstance(
                    selectedCategory, currentSort, minPrice, maxPrice, minRating
            );
            filterSheet.setFilterListener((category, sort, min, max, rating) -> {
                this.selectedCategory = category;
                this.currentSort = sort;
                this.minPrice = min;
                this.maxPrice = max;
                this.minRating = rating;
                setupCategoryChips();
                fetchProducts();
            });
            filterSheet.show(getChildFragmentManager(), "FilterBottomSheet");
        } catch (Exception e) {
            Toast.makeText(getContext(), "Lỗi mở bộ lọc", Toast.LENGTH_SHORT).show();
        }
    }








    @Override
    public void onProductClick(Product product) {
        if (product == null || product.getId() == null) return;
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }








    // FIX (yêu cầu #3): luôn hiển thị popup chọn số lượng/phân loại, bất kể có phân loại hay không.
    @Override
    public void onAddToCart(Product product) {
        showVariantSheet(product);
    }




    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity, selections) ->
                com.example.healthup.util.CartHelper.addToCart(requireContext(), product, variant, quantity, selections));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }








    @Override
    public void onResume() {
        super.onResume();
        GuestWishlistUiHelper.applyTo(productAdapter);
    }

    @Override
    public void onFavoriteClick(Product product) {
        WishlistManager.toggle(requireContext(), product, success -> {
            if (success && isAdded()) {
                productAdapter.notifyDataSetChanged();
            }
        });
    }
}