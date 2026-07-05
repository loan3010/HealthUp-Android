package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.healthup.databinding.FragmentProductListBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.example.models.Category;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class ProductListFragment extends Fragment {
    private FragmentProductListBinding binding;
    private FirestoreManager firestoreManager;
    private ProductAdapter adapter;
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> displayList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProductListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firestoreManager = FirestoreManager.getInstance();

        setupRecyclerView();
        handleArguments();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        binding.btnFilter.setOnClickListener(v -> showFilterBottomSheet());

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = binding.etSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    searchProducts(query);
                }
                return true;
            }
            return false;
        });
    }

    private void showFilterBottomSheet() {
        FilterBottomSheetFragment filterSheet = new FilterBottomSheetFragment();
        filterSheet.setOnFilterApplyListener((categories, sortType, minPrice, maxPrice, minRating) -> {
            applyFilters(categories, sortType, minPrice, maxPrice, minRating);
        });
        filterSheet.show(getChildFragmentManager(), "FilterBottomSheet");
    }

    private void applyFilters(List<String> categories, String sortType, float minPrice, float maxPrice, float minRating) {
        List<Product> filtered = new ArrayList<>();
        for (Product p : allProducts) {
            // Support multi-category matching
            boolean matchCat = categories.isEmpty();
            if (!matchCat) {
                List<String> productCats = p.getCategories();
                for (String selCat : categories) {
                    if (productCats.contains(selCat)) {
                        matchCat = true;
                        break;
                    }
                }
            }
            
            boolean matchPrice = p.getPrice() >= minPrice && p.getPrice() <= maxPrice;
            boolean matchRating = p.getRating() >= minRating;
            
            if (matchCat && matchPrice && matchRating) {
                filtered.add(p);
            }
        }

        // Sorting
        if ("price_asc".equals(sortType)) {
            Collections.sort(filtered, (p1, p2) -> Double.compare(p1.getPrice(), p2.getPrice()));
        } else if ("price_desc".equals(sortType)) {
            Collections.sort(filtered, (p1, p2) -> Double.compare(p2.getPrice(), p1.getPrice()));
        } else if ("newest".equals(sortType)) {
            Collections.sort(filtered, (p1, p2) -> Boolean.compare(p2.isNew(), p1.isNew()));
        } else if ("popular".equals(sortType)) {
            Collections.sort(filtered, (p1, p2) -> Integer.compare(p2.getSold(), p1.getSold()));
        } else if ("favorite".equals(sortType)) {
            Collections.sort(filtered, (p1, p2) -> Boolean.compare(p2.isFavorite(), p1.isFavorite()));
        }

        displayList.clear();
        displayList.addAll(filtered);
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(displayList, false);
        binding.rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvProducts.setAdapter(adapter);
        
        loadCategories();
    }

    private void loadCategories() {
        String currentCat = "Tất cả";
        if (getArguments() != null && "category".equals(getArguments().getString("filter_type"))) {
            currentCat = getArguments().getString("filter_value");
        }
        
        final String selectedCat = currentCat;
        binding.chipGroupCategories.removeAllViews();
        
        // Use the fixed 6 HealthUp categories
        String[] healthUpCategories = {"Tất cả", "Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo"};
        
        for (String catName : healthUpCategories) {
            addCategoryChip(catName, selectedCat.equals(catName));
        }
    }

    private void addCategoryChip(String categoryName, boolean isSelected) {
        Chip chip = new Chip(getContext());
        chip.setText(categoryName);
        chip.setCheckable(true);
        chip.setChecked(isSelected);
        chip.setClickable(true);
        
        // Thiết kế màu sắc theo yêu cầu: màu xanh HealthUp khi được chọn
        chip.setChipBackgroundColorResource(isSelected ? R.color.primary_default : R.color.neutral_light_grey);
        chip.setTextColor(getResources().getColor(isSelected ? R.color.neutral_white : R.color.neutral_black));

        chip.setOnClickListener(v -> {
            // Cập nhật lại giao diện các chip khác
            for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
                Chip c = (Chip) binding.chipGroupCategories.getChildAt(i);
                boolean isCurrent = c.getText().toString().equals(categoryName);
                c.setChipBackgroundColorResource(isCurrent ? R.color.primary_default : R.color.neutral_light_grey);
                c.setTextColor(getResources().getColor(isCurrent ? R.color.neutral_white : R.color.neutral_black));
            }

            if (categoryName.equals("Tất cả")) {
                fetchAllProducts();
            } else {
                fetchByCategory(categoryName);
            }
        });
        
        binding.chipGroupCategories.addView(chip);
    }

    private void handleArguments() {
        String type = "all";
        String value = "Danh mục";

        if (getArguments() != null) {
            type = getArguments().getString("filter_type", "all");
            value = getArguments().getString("filter_value", "Danh mục");
        }

        binding.tvTitle.setText(value);

        if ("category".equals(type)) {
            fetchByCategory(value);
        } else if ("search".equals(type)) {
            searchProducts(value);
        } else {
            fetchAllProducts();
        }
    }

    private void fetchAllProducts() {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getNewProducts(100, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                allProducts.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        Product p = doc.toObject(Product.class);
                        p.setId(doc.getId());
                        allProducts.add(p);
                    } catch (Exception e) {
                        android.util.Log.e("ProductListFragment", "Lỗi nạp sản phẩm: " + doc.getId(), e);
                    }
                }
                syncWishlistAndDisplay();
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void fetchByCategory(String categoryName) {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getProductsByCategory(categoryName, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                allProducts.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        Product p = doc.toObject(Product.class);
                        p.setId(doc.getId());
                        allProducts.add(p);
                    } catch (Exception e) {
                        android.util.Log.e("ProductListFragment", "Lỗi nạp sản phẩm: " + doc.getId(), e);
                    }
                }
                syncWishlistAndDisplay();
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void syncWishlistAndDisplay() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            binding.progressBar.setVisibility(View.GONE);
            displayList.clear();
            displayList.addAll(allProducts);
            adapter.notifyDataSetChanged();
            binding.tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        firestoreManager.getWishlist(wishTask -> {
            binding.progressBar.setVisibility(View.GONE);
            if (wishTask.isSuccessful() && wishTask.getResult() != null) {
                List<String> wishlistIds = new ArrayList<>();
                for (QueryDocumentSnapshot doc : wishTask.getResult()) {
                    wishlistIds.add(doc.getString("productId"));
                }
                for (Product p : allProducts) {
                    p.setFavorite(wishlistIds.contains(p.getId()));
                }
            }
            displayList.clear();
            displayList.addAll(allProducts);
            adapter.notifyDataSetChanged();
            binding.tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private String removeAccents(String s) {
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").toLowerCase().replace('đ', 'd').replace('Đ', 'd');
    }

    private void searchProducts(String query) {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getNewProducts(100, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                allProducts.clear();
                String normalizedQuery = removeAccents(query);
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        Product p = doc.toObject(Product.class);
                        p.setId(doc.getId());
                        if (p.getName() != null) {
                            String normalizedName = removeAccents(p.getName());
                            if (normalizedName.contains(normalizedQuery)) {
                                allProducts.add(p);
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ProductListFragment", "Lỗi tìm kiếm sản phẩm: " + doc.getId(), e);
                    }
                }
                syncWishlistAndDisplay();
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
