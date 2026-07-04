package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.healthup.databinding.FragmentHomeBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import com.example.models.Category;
import com.example.models.Product;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private FirestoreManager firestoreManager;
    
    private CategoryAdapter categoryAdapter;
    private ProductAdapter flashSaleAdapter;
    private ProductAdapter newProductAdapter;
    private BlogAdapter blogAdapter;
    private ProductAdapter searchRealtimeAdapter;
    
    private final List<Category> categories = new ArrayList<>();
    private final List<Product> flashSaleProducts = new ArrayList<>();
    private final List<Product> newProducts = new ArrayList<>();
    private final List<Blog> blogs = new ArrayList<>();
    private final List<Product> allProductsForSearch = new ArrayList<>();
    private final List<Product> searchResults = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firestoreManager = FirestoreManager.getInstance();

        setupRecyclerViews();
        setupSearch();
        loadBanner();
        loadData();
    }

    private void loadBanner() {
        Glide.with(this)
                .load("file:///android_asset/images/banners/banner01.jpg")
                .into(binding.ivBanner);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() > 0) {
                    binding.mainContent.setVisibility(View.GONE);
                    binding.rvRealtimeSearch.setVisibility(View.VISIBLE);
                    filterProductsLocally(query);
                } else {
                    binding.mainContent.setVisibility(View.VISIBLE);
                    binding.rvRealtimeSearch.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.ivSearchIcon.setOnClickListener(v -> performSearch());

        binding.chipGranola.setOnClickListener(v -> openProductList("category", "Granola"));
        binding.chipHatDieu.setOnClickListener(v -> openProductList("category", "Hạt điều"));
        binding.chipRongBien.setOnClickListener(v -> openProductList("category", "Snack rong biển"));
        binding.chipYenMach.setOnClickListener(v -> openProductList("category", "Yến mạch"));

        binding.tvViewAllNew.setOnClickListener(v -> openProductList("all", "Sản phẩm mới nhất"));
    }

    private void performSearch() {
        String query = binding.etSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            openProductList("search", query);
        } else {
            Toast.makeText(getContext(), "Vui lòng nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterProductsLocally(String query) {
        String lowerQuery = query.toLowerCase();
        searchResults.clear();
        for (Product p : allProductsForSearch) {
            if (p.getName() != null && p.getName().toLowerCase().contains(lowerQuery)) {
                searchResults.add(p);
            }
        }
        searchRealtimeAdapter.notifyDataSetChanged();
    }

    private void openProductList(String type, String value) {
        ProductListFragment fragment = new ProductListFragment();
        Bundle args = new Bundle();
        args.putString("filter_type", type);
        args.putString("filter_value", value);
        fragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void setupRecyclerViews() {
        // Categories horizontal
        categoryAdapter = new CategoryAdapter(categories, category -> {
            openProductList("category", category.getName());
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvCategories.setAdapter(categoryAdapter);

        // Flash Sale horizontal
        flashSaleAdapter = new ProductAdapter(flashSaleProducts, true);
        binding.rvFlashSale.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvFlashSale.setAdapter(flashSaleAdapter);

        // New Products grid 2 columns
        newProductAdapter = new ProductAdapter(newProducts, false);
        binding.rvNewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvNewProducts.setAdapter(newProductAdapter);

        // Blogs horizontal
        blogAdapter = new BlogAdapter(blogs);
        binding.rvBlogs.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvBlogs.setAdapter(blogAdapter);

        // Realtime Search Grid
        searchRealtimeAdapter = new ProductAdapter(searchResults, false);
        binding.rvRealtimeSearch.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvRealtimeSearch.setAdapter(searchRealtimeAdapter);
    }

    private void loadData() {
        Log.d("HomeFragment", "Starting to load data from Firestore...");
        
        // Fetch Categories
        firestoreManager.getCategories(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Log.d("HomeFragment", "Categories fetched: " + task.getResult().size());
                categories.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Log.d("HomeFragment", "Category Raw Data: " + document.getData());
                    Category cat = document.toObject(Category.class);
                    cat.setId(document.getId());
                    categories.add(cat);
                }
                categoryAdapter.notifyDataSetChanged();
            } else {
                Log.e("HomeFragment", "Error fetching categories", task.getException());
            }
        });

        // Fetch Flash Sale
        firestoreManager.getFlashSaleProducts(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Log.d("HomeFragment", "Flash sale products fetched: " + task.getResult().size());
                if (task.getResult().isEmpty()) {
                    Log.d("HomeFragment", "Flash sale is empty. Check if collection 'products' exists and has 'isFlashSale: true'");
                }
                flashSaleProducts.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Log.d("HomeFragment", "Product Raw Data: " + document.getData());
                    Product prod = document.toObject(Product.class);
                    prod.setId(document.getId());
                    flashSaleProducts.add(prod);
                }
                flashSaleAdapter.notifyDataSetChanged();
            } else {
                Log.e("HomeFragment", "Error fetching flash sale", task.getException());
            }
        });

        // Fetch New Products
        firestoreManager.getNewProducts(100, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Log.d("HomeFragment", "New products fetched: " + task.getResult().size());
                
                List<Product> allFetchedProducts = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Product prod = document.toObject(Product.class);
                    prod.setId(document.getId());
                    allFetchedProducts.add(prod);
                }

                // Lưu toàn bộ vào danh sách tìm kiếm
                allProductsForSearch.clear();
                allProductsForSearch.addAll(allFetchedProducts);

                // Lấy ngẫu nhiên tầm 10 sản phẩm để hiện ra trang Home
                newProducts.clear();
                if (!allFetchedProducts.isEmpty()) {
                    Collections.shuffle(allFetchedProducts);
                    int limit = Math.min(allFetchedProducts.size(), 10);
                    newProducts.addAll(allFetchedProducts.subList(0, limit));
                }

                newProductAdapter.notifyDataSetChanged();
            } else {
                Log.e("HomeFragment", "Error fetching new products", task.getException());
            }
        });

        // Fetch Blogs
        firestoreManager.getBlogs(5, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Log.d("HomeFragment", "Blogs fetched: " + task.getResult().size());
                blogs.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Log.d("HomeFragment", "Blog Raw Data: " + document.getData());
                    Blog blog = document.toObject(Blog.class);
                    blog.setId(document.getId());
                    blogs.add(blog);
                }
                blogAdapter.notifyDataSetChanged();
            } else {
                Log.e("HomeFragment", "Error fetching blogs", task.getException());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
