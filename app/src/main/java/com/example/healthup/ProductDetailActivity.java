package com.example.healthup;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
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
                        showProductUi();
                    } else {
                        finish();
                    }
                })
                .addOnFailureListener(e -> finish());
    }

    private void showProductUi() {
        initViews();
        String uid = WishlistManager.currentUserId();
        if (uid != null && product.getId() != null) {
            WishlistManager.loadFavoriteIds(uid, ids -> {
                product.setFavorite(ids.contains(product.getId()));
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        setupProductInfo();
                        setupExpandableSections();
                        setupRecommendations();
                    }
                });
            });
        } else {
            product.setFavorite(false);
            setupProductInfo();
            setupExpandableSections();
            setupRecommendations();
        }
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
        View btnChat = findViewById(R.id.btn_chat);

        rvRecommendations = findViewById(R.id.rv_detail_recommendations);
        rvReviews = findViewById(R.id.rv_reviews);
        tvViewAllReviews = findViewById(R.id.tv_view_all_reviews);

        btnBack.setOnClickListener(v -> finish());
        btnWishlist.setOnClickListener(v -> toggleFavorite());
        btnAddCart.setOnClickListener(v -> showVariantSelection(false));
        btnBuyNow.setOnClickListener(v -> showVariantSelection(true));
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> openProductChat());
        }

        if (tvViewAllReviews != null) {
            tvViewAllReviews.setOnClickListener(v -> openAllReviews());
        }
    }

    private void openAllReviews() {
        if (product == null || product.getId() == null) return;
        android.content.Intent intent = new android.content.Intent(this, ProductReviewsActivity.class);
        intent.putExtra("productId", product.getId());
        intent.putExtra("product_name", product.getName());
        intent.putExtra("avgRating", product.getRating());
        intent.putExtra("reviewCount", product.getReviewCount());
        startActivity(intent);
    }

    private void openProductChat() {
        if (product == null || product.getName() == null) {
            Toast.makeText(this, "Đang tải thông tin sản phẩm...", Toast.LENGTH_SHORT).show();
            return;
        }
        String variant = selectedVariant != null ? selectedVariant.getName() : null;
        startActivity(ChatActivity.buyerIntentForProductBrowse(
                this, product.getName(), variant, product.getId()));
    }

    private void setupProductInfo() {
        tvName.setText(product.getName());
        updatePriceDisplay();

        tvRating.setText(String.valueOf(product.getRating()));
        tvReviewCount.setText(product.getReviewCount() + " đánh giá");
        tvSold.setText("Đã bán " + product.getSoldCount() + "+");

        updateWishlistIcon();
        setupVariants();

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

        fetchRecommendationsByCategory(recommendations);
        fetchReviews();
    }

    // FIX ROOT CAUSE: Product.java xác nhận field "cat" trên Firestore CÓ THỂ là String
    // hoặc List<String> (tùy sản phẩm). Code cũ chỉ dùng whereEqualTo("cat", category),
    // nếu "cat" trên Firestore là kiểu array thì so sánh với String KHÔNG BAO GIỜ khớp
    // -> query trả 0 kết quả -> "Có thể bạn quan tâm" trống trơn.
    // Cách fix: thử whereEqualTo trước (khớp trường hợp cat là String),
    // nếu rỗng thì thử whereArrayContains (khớp trường hợp cat là array),
    // nếu vẫn rỗng mới rơi về fallback (lấy tạm sản phẩm khác để luôn có gợi ý).
    private void fetchRecommendationsByCategory(List<Product> recommendations) {
        String category = product.getCategory();
        if (category == null || category.isEmpty()) {
            fetchFallbackRecommendations(recommendations);
            return;
        }

        FirestoreManager.getInstance().getProductsCollection()
                .whereEqualTo("cat", category)
                .limit(8).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        fillRecommendations(recommendations, snapshot);
                    } else {
                        tryArrayContainsCategory(recommendations, category);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ProductDetail", "Lỗi whereEqualTo cat: " + e.getMessage());
                    tryArrayContainsCategory(recommendations, category);
                });
    }

    private void tryArrayContainsCategory(List<Product> recommendations, String category) {
        FirestoreManager.getInstance().getProductsCollection()
                .whereArrayContains("cat", category)
                .limit(8).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        fillRecommendations(recommendations, snapshot);
                    } else {
                        fetchFallbackRecommendations(recommendations);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ProductDetail", "Lỗi whereArrayContains cat: " + e.getMessage());
                    fetchFallbackRecommendations(recommendations);
                });
    }

    private void fillRecommendations(List<Product> recommendations, QuerySnapshot snapshot) {
        recommendations.clear();
        for (DocumentSnapshot doc : snapshot) {
            Product p = doc.toObject(Product.class);
            if (p != null && !doc.getId().equals(product.getId())) {
                p.setId(doc.getId());
                recommendations.add(p);
                if (recommendations.size() >= 6) break;
            }
        }
        if (recommendations.isEmpty()) {
            fetchFallbackRecommendations(recommendations);
        } else {
            recommendationAdapter.notifyDataSetChanged();
        }
    }

    // Fallback cuối cùng: không lọc điều kiện gì, đảm bảo luôn có dữ liệu Firestore thật hiển thị
    private void fetchFallbackRecommendations(List<Product> recommendations) {
        FirestoreManager.getInstance().getProductsCollection()
                .limit(8).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recommendations.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product p = doc.toObject(Product.class);
                        if (p != null && !doc.getId().equals(product.getId())) {
                            p.setId(doc.getId());
                            recommendations.add(p);
                            if (recommendations.size() >= 6) break;
                        }
                    }
                    recommendationAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Log.e("ProductDetail", "Lỗi fallback recommendations: " + e.getMessage()));
    }

    private void fetchReviews() {
        List<com.example.models.Review> reviews = new ArrayList<>();
        ProductReviewEntryAdapter reviewAdapter = new ProductReviewEntryAdapter(reviews);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(reviewAdapter);

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

        WishlistManager.toggle(this, p, success -> {
            if (!success) {
                return;
            }
            if (p.getId().equals(product.getId())) {
                product.setFavorite(p.isFavorite());
                updateWishlistIcon();
            }
            if (recommendationAdapter != null) {
                recommendationAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updateWishlistIcon() {
        boolean isFavorite = product.isFavorite();
        btnWishlist.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        int tintColor = ContextCompat.getColor(this, isFavorite ? R.color.error : R.color.text_dark);
        btnWishlist.setImageTintList(ColorStateList.valueOf(tintColor));
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
                        if (selectedVariant != null) {
                            newItem.setVariantId(selectedVariant.getId());
                            newItem.setVariantName(selectedVariant.getName());
                            newItem.setPrice(selectedVariant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                        }
                        cartRef.add(newItem);
                    }
                    Toast.makeText(this, getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
