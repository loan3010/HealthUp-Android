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
import com.example.healthup.R;
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




    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long endTime;


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
            timerHandler.postDelayed(this, 2000);
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




        endTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000);
        timerHandler.post(timerRunnable);


        timerHandler.post(bannerRunnable);




        return view;
    }




    private void initViews(View view) {
        rvCategories = view.findViewById(R.id.rvCategories);
        categoryAdapter = new CategoryAdapter(categoryList, this);
        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);




        rvFlashSale = view.findViewById(R.id.rvFlashSale);
        flashSaleAdapter = new ProductAdapter(flashSaleList, this, true);
        LinearLayoutManager flashSaleLM = new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        rvFlashSale.setLayoutManager(flashSaleLM);
        rvFlashSale.setAdapter(flashSaleAdapter);




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




        rvNewProducts = view.findViewById(R.id.rvNewProducts);
        newProductAdapter = new ProductAdapter(newProductList, this, false);
        rvNewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvNewProducts.setAdapter(newProductAdapter);




        rvBlogs = view.findViewById(R.id.rvBlogs);
        blogAdapter = new BlogAdapter(blogList, this);
        rvBlogs.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvBlogs.setAdapter(blogAdapter);




        view.findViewById(R.id.tvViewAllNew).setOnClickListener(v -> navigateToCategory(null));




        View tvViewAllBlogs = view.findViewById(R.id.tvViewAllBlogs);
        if (tvViewAllBlogs != null) {
            tvViewAllBlogs.setOnClickListener(v -> openBlogList());
        }




        setupChipListeners(view);
    }




    private void openBlogList() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new BlogFragment())
                    .addToBackStack(null)
                    .commit();
        }
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
        List<String> brandCategories = Arrays.asList("Hạt dinh dưỡng", "Granola", "Trái cây sấy", "Đồ ăn vặt", "Trà thảo mộc", "Combo");
        categoryList.clear();
        for (int i = 0; i < brandCategories.size(); i++) {
            categoryList.add(new Category(String.valueOf(i + 1), brandCategories.get(i), "fruit.png"));
        }
        categoryAdapter.notifyDataSetChanged();




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


                            Object isFlash = doc.get("isFlashSale");
                            if (isFlash instanceof Boolean && (Boolean)isFlash) {
                                product.setFlashSale(true);
                                flashSales.add(product);
                            }




                            Object isNewObj = doc.get("isNew");
                            if (isNewObj instanceof Boolean && (Boolean)isNewObj) {
                                product.setNew(true);
                            }
                        }
                    }


                    allProductsForSearch.clear();
                    allProductsForSearch.addAll(allFetched);




                    flashSaleList.clear();
                    if (!flashSales.isEmpty()) {
                        Collections.shuffle(flashSales);
                        flashSaleList.addAll(flashSales.subList(0, Math.min(flashSales.size(), 5)));
                    } else if (!allFetched.isEmpty()) {
                        flashSaleList.addAll(allFetched.subList(0, Math.min(allFetched.size(), 5)));
                    }
                    flashSaleAdapter.updateData(new ArrayList<>(flashSaleList));




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
                    applyWishlistToHomeLists();
                })
                .addOnFailureListener(e -> {
                    newProductList.clear();
                    newProductList.addAll(Product.getDummyProducts());
                    newProductAdapter.notifyDataSetChanged();
                });
    }




    // FIX ROOT CAUSE (yêu cầu #4): applyFavoriteState() MUTATE trực tiếp trên các Product
    // object đang được adapter giữ tham chiếu (không tạo bản sao mới). Khi đó gọi
    // updateData(new ArrayList<>(list)) truyền vào DiffUtil 1 "newList" chứa CHÍNH XÁC
    // cùng các object reference với "oldList" (vốn cũng chỉ là những Product cũ chưa từng
    // bị clone) -> DiffUtil.areContentsTheSame() so sánh 2 tham chiếu giống hệt nhau
    // (bằng nhau tuyệt đối) nên luôn kết luận "không có gì thay đổi" -> KHÔNG gọi
    // notifyItemChanged cho item đó -> RecyclerView không vẽ lại trái tim dù dữ liệu
    // isFavorite đã đổi. Đây chính là lý do trái tim không đỏ trên Home dù đã tim ở
    // trang Chi tiết sản phẩm. Sửa: dùng notifyDataSetChanged() cho bước đồng bộ này
    // (không dùng updateData/DiffUtil), vì đây chỉ là cập nhật field của các item sẵn có,
    // không phải thay đổi cấu trúc danh sách.
    private void applyWishlistToHomeLists() {
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            return;
        }
        WishlistManager.loadFavoriteIds(uid, ids -> {
            if (!isAdded()) {
                return;
            }
            WishlistManager.applyFavoriteState(flashSaleList, ids);
            WishlistManager.applyFavoriteState(newProductList, ids);
            if (flashSaleAdapter != null) {
                flashSaleAdapter.notifyDataSetChanged();
            }
            if (newProductAdapter != null) {
                newProductAdapter.notifyDataSetChanged();
            }
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




    // FIX (yêu cầu #3): luôn hiển thị popup chọn số lượng/phân loại khi bấm "+", bất kể
    // sản phẩm có phân loại hay không, để giống hành vi nút "Thêm vào giỏ hàng" ở trang
    // Chi tiết sản phẩm (vốn luôn mở bottom sheet, kể cả sản phẩm không có phân loại).
    @Override
    public void onAddToCart(Product product) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), getString(R.string.login_required_cart), Toast.LENGTH_SHORT).show();
            return;
        }
        showVariantSheet(product);
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
                newProductAdapter.notifyDataSetChanged();
                if (flashSaleAdapter != null) {
                    flashSaleAdapter.notifyDataSetChanged();
                }
            }
        });
    }




    @Override
    public void onBlogClick(Blog blog) {
        Intent intent = new Intent(getContext(), BlogDetailActivity.class);
        intent.putExtra("blog", blog);
        startActivity(intent);
    }




    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.removeCallbacks(bannerRunnable);
    }
}