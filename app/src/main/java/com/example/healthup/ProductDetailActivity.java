package com.example.healthup;


import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.R;
import com.example.healthup.ProductAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class ProductDetailActivity extends AppCompatActivity {


    private Product product;
    private ViewPager2 viewPagerImages;
    private TextView tvImageIndex, tvName, tvPrice, tvOriginalPrice, tvDiscount, tvRating, tvReviewCount, tvSold, tvSavings, tvStock;
    private TextView tvViewAllReviews;
    private ImageButton btnBack, btnShare, btnWishlist;
    private MaterialButton btnAddCart, btnBuyNow;
    private RecyclerView rvRecommendations, rvReviews;
    private ProductAdapter recommendationAdapter;
    private com.google.android.material.chip.ChipGroup chipGroupVariants;
    private View layoutVariants, dividerVariants;
    private Product.ProductVariant selectedVariant;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);


        // Ưu tiên lấy productId để fetch dữ liệu mới nhất từ Firestore, tránh lỗi quá tải Intent
        String productId = getIntent().getStringExtra("productId");
        product = (Product) getIntent().getSerializableExtra("product");


        if (productId != null) {
            fetchProductDetails(productId);
        } else if (product != null && product.getId() != null) {
            fetchProductDetails(product.getId());
        } else {
            Toast.makeText(this, "Không tìm thấy thông tin sản phẩm", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void fetchProductDetails(String productId) {
        FirestoreManager.getInstance().getProductsCollection().document(productId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    product = documentSnapshot.toObject(Product.class);
                    if (product != null) {
                        product.setId(documentSnapshot.getId());
                        initViews();
                        setupProductInfo();
                        setupExpandableSections();
                        setupRecommendations();
                    } else {
                        finish();
                    }
                })
                .addOnFailureListener(e -> finish());
    }


    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        viewPagerImages = findViewById(R.id.view_pager_images);
        tvImageIndex = findViewById(R.id.tv_image_index);
        tvName = findViewById(R.id.tv_detail_name);
        tvPrice = findViewById(R.id.tv_detail_price);
        tvOriginalPrice = findViewById(R.id.tv_detail_original_price);
        tvDiscount = findViewById(R.id.tv_discount_tag);
        tvRating = findViewById(R.id.tv_detail_rating);
        tvReviewCount = findViewById(R.id.tv_review_count);
        tvSold = findViewById(R.id.tv_detail_sold);
        tvSavings = findViewById(R.id.tv_savings);
        tvStock = findViewById(R.id.tv_detail_stock);


        layoutVariants = findViewById(R.id.layout_variants);
        dividerVariants = findViewById(R.id.divider_variants);
        chipGroupVariants = findViewById(R.id.chip_group_variants);
        btnShare = findViewById(R.id.btn_share);
        btnWishlist = findViewById(R.id.btn_wishlist);
        btnAddCart = findViewById(R.id.btn_detail_add_cart);
        btnBuyNow = findViewById(R.id.btn_buy_now);


        rvRecommendations = findViewById(R.id.rv_detail_recommendations);
        rvReviews = findViewById(R.id.rv_reviews);
        tvViewAllReviews = findViewById(R.id.tv_view_all_reviews);


        btnBack.setOnClickListener(v -> finish());
        btnWishlist.setOnClickListener(v -> toggleFavorite());
        btnAddCart.setOnClickListener(v -> showVariantSelection(false));
        btnBuyNow.setOnClickListener(v -> showVariantSelection(true));


        // FIX: nút "Xem tất cả" đánh giá trước đây không có sự kiện click nào cả
        if (tvViewAllReviews != null) {
            tvViewAllReviews.setOnClickListener(v -> openAllReviews());
        }
    }


    // FIX: mở màn hình xem tất cả đánh giá thật của sản phẩm đang xem (truyền productId
    // để ProductReviewsActivity query đúng sub-collection "reviews" của sản phẩm này)
    private void openAllReviews() {
        if (product == null || product.getId() == null) return;
        android.content.Intent intent = new android.content.Intent(this, ProductReviewsActivity.class);
        intent.putExtra("productId", product.getId());
        intent.putExtra("product_name", product.getName());
        startActivity(intent);
    }


    private void setupProductInfo() {
        tvName.setText(product.getName());
        updatePriceDisplay();


        tvRating.setText(String.valueOf(product.getRating()));
        tvReviewCount.setText(product.getReviewCount() + " đánh giá");
        tvSold.setText("Đã bán " + product.getSoldCount() + "+");


        updateWishlistIcon();
        setupVariants();


        // Image Slider setup
        List<String> images = product.getImages();
        if (images == null || images.isEmpty()) {
            images = new ArrayList<>();
            images.add(product.getImageUrl());
        }


        final List<String> finalImages = images;
        viewPagerImages.setAdapter(new ImageSliderAdapter(finalImages));


        tvImageIndex.setText("1/" + finalImages.size());
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvImageIndex.setText((position + 1) + "/" + finalImages.size());
            }
        });
    }


    private void setupVariants() {
        if (product.isHasVariants() && product.getVariants() != null && !product.getVariants().isEmpty()) {
            layoutVariants.setVisibility(View.VISIBLE);
            dividerVariants.setVisibility(View.VISIBLE);
            chipGroupVariants.removeAllViews();


            for (Product.ProductVariant variant : product.getVariants()) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) getLayoutInflater()
                        .inflate(R.layout.item_variant_chip, chipGroupVariants, false);
                chip.setText(variant.getName());
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedVariant = variant;
                        updatePriceDisplay();
                    }
                });
                chipGroupVariants.addView(chip);
            }
            // Select first variant by default
            if (chipGroupVariants.getChildCount() > 0) {
                ((com.google.android.material.chip.Chip) chipGroupVariants.getChildAt(0)).setChecked(true);
            }
        } else {
            layoutVariants.setVisibility(View.GONE);
            dividerVariants.setVisibility(View.GONE);
            selectedVariant = null;
            updatePriceDisplay();
        }
    }


    private void updatePriceDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        double displayPrice = (selectedVariant != null) ? selectedVariant.getPrice() : product.getPrice();
        int stock = (selectedVariant != null) ? selectedVariant.getStock() : product.getStockCount();


        tvPrice.setText(formatter.format(displayPrice) + "đ");


        if (product.getOriginalPrice() > displayPrice) {
            tvOriginalPrice.setVisibility(View.VISIBLE);
            tvDiscount.setVisibility(View.VISIBLE);
            tvSavings.setVisibility(View.VISIBLE);


            tvOriginalPrice.setText(formatter.format(product.getOriginalPrice()) + "đ");
            tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);


            int discountPercent = (int) (((product.getOriginalPrice() - displayPrice) / product.getOriginalPrice()) * 100);
            tvDiscount.setText("-" + discountPercent + "%");
            tvSavings.setText("Tiết kiệm " + formatter.format(product.getOriginalPrice() - displayPrice) + "đ");
        } else {
            tvOriginalPrice.setVisibility(View.GONE);
            tvDiscount.setVisibility(View.GONE);
            tvSavings.setVisibility(View.GONE);
        }


        if (tvStock != null) {
            tvStock.setText(getString(R.string.stock_prefix, stock));
            btnAddCart.setEnabled(stock > 0);
            btnBuyNow.setEnabled(stock > 0);
        }
    }


    private void setupExpandableSections() {
        setupSection(findViewById(R.id.section_ingredients), "Thành phần chính", product.getIngredients());


        // Format Nutrition List
        StringBuilder nutritionText = new StringBuilder();
        if (product.getNutrition() != null) {
            for (Product.NutritionItem item : product.getNutrition()) {
                nutritionText.append("• ").append(item.getName())
                        .append(": ").append(item.getValue());
                if (item.getPercent() > 0) {
                    nutritionText.append(" (").append(item.getPercent()).append("%)");
                }
                nutritionText.append("\n");
            }
        }
        setupSection(findViewById(R.id.section_nutrition), "Giá trị dinh dưỡng",
                nutritionText.length() > 0 ? nutritionText.toString().trim() : null);


        setupSection(findViewById(R.id.section_usage), "Hướng dẫn sử dụng", product.getUsage());
        setupSection(findViewById(R.id.section_origin), "Nguồn gốc xuất xứ", product.getOrigin());
    }


    private void setupSection(View sectionView, String title, String content) {
        if (sectionView == null) return;


        TextView tvTitle = sectionView.findViewById(R.id.tv_section_title);
        TextView tvContent = sectionView.findViewById(R.id.tv_section_content);
        View btnExpand = sectionView.findViewById(R.id.btn_expand);
        ImageView ivArrow = sectionView.findViewById(R.id.iv_expand_arrow);


        if (tvTitle != null) tvTitle.setText(title);
        if (tvContent != null) tvContent.setText(content != null ? content : "Thông tin đang được cập nhật...");


        if (btnExpand != null) {
            btnExpand.setOnClickListener(v -> {
                if (tvContent != null && ivArrow != null) {
                    if (tvContent.getVisibility() == View.GONE) {
                        tvContent.setVisibility(View.VISIBLE);
                        ivArrow.setRotation(90);
                    } else {
                        tvContent.setVisibility(View.GONE);
                        ivArrow.setRotation(-90);
                    }
                }
            });
        }
    }


    private void setupRecommendations() {
        List<Product> recommendations = new ArrayList<>();
        recommendationAdapter = new ProductAdapter(recommendations, new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product p) {
                if (p == null || p.getId() == null) return;
                android.content.Intent intent = new android.content.Intent(ProductDetailActivity.this, ProductDetailActivity.class);
                // CHỈ truyền productId để tránh lỗi crash do quá tải Intent hoặc Serialization
                intent.putExtra("productId", p.getId());
                startActivity(intent);
                finish();
            }
            @Override
            public void onAddToCart(Product p) {
                if (p.isHasVariants()) {
                    showVariantSelection(false);
                } else {
                    addToCart(1);
                }
            }
            @Override
            public void onFavoriteClick(Product p) {
                toggleFavoriteForProduct(p);
            }
        });


        rvRecommendations.setLayoutManager(new GridLayoutManager(this, 2));
        rvRecommendations.setAdapter(recommendationAdapter);


        FirestoreManager.getInstance().getProductsCollection()
                .whereEqualTo("cat", product.getCategory())
                .limit(4).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recommendations.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product p = doc.toObject(Product.class);
                        if (p != null && !doc.getId().equals(product.getId())) {
                            p.setId(doc.getId());
                            recommendations.add(p);
                        }
                    }
                    recommendationAdapter.notifyDataSetChanged();
                });


        fetchReviews();
    }


    private void fetchReviews() {
        List<com.example.models.Review> reviews = new ArrayList<>();
        ProductReviewEntryAdapter reviewAdapter = new ProductReviewEntryAdapter(reviews);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(reviewAdapter);


        // Giả sử review được lưu trong sub-collection 'reviews' của mỗi product
        FirestoreManager.getInstance().getProductsCollection()
                .document(product.getId())
                .collection("reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    reviews.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        com.example.models.Review r = doc.toObject(com.example.models.Review.class);
                        if (r != null) reviews.add(r);
                    }
                    reviewAdapter.notifyDataSetChanged();
                    if (reviews.isEmpty()) {
                        findViewById(R.id.tv_no_reviews).setVisibility(View.VISIBLE);
                    }
                });
    }


    private void toggleFavorite() {
        toggleFavoriteForProduct(product);
    }


    private void toggleFavoriteForProduct(Product p) {
        if (p == null || p.getId() == null) return;


        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để sử dụng chức năng yêu thích", Toast.LENGTH_SHORT).show();
            return;
        }


        boolean oldFavoriteState = p.isFavorite();
        boolean newFavoriteState = !oldFavoriteState;


        p.setFavorite(newFavoriteState);


        if (p.getId().equals(product.getId())) {
            product.setFavorite(newFavoriteState);
            updateWishlistIcon();
        }


        if (recommendationAdapter != null) {
            recommendationAdapter.notifyDataSetChanged();
        }


        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("favorite", newFavoriteState);


        FirestoreManager.getInstance().getProductsCollection()
                .document(p.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, newFavoriteState ? "Đã thêm vào yêu thích" : "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    p.setFavorite(oldFavoriteState);
                    if (p.getId().equals(product.getId())) {
                        product.setFavorite(oldFavoriteState);
                        updateWishlistIcon();
                    }
                    if (recommendationAdapter != null) {
                        recommendationAdapter.notifyDataSetChanged();
                    }


                    android.util.Log.e("ProductDetail", "Favorite update failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Không thể cập nhật yêu thích: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


    private void updateWishlistIcon() {
        btnWishlist.setImageResource(product.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }


    private void showVariantSelection(boolean isBuyNow) {
        VariantBottomSheetFragment bottomSheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) -> {
            selectedVariant = variant;
            if (isBuyNow) {
                performBuyNow(quantity);
            } else {
                addToCart(quantity);
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "VariantSelection");
    }


    private void performBuyNow(int quantity) {
        addToCart(quantity);
        // TODO: Redirect to CheckoutActivity
    }


    private void addToCart(int quantity) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.login_required_cart), Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = user.getUid();


        String productId = product.getId();
        String variantId = (selectedVariant != null) ? selectedVariant.getId() : null;


        FirestoreManager.getInstance().getFirestore().collection("cart")
                .whereEqualTo("userId", userId)
                .whereEqualTo("productId", productId)
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
                        if (selectedVariant != null) {
                            newItem.setVariantId(selectedVariant.getId());
                            newItem.setVariantName(selectedVariant.getName());
                            newItem.setPrice(selectedVariant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                        }
                        FirestoreManager.getInstance().getFirestore().collection("cart")
                                .add(newItem);
                    }
                    Toast.makeText(this, getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}