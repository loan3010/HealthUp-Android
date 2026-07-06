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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.ProductAdapter;
import com.example.healthup.BlogAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import com.example.models.Category;
import com.example.models.Product;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryClickListener, BlogAdapter.OnBlogClickListener {

    private RecyclerView rvNewProducts, rvCategories, rvFlashSale, rvBlogs;
    private ProductAdapter newProductAdapter, flashSaleAdapter;
    private CategoryAdapter categoryAdapter;
    private BlogAdapter blogAdapter;

    private List<Product> newProductList = new ArrayList<>();
    private List<Product> flashSaleList = new ArrayList<>();
    private List<Category> categoryList = new ArrayList<>();
    private List<Blog> blogList = new ArrayList<>();
    private List<Product> allProductsForSearch = new ArrayList<>();
    private List<String> bannerImages = new ArrayList<>();
    private int currentBannerIndex = 0;

    // Countdown Timer logic
    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long endTime; 
    
    // Banner Slider logic
    private Runnable bannerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!bannerImages.isEmpty()) {
                View view = getView();
                if (view != null) {
                    ImageView ivBanner = view.findViewById(R.id.ivBanner);
                    if (ivBanner != null) {
                        Glide.with(HomeFragment.this)
                                .load("file:///android_asset/images/banners/" + bannerImages.get(currentBannerIndex))
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                                .into(ivBanner);
                        
                        currentBannerIndex = (currentBannerIndex + 1) % bannerImages.size();
                    }
                }
            }
            timerHandler.postDelayed(this, 2000); // 2 giây đổi 1 lần
        }
    };
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = endTime - System.currentTimeMillis();
            if (millis > 0) {
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                seconds = seconds % 60;
                minutes = minutes % 60;

                View view = getView();
                if (view != null) {
                    TextView tvH = view.findViewById(R.id.tvTimerHour);
                    TextView tvM = view.findViewById(R.id.tvTimerMinute);
                    TextView tvS = view.findViewById(R.id.tvTimerSecond);
                    if (tvH != null) tvH.setText(String.format("%02d", hours));
                    if (tvM != null) tvM.setText(String.format("%02d", minutes));
                    if (tvS != null) tvS.setText(String.format("%02d", seconds));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // Cấu hình danh sách banner từ assets
        bannerImages.clear();
        bannerImages.addAll(Arrays.asList(
            "banner01.jpg", "banner02.jpg", "banner03.jpg", 
            "banner04.jpg", "banner05.jpg", "banner06.jpg", "banner07.jpg",
            "freshfood.jpg", "goodfood.jpg", "healthyfood.jpg"
        ));
        Collections.shuffle(bannerImages);

        initViews(view);
        setupSearch(view);
        fetchCategories();
        fetchProducts();
        fetchBlogs();

        // Khởi động đồng hồ đếm ngược (giả lập 2 giờ)
        endTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000);
        timerHandler.post(timerRunnable);
        
        // Khởi động slider banner
        timerHandler.post(bannerRunnable);

        return view;
    }

    private void initViews(View view) {
        // Categories
        rvCategories = view.findViewById(R.id.rvCategories);
        categoryAdapter = new CategoryAdapter(categoryList, this);
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        // Flash Sale (Horizontal)
        rvFlashSale = view.findViewById(R.id.rvFlashSale);
        flashSaleAdapter = new ProductAdapter(flashSaleList, this, true);
        LinearLayoutManager flashSaleLM = new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        rvFlashSale.setLayoutManager(flashSaleLM);
        rvFlashSale.setAdapter(flashSaleAdapter);

        // Flash Sale Scroll Buttons
        View btnLeft = view.findViewById(R.id.btnFlashSaleLeft);
        View btnRight = view.findViewById(R.id.btnFlashSaleRight);

        btnLeft.setOnClickListener(v -> {
            int pos = flashSaleLM.findFirstVisibleItemPosition();
            if (pos > 0) rvFlashSale.smoothScrollToPosition(pos - 1);
        });

        btnRight.setOnClickListener(v -> {
            int pos = flashSaleLM.findLastVisibleItemPosition();
            if (pos < flashSaleAdapter.getItemCount() - 1) rvFlashSale.smoothScrollToPosition(pos + 1);
        });

        rvFlashSale.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                btnLeft.setVisibility(flashSaleLM.findFirstVisibleItemPosition() > 0 ? View.VISIBLE : View.GONE);
                btnRight.setVisibility(flashSaleLM.findLastVisibleItemPosition() < flashSaleAdapter.getItemCount() - 1 ? View.VISIBLE : View.GONE);
            }
        });

        // New Products (Grid)
        rvNewProducts = view.findViewById(R.id.rvNewProducts);
        newProductAdapter = new ProductAdapter(newProductList, this, false);
        rvNewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvNewProducts.setAdapter(newProductAdapter);

        // Blogs (Horizontal)
        rvBlogs = view.findViewById(R.id.rvBlogs);
        blogAdapter = new BlogAdapter(blogList, this);
        rvBlogs.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvBlogs.setAdapter(blogAdapter);

        view.findViewById(R.id.tvViewAllNew).setOnClickListener(v -> navigateToCategory(null));

        setupChipListeners(view);
    }

    private void setupChipListeners(View view) {
        int[] chipIds = {
                R.id.chipHatDinhDuong, R.id.chipGranola, R.id.chipTraiCaySay,
                R.id.chipDoAnVat, R.id.chipTraThaoMoc, R.id.chipCombo
        };
        String[] categoryNames = {
                "Hạt dinh dưỡng", "Granola", "Trái cây sấy",
                "Đồ ăn vặt", "Trà thảo mộc", "Combo"
        };

        for (int i = 0; i < chipIds.length; i++) {
            final String categoryName = categoryNames[i];
            View chip = view.findViewById(chipIds[i]);
            if (chip != null) {
                chip.setOnClickListener(v -> navigateToCategory(categoryName));
            }
        }
    }

    private void setupSearch(View view) {
        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                if (query.isEmpty()) {
                    view.findViewById(R.id.mainContent).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.rvRealtimeSearch).setVisibility(View.GONE);
                } else {
                    view.findViewById(R.id.mainContent).setVisibility(View.GONE);
                    view.findViewById(R.id.rvRealtimeSearch).setVisibility(View.VISIBLE);
                    
                    List<Product> searchResults = new ArrayList<>();
                    for (Product p : allProductsForSearch) {
                        if (p.getName().toLowerCase().contains(query)) {
                            searchResults.add(p);
                        }
                    }
                    
                    RecyclerView rvSearch = view.findViewById(R.id.rvRealtimeSearch);
                    ProductAdapter searchAdapter = new ProductAdapter(searchResults, HomeFragment.this);
                    rvSearch.setLayoutManager(new GridLayoutManager(getContext(), 2));
                    rvSearch.setAdapter(searchAdapter);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void fetchCategories() {
        // Ưu tiên tính nhất quán của thương hiệu HealthUp với 6 danh mục chính
        List<String> brandCategories = Arrays.asList("Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo");
        categoryList.clear();
        for (int i = 0; i < brandCategories.size(); i++) {
            categoryList.add(new Category(String.valueOf(i + 1), brandCategories.get(i), "fruit.png"));
        }
        categoryAdapter.notifyDataSetChanged();

        // Cập nhật icon từ Firestore nếu có dữ liệu phù hợp
        FirestoreManager.getInstance().getFirestore().collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasChanges = false;
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("name");
                        String iconUrl = doc.getString("iconUrl");
                        if (name != null && iconUrl != null) {
                            for (Category cat : categoryList) {
                                if (cat.getName().equalsIgnoreCase(name)) {
                                    cat.setIconUrl(iconUrl);
                                    hasChanges = true;
                                }
                            }
                        }
                    }
                    if (hasChanges) {
                        categoryAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchProducts() {
        FirestoreManager.getInstance().getProductsCollection()
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Product> allFetched = new ArrayList<>();
                    List<Product> flashSales = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            allFetched.add(product);
                            
                            // Phân loại Flash Sale
                            Object isFlash = doc.get("isFlashSale");
                            if (isFlash instanceof Boolean && (Boolean)isFlash) {
                                product.setFlashSale(true);
                                flashSales.add(product);
                            }

                            // Phân loại Sản phẩm mới
                            Object isNewObj = doc.get("isNew");
                            if (isNewObj instanceof Boolean && (Boolean)isNewObj) {
                                product.setNew(true);
                            }
                        }
                    }
                    
                    allProductsForSearch.clear();
                    allProductsForSearch.addAll(allFetched);

                    // Hiển thị Flash Sale (lấy tối đa 5 cái)
                    flashSaleList.clear();
                    if (!flashSales.isEmpty()) {
                        Collections.shuffle(flashSales);
                        flashSaleList.addAll(flashSales.subList(0, Math.min(flashSales.size(), 5)));
                    } else if (!allFetched.isEmpty()) {
                        // Nếu không có sp nào marked flash sale, lấy tạm 5 cái đầu
                        flashSaleList.addAll(allFetched.subList(0, Math.min(allFetched.size(), 5)));
                    }
                    flashSaleAdapter.updateData(new ArrayList<>(flashSaleList));

                    // Hiển thị New Products (Lọc sản phẩm mới hoặc Random 10 cái nếu không có)
                    newProductList.clear();
                    List<Product> newProducts = new ArrayList<>();
                    for (Product p : allFetched) {
                        if (p.isNew()) newProducts.add(p);
                    }

                    if (!newProducts.isEmpty()) {
                        Collections.shuffle(newProducts);
                        newProductList.addAll(newProducts.subList(0, Math.min(newProducts.size(), 10)));
                    } else if (!allFetched.isEmpty()) {
                        Collections.shuffle(allFetched);
                        int limit = Math.min(allFetched.size(), 10);
                        newProductList.addAll(allFetched.subList(0, limit));
                    } else {
                        newProductList.addAll(Product.getDummyProducts());
                    }
                    newProductAdapter.updateData(new ArrayList<>(newProductList));
                })
                .addOnFailureListener(e -> {
                    newProductList.clear();
                    newProductList.addAll(Product.getDummyProducts());
                    newProductAdapter.notifyDataSetChanged();
                });
    }

    private void fetchBlogs() {
        FirestoreManager.getInstance().getFirestore().collection("blogs")
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    blogList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Blog blog = doc.toObject(Blog.class);
                        if (blog != null) {
                            blog.setId(doc.getId());
                            blogList.add(blog);
                        }
                    }
                    blogAdapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onCategoryClick(Category category) {
        navigateToCategory(category.getName());
    }

    private void navigateToCategory(String categoryName) {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            BottomNavigationView navView = mainActivity.findViewById(R.id.bottom_navigation);
            // Cập nhật tab đang chọn sang 'Danh mục'
            navView.setSelectedItemId(R.id.nav_category);
            
            ProductListFragment fragment = new ProductListFragment();
            Bundle args = new Bundle();
            args.putString("category", categoryName != null ? categoryName : "Tất cả");
            fragment.setArguments(args);
            
            mainActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onProductClick(Product product) {
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }

    @Override
    public void onAddToCart(Product product) {
        Toast.makeText(getContext(), "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFavoriteClick(Product product) {
        product.setFavorite(!product.isFavorite());
        newProductAdapter.notifyDataSetChanged();
        if (flashSaleAdapter != null) flashSaleAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBlogClick(Blog blog) {
        Intent intent = new Intent(getContext(), BlogDetailActivity.class);
        intent.putExtra("blogId", blog.getId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.removeCallbacks(bannerRunnable);
    }
}
