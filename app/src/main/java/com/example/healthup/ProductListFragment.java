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
import com.example.models.Product;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


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
    private String selectedCategory = "Tất cả";


    private String currentSort = "Phổ biến";
    private double minPrice = 0;
    // Giá tối đa mặc định: 1.000.000đ (trước là 10.000.000đ)
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
        // FIX: RecyclerView wrap_content lồng trong NestedScrollView không tự đo lại
        // chiều cao khi dữ liệu Firestore load xong (bất đồng bộ, sau khi layout đã đo lần đầu).
        // Đây là nguyên nhân khiến sản phẩm không hiển thị cho tới khi người dùng đổi tab
        // (thao tác đổi tab vô tình kích hoạt lại việc đo layout của cây view cha).
        rvProducts.setNestedScrollingEnabled(false);


        recommendationAdapter = new ProductAdapter(recommendationList, this);
        rvRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvRecommendations.setAdapter(recommendationAdapter);
        rvRecommendations.setNestedScrollingEnabled(false);
    }


    private void fetchCategoriesFromFirestore() {
        // Cố định 7 danh mục theo yêu cầu của HealthUp để đảm bảo tính nhất quán với thương hiệu
        categoryNames.clear();
        categoryNames.addAll(Arrays.asList("Tất cả", "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"));
        setupCategoryChips();
        
        // Vẫn giữ fetch từ Firestore nếu sau này muốn đồng bộ icon hoặc metadata, 
        // nhưng hiện tại ưu tiên hiển thị đúng 7 tab yêu cầu.
        FirestoreManager.getInstance().getFirestore().collection("categories").get().addOnSuccessListener(snapshots -> {
            if (!snapshots.isEmpty()) {
                // Nếu muốn đồng bộ tên từ server thì xử lý ở đây, 
                // nhưng hiện tại đã fix cứng list theo yêu cầu user.
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
        // Chỉ lọc category trên Firestore (không cần index).
        // Lọc giá/rating + sắp xếp được xử lý ở processProductSnapshots() bằng Java.
        Query query = FirestoreManager.getInstance().getFilteredProductsQuery(selectedCategory);


        query.get().addOnSuccessListener(snapshots -> {
            List<Product> result = FirestoreManager.getInstance()
                    .processProductSnapshots(snapshots, currentSort, minPrice, maxPrice, minRating);


            productList.clear();
            productList.addAll(result);
            productAdapter.updateData(new ArrayList<>(productList));
            applyWishlistState();
            updateEmptyState();
        }).addOnFailureListener(e -> {
            Log.e("ProductList", "Error fetching products: " + e.getMessage());
            Toast.makeText(getContext(), "Lỗi tải sản phẩm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            updateEmptyState();
        });


        // Recommendations
        FirestoreManager.getInstance().getProductsCollection().limit(4).get().addOnSuccessListener(snapshots -> {
            recommendationList.clear();
            for (DocumentSnapshot doc : snapshots) {
                Product p = doc.toObject(Product.class);
                if (p != null) {
                    p.setId(doc.getId());
                    recommendationList.add(p);
                }
            }
            recommendationAdapter.updateData(new ArrayList<>(recommendationList));
        });
    }


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
            productAdapter.updateData(new ArrayList<>(productList));
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


    @Override
    public void onAddToCart(Product product) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), getString(R.string.login_required_cart), Toast.LENGTH_SHORT).show();
            return;
        }
        if (product.isHasVariants()) {
            showVariantSheet(product);
        } else {
            performAddToCart(product, null, 1);
        }
    }

    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) ->
                performAddToCart(product, variant, quantity));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }

    private void performAddToCart(Product product, Product.ProductVariant variant, int quantity) {
        String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        String productId = product.getId();
        String variantId = (variant != null) ? variant.getId() : null;

        com.google.firebase.firestore.CollectionReference cartRef =
                FirestoreManager.getInstance().getFirestore()
                        .collection("users").document(userId).collection("cart");

        cartRef.whereEqualTo("productId", productId)
                .whereEqualTo("variantId", variantId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Long currentQtyLong = doc.getLong("quantity");
                        long currentQty = (currentQtyLong != null) ? currentQtyLong : 0;
                        doc.getReference().update("quantity", currentQty + quantity);
                    } else {
                        com.example.models.CartItem newItem = new com.example.models.CartItem(
                                productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                            newItem.setOriginalPrice(variant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                            newItem.setOriginalPrice(product.getOriginalPrice());
                        }
                        cartRef.add(newItem);
                    }
                    Toast.makeText(getContext(), getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
                });
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
