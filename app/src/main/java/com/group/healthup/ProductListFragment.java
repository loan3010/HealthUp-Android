package com.group.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.group.healthup.R;
import com.group.adapters.ProductAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Product;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProductListFragment extends Fragment implements ProductAdapter.OnProductClickListener {

    private RecyclerView rvProducts, rvRecommendations;
    private ProductAdapter productAdapter, recommendationAdapter;
    private List<Product> productList = new ArrayList<>();
    private List<Product> recommendationList = new ArrayList<>();
    private EditText etSearch;
    private ExtendedFloatingActionButton fabFilter;
    private ChipGroup chipGroupCategories;
    private View layoutEmpty;

    private List<String> categoryNames = new ArrayList<>();
    private Set<String> selectedCategories = new HashSet<>();

    private String currentSort = "Phổ biến";
    private double minPrice = 0;
    private double maxPrice = 10000000;
    private float minRating = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        initViews(view);
        
        String startCat = "Tất cả";
        if (getArguments() != null) {
            startCat = getArguments().getString("category", "Tất cả");
        }
        
        selectedCategories.clear();
        selectedCategories.add(startCat);

        setupRecyclerViews();
        fetchCategoriesFromFirestore();
        fetchProducts();
        return view;
    }

    private void initViews(View view) {
        rvProducts = view.findViewById(R.id.rv_products);
        rvRecommendations = view.findViewById(R.id.rv_recommendations);
        etSearch = view.findViewById(R.id.et_search);
        fabFilter = view.findViewById(R.id.fab_filter);
        chipGroupCategories = view.findViewById(R.id.chip_group_categories);
        layoutEmpty = view.findViewById(R.id.layout_empty);

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
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvProducts.setAdapter(productAdapter);

        recommendationAdapter = new ProductAdapter(recommendationList, this);
        rvRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvRecommendations.setAdapter(recommendationAdapter);
    }

    private void fetchCategoriesFromFirestore() {
        categoryNames.clear();
        categoryNames.add("Tất cả");
        categoryNames.addAll(Arrays.asList("Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"));
        setupCategoryChips();
    }

    private void setupCategoryChips() {
        chipGroupCategories.removeAllViews();
        for (String name : categoryNames) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroupCategories, false);
            chip.setText(name);
            chip.setCheckable(true);
            
            boolean isSelected = selectedCategories.contains(name);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);

            chip.setOnClickListener(v -> {
                if (name.equals("Tất cả")) {
                    selectedCategories.clear();
                    selectedCategories.add("Tất cả");
                } else {
                    selectedCategories.remove("Tất cả");
                    if (selectedCategories.contains(name)) {
                        selectedCategories.remove(name);
                    } else {
                        selectedCategories.add(name);
                    }
                    if (selectedCategories.isEmpty()) selectedCategories.add("Tất cả");
                }
                refreshChipGroupUI();
                fetchProducts();
            });
            chipGroupCategories.addView(chip);
        }
    }

    private void refreshChipGroupUI() {
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCategories.getChildAt(i);
            boolean isSelected = selectedCategories.contains(chip.getText().toString());
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
        Query query;
        // Kiểm tra xem có đang ở trạng thái mặc định (Tất cả & Không lọc dải) hay không
        boolean isNoFilter = minPrice <= 0 && maxPrice >= 10000000 && minRating <= 0;

        if (selectedCategories.contains("Tất cả")) {
            if (isNoFilter && currentSort.equals("Phổ biến")) {
                // Trường hợp mặc định: Hiện toàn bộ sản phẩm (An toàn nhất, không cần index)
                query = FirestoreManager.getInstance().getProductsCollection();
            } else {
                // Sử dụng hàm lọc tập trung trong FirestoreManager
                query = FirestoreManager.getInstance().getFilteredProducts("Tất cả", currentSort, minPrice, maxPrice, minRating);
            }
        } else {
            // Lọc theo nhiều danh mục cụ thể (Sử dụng whereIn)
            query = FirestoreManager.getInstance().getProductsCollection()
                    .whereIn("cat", new ArrayList<>(selectedCategories));
            
            // Sắp xếp bổ sung dựa trên lựa chọn người dùng
            if (currentSort.equals("Giá Thấp-Cao")) query = query.orderBy("price", Query.Direction.ASCENDING);
            else if (currentSort.equals("Giá Cao-Thấp")) query = query.orderBy("price", Query.Direction.DESCENDING);
            else if (currentSort.equals("Mới nhất")) query = query.orderBy("createdAt", Query.Direction.DESCENDING);
        }

        query.get().addOnSuccessListener(snapshots -> {
            productList.clear();
            for (DocumentSnapshot doc : snapshots) {
                try {
                    Product p = doc.toObject(Product.class);
                    if (p != null) {
                        p.setId(doc.getId());
                        productList.add(p);
                    }
                } catch (Exception e) {
                    Log.e("ProductList", "Error parsing product: " + e.getMessage());
                }
            }
            productAdapter.updateData(new ArrayList<>(productList));
            updateEmptyState();
        }).addOnFailureListener(e -> {
            Log.e("ProductList", "Error fetching products: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("FAILED_PRECONDITION")) {
                Toast.makeText(getContext(), "Cần tạo index cho Firestore để sử dụng bộ lọc này.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Lỗi tải sản phẩm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            updateEmptyState();
        });

        // Tải phần Gợi ý (Recommendations) - Tối đa 4 sản phẩm
        FirestoreManager.getInstance().getProductsCollection().limit(4).get().addOnSuccessListener(snapshots -> {
            recommendationList.clear();
            for (DocumentSnapshot doc : snapshots) {
                try {
                    Product p = doc.toObject(Product.class);
                    if (p != null) {
                        p.setId(doc.getId());
                        recommendationList.add(p);
                    }
                } catch (Exception e) {
                    Log.e("ProductList", "Error parsing recommendation: " + e.getMessage());
                }
            }
            recommendationAdapter.updateData(new ArrayList<>(recommendationList));
        }).addOnFailureListener(e -> Log.e("ProductList", "Error fetching recommendations: " + e.getMessage()));
    }

    private void updateEmptyState() {
        if (productList.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void resetFilters() {
        selectedCategories.clear();
        selectedCategories.add("Tất cả");
        currentSort = "Phổ biến";
        minPrice = 0;
        maxPrice = 10000000;
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
                    selectedCategories.contains("Tất cả") ? "Tất cả" : selectedCategories.iterator().next(),
                    currentSort, minPrice, maxPrice, minRating
            );
            filterSheet.setFilterListener((category, sort, min, max, rating) -> {
                selectedCategories.clear();
                selectedCategories.add(category);
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
        // CHỈ truyền productId để tránh lỗi quá tải Intent gây văng app
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }

    @Override public void onAddToCart(Product product) { /* Logic đã ổn định */ }
    @Override
    public void onFavoriteClick(Product product) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để yêu thích", Toast.LENGTH_SHORT).show();
            return;
        }

        if (product.getId() == null) return;

        boolean oldState = product.isFavorite();
        boolean newState = !oldState;
        
        // 1. Cập nhật UI ngay lập tức cho cả 2 adapter (đề phòng sản phẩm nằm trong cả 2 danh sách)
        product.setFavorite(newState);
        productAdapter.notifyDataSetChanged();
        if (recommendationAdapter != null) {
            recommendationAdapter.notifyDataSetChanged();
        }

        // 2. Cập nhật Firestore bằng Map duy nhất trường "favorite"
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("favorite", newState);

        FirestoreManager.getInstance().getProductsCollection()
                .document(product.getId())
                .update(updates)
                .addOnFailureListener(e -> {
                    // 3. Rollback nếu lỗi server
                    product.setFavorite(oldState);
                    productAdapter.notifyDataSetChanged();
                    if (recommendationAdapter != null) {
                        recommendationAdapter.notifyDataSetChanged();
                    }
                    
                    if (e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED")) {
                        Toast.makeText(getContext(), "Lỗi quyền: Firestore Rules chặn lệnh update.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
