package com.example.healthup;

import android.text.TextUtils;
import android.content.Intent;
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
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
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
import com.example.healthup.util.CartHelper;
import com.example.healthup.util.GuestWishlistUiHelper;
import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.ReviewStatsHelper;
import com.example.healthup.util.TranslationManager;
import com.example.models.Product;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProductDetailActivity extends AppCompatActivity {

    private Product product;
    private ViewPager2 viewPagerImages;
    private TextView tvImageIndex, tvName, tvShortDesc, tvDescription, tvPrice, tvOriginalPrice, tvDiscount, tvRating, tvReviewCount, tvSold, tvSavings, tvStock;
    private TextView tvViewAllReviews, tvViewAllRecommend, tvCartBadgeHeader;
    private ImageButton btnBack, btnShare, btnWishlist, btnCartHeader;
    private MaterialButton btnAddCart, btnBuyNow;
    private RecyclerView rvRecommendations, rvReviews;
    private ProductAdapter recommendationAdapter;
    private Product.ProductVariant selectedVariant;
    private com.google.firebase.firestore.ListenerRegistration cartListener;

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
                    product = Product.fromDocument(documentSnapshot);
                    if (product != null) {
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
                        setupToolbarScroll();
                    }
                });
            });
        } else {
            product.setFavorite(false);
            setupProductInfo();
            setupExpandableSections();
            setupRecommendations();
            setupToolbarScroll();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        viewPagerImages = findViewById(R.id.view_pager_images);
        tvImageIndex = findViewById(R.id.tv_image_index);
        tvName = findViewById(R.id.tv_detail_name);
        tvShortDesc = findViewById(R.id.tv_detail_short_desc);
        tvDescription = findViewById(R.id.tv_detail_description);
        tvPrice = findViewById(R.id.tv_detail_price);
        tvOriginalPrice = findViewById(R.id.tv_detail_original_price);
        tvDiscount = findViewById(R.id.tv_discount_tag);
        tvRating = findViewById(R.id.tv_detail_rating);
        tvReviewCount = findViewById(R.id.tv_review_count);
        tvSold = findViewById(R.id.tv_detail_sold);
        tvSavings = findViewById(R.id.tv_savings);
        tvStock = findViewById(R.id.tv_detail_stock);

        btnShare = findViewById(R.id.btn_share);
        btnWishlist = findViewById(R.id.btn_wishlist);
        btnCartHeader = findViewById(R.id.btn_cart_header);
        tvCartBadgeHeader = findViewById(R.id.tv_cart_badge_header);

        btnAddCart = findViewById(R.id.btn_detail_add_cart);
        btnBuyNow = findViewById(R.id.btn_buy_now);
        View btnChat = findViewById(R.id.btn_chat);

        rvRecommendations = findViewById(R.id.rv_detail_recommendations);
        rvReviews = findViewById(R.id.rv_reviews);
        tvViewAllReviews = findViewById(R.id.tv_view_all_reviews);
        tvViewAllRecommend = findViewById(R.id.tv_view_all_recommend);

        btnBack.setOnClickListener(v -> finish());
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareProduct());
        }
        btnWishlist.setOnClickListener(v -> toggleFavorite());
        if (btnCartHeader != null) {
            btnCartHeader.setOnClickListener(v -> {
                // FIX (bug #4): trước đây dùng FLAG_ACTIVITY_CLEAR_TOP|SINGLE_TOP sẽ XÓA
                // ProductDetailActivity ra khỏi back stack (vì MainActivity gốc nằm ngay dưới
                // nó), khiến bấm "Quay lại" ở Giỏ hàng không còn màn Chi tiết sản phẩm để quay
                // về -> rơi về trang chủ. Bỏ 2 flag này để MainActivity mới được ĐẨY CHỒNG lên
                // trên ProductDetailActivity (không hủy nó), đồng thời gắn cờ
                // "return_to_previous" để CartFragment biết cần finish() để quay lại đúng màn
                // Chi tiết sản phẩm khi người dùng bấm nút "Quay lại".
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", "cart_tab");
                intent.putExtra("return_to_previous", true);
                startActivity(intent);
            });
        }
        btnAddCart.setOnClickListener(v -> showVariantSelection(false));
        btnBuyNow.setOnClickListener(v -> showVariantSelection(true));
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> openProductChat());
        }

        if (tvViewAllReviews != null) {
            tvViewAllReviews.setOnClickListener(v -> openAllReviews());
        }

        if (tvViewAllRecommend != null) {
            tvViewAllRecommend.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", "category_tab");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
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

        if (tvShortDesc != null) {
            String shortDesc = product.getShortDesc();
            if (!TextUtils.isEmpty(shortDesc)) {
                tvShortDesc.setText(shortDesc.trim());
                tvShortDesc.setVisibility(View.VISIBLE);
            } else {
                tvShortDesc.setVisibility(View.GONE);
            }
        }

        if (tvDescription != null) {
            String description = product.getDescription();
            if (!TextUtils.isEmpty(description)) {
                tvDescription.setText(description.trim());
                tvDescription.setVisibility(View.VISIBLE);
            } else {
                tvDescription.setVisibility(View.GONE);
            }
        }
        
        String currentLang = LocaleHelper.getLanguage(this);
        if ("en".equals(currentLang)) {
            TranslationManager.translate(product.getName(), "en", translated -> {
                if (translated != null) tvName.setText(translated);
            });
        }
        
        updatePriceDisplay();
        setupCartBadgeListener();

        updateRatingUi(product.getRating(), product.getReviewCount());
        loadReviewStats();
        tvSold.setText("Đã bán " + product.getSoldCount());

        updateWishlistIcon();
        setupImagePager();
    }

    private void updateRatingUi(float avgRating, int reviewCount) {
        if (tvRating != null) {
            tvRating.setText(ReviewStatsHelper.formatAvgRating(avgRating));
        }
        if (tvReviewCount != null) {
            tvReviewCount.setText(ReviewStatsHelper.formatReviewCountLabel(reviewCount));
        }
    }

    private void loadReviewStats() {
        if (product == null || product.getId() == null) return;
        ReviewStatsHelper.loadForProduct(product.getId(), stats -> {
            if (isFinishing()) return;
            ReviewStatsHelper.applyToProduct(product, stats);
            updateRatingUi(stats.avgRating, stats.count);
        });
    }

    private void setupImagePager() {
        final List<String> finalImages = collectProductImages(product);
        viewPagerImages.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPagerImages.setOffscreenPageLimit(1);
        viewPagerImages.setUserInputEnabled(finalImages.size() > 1);
        viewPagerImages.setAdapter(new ImageSliderAdapter(finalImages, true));

        // ViewPager2's inner RecyclerView must not nest-scroll vertically or parents steal swipes.
        viewPagerImages.post(() -> {
            if (viewPagerImages.getChildCount() > 0
                    && viewPagerImages.getChildAt(0) instanceof RecyclerView) {
                RecyclerView inner = (RecyclerView) viewPagerImages.getChildAt(0);
                inner.setNestedScrollingEnabled(false);
                inner.setOverScrollMode(View.OVER_SCROLL_NEVER);
                inner.setClipToPadding(false);
            }
            if (tvImageIndex != null) {
                tvImageIndex.bringToFront();
            }
        });

        if (tvImageIndex == null) return;
        if (finalImages.size() > 1) {
            tvImageIndex.setVisibility(View.VISIBLE);
            tvImageIndex.setText("1/" + finalImages.size());
            viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    tvImageIndex.setText((position + 1) + "/" + finalImages.size());
                }
            });
        } else {
            tvImageIndex.setVisibility(View.GONE);
        }
    }

    @NonNull
    private static List<String> collectProductImages(@NonNull Product product) {
        List<String> images = new ArrayList<>();
        if (product.getImages() != null) {
            for (String url : product.getImages()) {
                if (url != null && !url.trim().isEmpty() && !images.contains(url.trim())) {
                    images.add(url.trim());
                }
            }
        }
        String fallback = product.getImageUrl();
        // getImageUrl() may return images[0]; only add if list was empty or URL is distinct.
        if (fallback != null && !fallback.trim().isEmpty() && !images.contains(fallback.trim())) {
            images.add(fallback.trim());
        }
        if (product.getVariants() != null) {
            for (Product.ProductVariant v : product.getVariants()) {
                if (v == null || v.getImageUrl() == null) continue;
                String url = v.getImageUrl().trim();
                if (!url.isEmpty() && !images.contains(url)) {
                    images.add(url);
                }
            }
        }
        if (images.isEmpty()) {
            images.add("");
        }
        return images;
    }

    private void updatePriceDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        double displayPrice = product.getPrice();
        int stock = product.getStockCount();

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

    private void setupCartBadgeListener() {
        if (cartListener != null) {
            cartListener.remove();
            cartListener = null;
        }

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            cartListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("cart")
                    .addSnapshotListener((value, error) -> {
                        if (value != null && tvCartBadgeHeader != null) {
                            int count = 0;
                            for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                                if (CartHelper.isValidCartDocument(doc)) {
                                    count++;
                                }
                            }
                            updateCartBadge(count);
                        }
                    });
        } else {
            updateCartBadge(com.example.healthup.util.GuestCartManager.getInstance(this).getItems().size());
        }
    }

    private void updateCartBadge(int count) {
        if (tvCartBadgeHeader == null) return;
        if (count > 0) {
            tvCartBadgeHeader.setVisibility(View.VISIBLE);
            tvCartBadgeHeader.setText(String.valueOf(count));
        } else {
            tvCartBadgeHeader.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        if (cartListener != null) {
            cartListener.remove();
        }
        super.onDestroy();
    }

    private void setupExpandableSections() {
        String currentLang = LocaleHelper.getLanguage(this);
        
        // Dịch nội dung chi tiết
        if ("en".equals(currentLang)) {
            TranslationManager.translate("Thành phần chính", "en", t -> setupSection(findViewById(R.id.section_ingredients), t, null));
            TranslationManager.translate(product.getIngredients(), "en", t -> setupSection(findViewById(R.id.section_ingredients), null, t));

            TranslationManager.translate("Giá trị dinh dưỡng", "en", t -> setupSection(findViewById(R.id.section_nutrition), t, null));
            
            TranslationManager.translate("Hướng dẫn sử dụng", "en", t -> setupSection(findViewById(R.id.section_usage), t, null));
            TranslationManager.translate(product.getUsage(), "en", t -> setupSection(findViewById(R.id.section_usage), null, t));

            TranslationManager.translate("Nguồn gốc xuất xứ", "en", t -> setupSection(findViewById(R.id.section_origin), t, null));
            TranslationManager.translate(product.getOrigin(), "en", t -> setupSection(findViewById(R.id.section_origin), null, t));
        } else {
            setupSection(findViewById(R.id.section_ingredients), "Thành phần chính", product.getIngredients());
            setupSection(findViewById(R.id.section_usage), "Hướng dẫn sử dụng", product.getUsage());
            setupSection(findViewById(R.id.section_origin), "Nguồn gốc xuất xứ", product.getOrigin());
        }

        String nutritionContent = product.getDisplayNutrition();
        if ("en".equals(currentLang) && !TextUtils.isEmpty(nutritionContent)) {
            TranslationManager.translate(nutritionContent, "en", t -> setupSection(findViewById(R.id.section_nutrition), "Giá trị dinh dưỡng", t));
        } else {
            setupSection(findViewById(R.id.section_nutrition), "Giá trị dinh dưỡng", nutritionContent);
        }
    }

    private void setupSection(View sectionView, String title, String content) {
        if (sectionView == null) return;

        TextView tvTitle = sectionView.findViewById(R.id.tv_section_title);
        TextView tvContent = sectionView.findViewById(R.id.tv_section_content);
        View btnExpand = sectionView.findViewById(R.id.btn_expand);
        ImageView ivArrow = sectionView.findViewById(R.id.iv_expand_arrow);

        if (title != null && tvTitle != null) tvTitle.setText(title);
        if (tvContent != null) {
            if (!TextUtils.isEmpty(content)) {
                tvContent.setText(content.trim());
            } else {
                tvContent.setText(R.string.detail_info_updating);
            }
        }

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
        recommendationAdapter = new ProductAdapter(new ArrayList<>(), new ProductAdapter.OnProductClickListener() {
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
                VariantBottomSheetFragment bottomSheet = VariantBottomSheetFragment.newInstance(p, (variant, quantity) ->
                        addToCartForProduct(p, variant, quantity));
                bottomSheet.show(getSupportFragmentManager(), "VariantSelectionRecommend");
            }
            @Override
            public void onFavoriteClick(Product p) {
                toggleFavoriteForProduct(p);
            }
        });

        GuestWishlistUiHelper.applyTo(recommendationAdapter);
        rvRecommendations.setLayoutManager(new GridLayoutManager(this, 2));
        rvRecommendations.setAdapter(recommendationAdapter);

        fetchRandomRecommendations();
        fetchReviews();
    }

    private void fetchRandomRecommendations() {
        FirestoreManager.getInstance().getProductsCollection()
                .limit(30).get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> pool = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        Product p = Product.fromDocument(doc);
                        if (p != null && !doc.getId().equals(product.getId())) {
                            pool.add(p);
                        }
                    }
                    Collections.shuffle(pool);
                    List<Product> selected = new ArrayList<>(pool.subList(0, Math.min(6, pool.size())));
                    syncRecommendationFavoriteState(selected);
                })
                .addOnFailureListener(e ->
                        Log.e("ProductDetail", "Lỗi tải gợi ý: " + e.getMessage()));
    }

    private void syncRecommendationFavoriteState(List<Product> recommendations) {
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            for (Product p : recommendations) p.setFavorite(false);
            recommendationAdapter.updateData(recommendations);
            GuestWishlistUiHelper.applyTo(recommendationAdapter);
            ReviewStatsHelper.enrichProducts(recommendations, () -> {
                if (!isFinishing() && recommendationAdapter != null) {
                    recommendationAdapter.notifyDataSetChanged();
                }
            });
            return;
        }
        WishlistManager.loadFavoriteIds(uid, (Set<String> ids) -> {
            if (isFinishing()) return;
            WishlistManager.applyFavoriteState(recommendations, ids);
            recommendationAdapter.updateData(recommendations);
            ReviewStatsHelper.enrichProducts(recommendations, () -> {
                if (!isFinishing() && recommendationAdapter != null) {
                    recommendationAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    private void fetchReviews() {
        List<com.example.models.Review> reviews = new ArrayList<>();
        ProductReviewEntryAdapter reviewAdapter = new ProductReviewEntryAdapter(reviews);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(reviewAdapter);

        FirestoreManager.getInstance().getProductsCollection()
                .document(product.getId())
                .collection("reviews")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    ReviewStatsHelper.Stats stats = ReviewStatsHelper.computeFromSnapshot(queryDocumentSnapshots);
                    ReviewStatsHelper.applyToProduct(product, stats);
                    updateRatingUi(stats.avgRating, stats.count);

                    List<com.example.models.Review> all = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        com.example.models.Review r = doc.toObject(com.example.models.Review.class);
                        if (r != null) all.add(r);
                    }
                    all.sort((r1, r2) -> {
                        long t1 = r1.getCreatedAt() != null ? r1.getCreatedAt().getTime() : 0;
                        long t2 = r2.getCreatedAt() != null ? r2.getCreatedAt().getTime() : 0;
                        return Long.compare(t2, t1);
                    });

                    reviews.clear();
                    reviews.addAll(all.subList(0, Math.min(5, all.size())));
                    reviewAdapter.notifyDataSetChanged();
                    if (reviews.isEmpty()) {
                        View noReviews = findViewById(R.id.tv_no_reviews);
                        if (noReviews != null) noReviews.setVisibility(View.VISIBLE);
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
                if (p.getId().equals(product.getId())) {
                    updateWishlistIcon();
                }
                if (recommendationAdapter != null) {
                    recommendationAdapter.notifyDataSetChanged();
                }
            }
        });

        if (p.getId().equals(product.getId())) {
            product.setFavorite(p.isFavorite());
            updateWishlistIcon();
        }
        if (recommendationAdapter != null) {
            recommendationAdapter.notifyDataSetChanged();
        }
    }

    private void updateWishlistIcon() {
        boolean isFavorite = product.isFavorite();
        btnWishlist.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        int tintColor = ContextCompat.getColor(this, isFavorite ? R.color.error : R.color.primary_default);
        btnWishlist.setImageTintList(ColorStateList.valueOf(tintColor));
    }

    private void showVariantSelection(boolean isBuyNow) {
        VariantBottomSheetFragment bottomSheet = VariantBottomSheetFragment.newInstance(product, isBuyNow, (variant, quantity) -> {
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
        com.example.models.CartItem buyNowItem = new com.example.models.CartItem(
                product.getId(), product, quantity, null);
        if (selectedVariant != null) {
            buyNowItem.setVariantId(selectedVariant.getId());
            buyNowItem.setVariantName(selectedVariant.getName());
            buyNowItem.setPrice(selectedVariant.getPrice());
        } else {
            buyNowItem.setPrice(product.getPrice());
        }

        ArrayList<com.example.models.CartItem> checkoutItems = new ArrayList<>();
        checkoutItems.add(buyNowItem);

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (user == null) {
            com.example.healthup.util.CheckoutIntentHelper.savePendingCheckout(this, checkoutItems);
            intent.putExtra("navigate_to", "phone_verification");
            startActivity(intent);
            return;
        }

        buyNowItem.setUserId(user.getUid());
        com.example.healthup.util.PhoneVerifiedHelper.requireForCheckout(
                new com.example.healthup.util.PhoneVerifiedHelper.Callback() {
                    @Override
                    public void onVerified() {
                        intent.putExtra("navigate_to", "checkout");
                        intent.putExtra("checkout_items", checkoutItems);
                        startActivity(intent);
                    }

                    @Override
                    public void onNeedPhoneVerification() {
                        Toast.makeText(
                                ProductDetailActivity.this,
                                R.string.checkout_need_phone_verified,
                                Toast.LENGTH_LONG
                        ).show();
                        com.example.healthup.util.CheckoutIntentHelper.savePendingCheckout(
                                ProductDetailActivity.this, checkoutItems);
                        intent.putExtra("navigate_to", "phone_verification");
                        startActivity(intent);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(
                                ProductDetailActivity.this,
                                R.string.register_error_generic,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void addToCart(int quantity) {
        addToCartForProduct(product, selectedVariant, quantity);
    }

    private void addToCartForProduct(Product targetProduct, Product.ProductVariant variant, int quantity) {
        CartHelper.addToCart(this, targetProduct, variant, quantity);
    }

    private void setupToolbarScroll() {
        NestedScrollView scrollView = findViewById(R.id.product_detail_scroll);
        View toolbarHeader = findViewById(R.id.layout_toolbar_header);
        if (scrollView == null || toolbarHeader == null) return;

        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float threshold = 200 * getResources().getDisplayMetrics().density;
            float alpha = Math.min(1f, (float) scrollY / threshold);

            int baseColor = ContextCompat.getColor(this, R.color.neutral_cream);
            int colorWithAlpha = ColorUtils.setAlphaComponent(baseColor, (int) (alpha * 255));
            toolbarHeader.setBackgroundColor(colorWithAlpha);

            updateToolbarIconsStyle(alpha > 0.8f);
        });
    }

    private void updateToolbarIconsStyle(boolean isCollapsed) {
        int iconTint = ContextCompat.getColor(this, isCollapsed ? R.color.text_dark : R.color.white);
        int bgRes = isCollapsed ? android.R.color.transparent : R.drawable.bg_circle_dark_translucent;

        if (btnBack != null) {
            btnBack.setBackgroundResource(bgRes);
            btnBack.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
        }
        if (btnShare != null) {
            btnShare.setBackgroundResource(bgRes);
            btnShare.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
        }
        if (btnCartHeader != null) {
            btnCartHeader.setBackgroundResource(bgRes);
            int cartTint = isCollapsed ? ContextCompat.getColor(this, R.color.primary_default) : iconTint;
            btnCartHeader.setImageTintList(android.content.res.ColorStateList.valueOf(cartTint));
        }
        if (btnWishlist != null) {
            btnWishlist.setBackgroundResource(bgRes);
            if (isCollapsed) {
                updateWishlistIcon(); // Restore colored heart when collapsed
            } else {
                btnWishlist.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
            }
        }
    }

    private void shareProduct() {
        if (product == null) return;

        String shareBody = "Hãy xem sản phẩm này trên HealthUp: " + product.getName() +
                "\nGiá: " + tvPrice.getText();
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "HealthUp - " + product.getName());
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Chia sẻ sản phẩm"));
    }
}
