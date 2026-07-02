package com.group.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.group.healthup.R;
import com.group.adapters.ProductAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Product;
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

    private List<String> categoryNames = Arrays.asList("Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo");

    // Filter settings
    private String currentCategory = "Hạt dinh dưỡng";
    private String currentSort = "Phổ biến";
    private double minPrice = 0;
    private double maxPrice = 1000000;
    private float minRating = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);
        initViews(view);
        setupRecyclerViews();
        setupCategoryChips();
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
        layoutEmpty.findViewById(R.id.btn_clear_filter).setOnClickListener(v -> {
            resetFilters();
            fetchProducts();
        });

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        fabFilter.setOnClickListener(v -> showFilterBottomSheet());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLocal(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerViews() {
        productAdapter = new ProductAdapter(productList, this);
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvProducts.setAdapter(productAdapter);
        rvProducts.setNestedScrollingEnabled(false);

        recommendationAdapter = new ProductAdapter(recommendationList, this);
        rvRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvRecommendations.setAdapter(recommendationAdapter);
        rvRecommendations.setNestedScrollingEnabled(false);
    }

    private void setupCategoryChips() {
        chipGroupCategories.removeAllViews();
        for (String name : categoryNames) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroupCategories, false);
            chip.setText(name);
            chip.setCheckable(true);
            if (name.equals(currentCategory)) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentCategory = name;
                    fetchProducts();
                }
            });
            chipGroupCategories.addView(chip);
        }
    }

    private void fetchProducts() {
        FirestoreManager.getInstance().getFilteredProducts(currentCategory, currentSort, minPrice, maxPrice, minRating)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    productList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            productList.add(product);
                        }
                    }
                    productAdapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Lỗi tải: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                });

        // Recommendations
        FirestoreManager.getInstance().getProductsCollection().limit(4)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recommendationList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            recommendationList.add(product);
                        }
                    }
                    recommendationAdapter.notifyDataSetChanged();
                });
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
        currentCategory = "Hạt dinh dưỡng";
        currentSort = "Phổ biến";
        minPrice = 0;
        maxPrice = 1000000;
        minRating = 0;
        setupCategoryChips();
    }

    private void filterLocal(String query) {
        List<Product> filtered = new ArrayList<>();
        for (Product p : productList) {
            if (p.getName().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(p);
            }
        }
        productAdapter.updateData(filtered);
    }

    private void showFilterBottomSheet() {
        FilterBottomSheetFragment filterSheet = FilterBottomSheetFragment.newInstance(
                currentCategory, currentSort, minPrice, maxPrice, minRating
        );
        filterSheet.setFilterListener((category, sort, min, max, rating) -> {
            this.currentCategory = category;
            this.currentSort = sort;
            this.minPrice = min;
            this.maxPrice = max;
            this.minRating = rating;
            
            // Update chip selection
            for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
                Chip chip = (Chip) chipGroupCategories.getChildAt(i);
                if (chip.getText().toString().equals(currentCategory)) {
                    chip.setChecked(true);
                }
            }
            
            fetchProducts();
        });
        filterSheet.show(getChildFragmentManager(), "FilterBottomSheet");
    }

    @Override
    public void onProductClick(Product product) {
        android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("product", product);
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
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) -> {
            performAddToCart(product, variant, quantity);
        });
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }

    private void performAddToCart(Product product, Product.ProductVariant variant, int quantity) {
        String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        String productId = product.getId();
        String variantId = (variant != null) ? variant.getId() : null;

        FirestoreManager.getInstance().getFirestore().collection("cart")
                .whereEqualTo("userId", userId)
                .whereEqualTo("productId", productId)
                .whereEqualTo("variantId", variantId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        long currentQty = doc.getLong("quantity");
                        doc.getReference().update("quantity", currentQty + quantity);
                    } else {
                        com.group.models.CartItem newItem = new com.group.models.CartItem(
                                productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                        }
                        FirestoreManager.getInstance().getFirestore().collection("cart")
                                .add(newItem);
                    }
                    Toast.makeText(getContext(), getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onFavoriteClick(Product product) {
        product.setFavorite(!product.isFavorite());
        productAdapter.notifyDataSetChanged();
    }
}
