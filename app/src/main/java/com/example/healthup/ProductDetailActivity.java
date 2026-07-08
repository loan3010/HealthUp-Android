package com.example.healthup;


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
    private TextView tvImageIndex, tvName, tvPrice, tvOriginalPrice, tvDiscount, tvRating, tvReviewCount, tvSold, tvSavings, tvStock;
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
        btnWishlist.setOnClickListener(v -> toggleFavorite());
        if (btnCartHeader != null) {
            btnCartHeader.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", "cart_tab");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        updatePriceDisplay();
        setupCartBadgeListener();


        tvRating.setText(String.valueOf(product.getRating()));
        tvReviewCount.setText(product.getReviewCount() + " đánh giá");
        tvSold.setText("Đã bán " + product.getSoldCount() + "+");


        updateWishlistIcon();


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
                                // FIX: dùng chung logic hợp lệ hoá với CartFragment.parseCartItem()
                                // và với badge ở Thanh điều hướng (MainActivity), để số lượng hiển
                                // thị ở đây luôn khớp với tiêu đề "Giỏ hàng (n)".
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




    // FIX ROOT CAUSE (bug "Có thể bạn quan tâm không hiển thị sản phẩm"):
    // ProductAdapter luôn tự COPY list truyền vào constructor ra 1 list nội bộ riêng
    // (xem ProductAdapter constructor). Code cũ tạo list rỗng, đưa cho adapter (adapter copy
    // ra 1 list rỗng riêng), rồi lại mutate (clear+addAll) đúng cái list rỗng BAN ĐẦU đó và
    // gọi notifyDataSetChanged() — nhưng adapter đang cầm 1 list hoàn toàn khác nên không có
    // gì thay đổi để vẽ lại => RecyclerView mãi mãi trống.
    // Sửa: không giữ list dùng chung nữa, luôn gọi recommendationAdapter.updateData(list mới)
    // để thay đúng list mà adapter đang cầm.
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
                // FIX (yêu cầu #3): luôn hiển thị popup chọn số lượng/phân loại, kể cả khi
                // sản phẩm không có phân loại, để đồng nhất với nút "Thêm vào giỏ hàng" chính
                // và không còn tình trạng bấm "+" là cộng thẳng 1 sản phẩm không cho chọn gì.
                VariantBottomSheetFragment bottomSheet = VariantBottomSheetFragment.newInstance(p, (variant, quantity) ->
                        addToCartForProduct(p, variant, quantity));
                bottomSheet.show(getSupportFragmentManager(), "VariantSelectionRecommend");
            }
            @Override
            public void onFavoriteClick(Product p) {
                toggleFavoriteForProduct(p);
            }
        });


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
            return;
        }
        WishlistManager.loadFavoriteIds(uid, (Set<String> ids) -> {
            if (isFinishing()) return;
            WishlistManager.applyFavoriteState(recommendations, ids);
            recommendationAdapter.updateData(recommendations);
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


        if (user != null) {
            buyNowItem.setUserId(user.getUid());
            intent.putExtra("navigate_to", "checkout");
            intent.putExtra("checkout_items", checkoutItems);
        } else {
            com.example.healthup.util.CheckoutIntentHelper.savePendingCheckout(this, checkoutItems);
            intent.putExtra("navigate_to", "phone_verification");
        }


        startActivity(intent);
    }




    private void addToCart(int quantity) {
        addToCartForProduct(product, selectedVariant, quantity);
    }




    private void addToCartForProduct(Product targetProduct, Product.ProductVariant variant, int quantity) {
        CartHelper.addToCart(this, targetProduct, variant, quantity);
    }
}