package com.example.healthup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.GuestCartManager;
import com.example.models.CartItem;
import com.example.models.Product;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private Product product;
    private ViewPager2 viewPagerImages;
    private TextView tvImageIndex, tvName, tvPrice, tvOriginalPrice, tvDiscount, tvRating, tvReviewCount, tvSold, tvSavings, tvStock;
    private TextView tvViewAllReviews, tvViewAllRecommend;
    private ImageButton btnBack, btnShare, btnWishlist, btnCart;
    private MaterialButton btnAddCart, btnBuyNow;
    private RecyclerView rvRecommendations, rvReviews;
    private ProductAdapter recommendationAdapter;
    private Product.ProductVariant selectedVariant;

    private TextView tvCartBadge;
    private ListenerRegistration cartListener;
    private final BroadcastReceiver guestCartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshGuestCartBadge();
        }
    };

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

    @Override
    protected void onResume() {
        super.onResume();
        setupCartBadgeListener();
        
        IntentFilter filter = new IntentFilter(GuestCartManager.ACTION_GUEST_CART_CHANGED);
        ContextCompat.registerReceiver(this, guestCartReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cartListener != null) {
            cartListener.remove();
            cartListener = null;
        }
        try {
            unregisterReceiver(guestCartReceiver);
        } catch (Exception ignored) {}
    }

    private void setupCartBadgeListener() {
        if (cartListener != null) {
            cartListener.remove();
            cartListener = null;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            cartListener = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("cart")
                    .addSnapshotListener((value, error) -> {
                        if (value != null) {
                            updateCartBadge(value.size());
                        }
                    });
        } else {
            refreshGuestCartBadge();
        }
    }

    private void refreshGuestCartBadge() {
        List<CartItem> items = GuestCartManager.getInstance(this).getItems();
        updateCartBadge(items.size());
    }

    private void updateCartBadge(int count) {
        if (tvCartBadge == null) return;
        if (count > 0) {
            tvCartBadge.setVisibility(View.VISIBLE);
            tvCartBadge.setText(String.valueOf(count));
        } else {
            tvCartBadge.setVisibility(View.GONE);
        }
    }

    private void fetchProductDetails(String productId) {
        FirestoreManager.getInstance().getProductsCollection().document(productId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    product = Product.fromDocument(documentSnapshot);
                    if (product != null) {
                        showProductUi();
                    } else {
                        Toast.makeText(this, "Sản phẩm không tồn tại", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void showProductUi() {
        if (product == null) return;
        initViews();
        setupProductInfo();
        setupVariants();
        setupExpandableSections();
        setupRecommendations();
        fetchReviews();
        updateWishlistIcon();
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
        tvViewAllReviews = findViewById(R.id.tv_view_all_reviews);
        tvViewAllRecommend = findViewById(R.id.tv_view_all_recommend);
        btnShare = findViewById(R.id.btn_share);
        btnWishlist = findViewById(R.id.btn_wishlist);
        btnCart = findViewById(R.id.btn_cart);
        tvCartBadge = findViewById(R.id.tv_cart_badge);
        btnAddCart = findViewById(R.id.btn_detail_add_cart);
        btnBuyNow = findViewById(R.id.btn_buy_now);
        rvRecommendations = findViewById(R.id.rv_detail_recommendations);
        rvReviews = findViewById(R.id.rv_reviews);

        btnBack.setOnClickListener(v -> finish());
        btnCart.setOnClickListener(v -> navigateToCart());
        tvViewAllReviews.setOnClickListener(v -> openAllReviews());
        tvViewAllRecommend.setOnClickListener(v -> {
            // Logic xem tất cả gợi ý
        });
        
        btnShare.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, product.getName());
            intent.putExtra(Intent.EXTRA_TEXT, "Xem sản phẩm này trên HealthUp: " + product.getName());
            startActivity(Intent.createChooser(intent, "Chia sẻ sản phẩm"));
        });

        btnWishlist.setOnClickListener(v -> toggleFavorite());

        btnAddCart.setOnClickListener(v -> showVariantSelection(false));
        btnBuyNow.setOnClickListener(v -> showVariantSelection(true));

        View btnChat = findViewById(R.id.btn_chat);
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> openProductChat());
        }
    }

    private void navigateToCart() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to", "cart_tab");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openAllReviews() {
        Intent intent = new Intent(this, ProductReviewsActivity.class);
        intent.putExtra("productId", product.getId());
        intent.putExtra("productName", product.getName());
        startActivity(intent);
    }

    private void openProductChat() {
        startActivity(ChatActivity.buyerIntent(this));
    }

    private void setupProductInfo() {
        tvName.setText(product.getName());
        updatePriceDisplay();
        tvRating.setText(String.valueOf(product.getRating()));
        tvReviewCount.setText(product.getReviewCount() + " đánh giá");
        tvSold.setText("Đã bán " + product.getSoldCount() + "+");
        tvStock.setText("Kho: " + product.getStockCount());

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            ProductImageAdapter adapter = new ProductImageAdapter(product.getImages());
            viewPagerImages.setAdapter(adapter);
            tvImageIndex.setText("1/" + product.getImages().size());
            viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    tvImageIndex.setText((position + 1) + "/" + product.getImages().size());
                }
            });
        }
    }

    private void setupVariants() {
        LinearLayout layoutVariants = findViewById(R.id.layout_variants);
        View dividerVariants = findViewById(R.id.divider_variants);
        
        Map<String, List<Product.ProductVariant>> grouped = product.getGroupedVariants();

        if (!grouped.isEmpty()) {
            layoutVariants.setVisibility(View.VISIBLE);
            dividerVariants.setVisibility(View.VISIBLE);
            layoutVariants.removeAllViews();

            for (Map.Entry<String, List<Product.ProductVariant>> entry : grouped.entrySet()) {
                String groupName = entry.getKey();
                List<Product.ProductVariant> variants = entry.getValue();

                View groupView = getLayoutInflater().inflate(R.layout.layout_variant_group, layoutVariants, false);
                TextView tvLabel = groupView.findViewById(R.id.tv_group_label);
                com.google.android.material.chip.ChipGroup chipGroup = groupView.findViewById(R.id.chip_group_variants);
                
                tvLabel.setText(groupName);
                chipGroup.removeAllViews();

                for (Product.ProductVariant variant : variants) {
                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) getLayoutInflater()
                            .inflate(R.layout.item_variant_chip, chipGroup, false);
                    chip.setText(variant.getName());
                    chip.setOnClickListener(v -> {
                        selectVariantInGroup(chipGroup, variant);
                    });
                    chipGroup.addView(chip);
                }
                
                layoutVariants.addView(groupView);
                
                // Select first variant in each group by default
                if (!variants.isEmpty()) {
                    selectVariantInGroup(chipGroup, variants.get(0));
                }
            }
        } else {
            layoutVariants.setVisibility(View.GONE);
            dividerVariants.setVisibility(View.GONE);
            selectedVariant = null;
            updatePriceDisplay();
        }
    }

    private void selectVariantInGroup(com.google.android.material.chip.ChipGroup group, Product.ProductVariant variant) {
        // Find the chip and select it
        for (int i = 0; i < group.getChildCount(); i++) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) group.getChildAt(i);
            boolean isThis = chip.getText().toString().equals(variant.getName());
            updateVariantChipStyle(chip, isThis);
            if (isThis) chip.setChecked(true);
        }
        
        // Update price based on this selection (if it has price)
        if (variant.getPrice() > 0) {
            selectedVariant = variant;
            updatePriceDisplay();
            tvStock.setText("Kho: " + variant.getStock());
        }
    }

    private void updateVariantChipStyle(com.google.android.material.chip.Chip chip, boolean selected) {
        if (selected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(ContextCompat.getColor(this, R.color.white));
            chip.setChipStrokeWidth(0);
        } else {
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
            chip.setChipStrokeWidth(1);
            chip.setChipStrokeColorResource(android.R.color.darker_gray);
        }
    }

    private void updatePriceDisplay() {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        double currentPrice = selectedVariant != null ? selectedVariant.getPrice() : product.getPrice();
        double originalPrice = selectedVariant != null ? selectedVariant.getOriginalPrice() : product.getOriginalPrice();

        tvPrice.setText(formatter.format(currentPrice) + "đ");
        
        if (originalPrice > currentPrice) {
            tvOriginalPrice.setVisibility(View.VISIBLE);
            tvOriginalPrice.setText(formatter.format(originalPrice) + "đ");
            tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            
            int discount = (int) ((originalPrice - currentPrice) / originalPrice * 100);
            tvDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText("-" + discount + "%");
            
            tvSavings.setVisibility(View.VISIBLE);
            tvSavings.setText("Tiết kiệm " + formatter.format(originalPrice - currentPrice) + "đ");
        } else {
            tvOriginalPrice.setVisibility(View.GONE);
            tvDiscount.setVisibility(View.GONE);
            tvSavings.setVisibility(View.GONE);
        }
    }

    private void setupExpandableSections() {
        setupSection(findViewById(R.id.section_ingredients), "Thành phần", product.getIngredients());
        setupSection(findViewById(R.id.section_usage), "Hướng dẫn sử dụng", product.getUsage());
        setupSection(findViewById(R.id.section_origin), "Nguồn gốc", product.getOrigin());
    }

    private void setupSection(View sectionView, String title, String content) {
        if (content == null || content.isEmpty()) {
            sectionView.setVisibility(View.GONE);
            return;
        }
        TextView tvTitle = sectionView.findViewById(R.id.tv_section_title);
        TextView tvContent = sectionView.findViewById(R.id.tv_section_content);
        ImageView ivChevron = sectionView.findViewById(R.id.iv_expand_arrow);

        tvTitle.setText(title);
        tvContent.setText(content);

        sectionView.setOnClickListener(v -> {
            if (tvContent.getVisibility() == View.VISIBLE) {
                tvContent.setVisibility(View.GONE);
                ivChevron.setRotation(0);
            } else {
                tvContent.setVisibility(View.VISIBLE);
                ivChevron.setRotation(180);
            }
        });
    }

    private void setupRecommendations() {
        rvRecommendations.setLayoutManager(new GridLayoutManager(this, 2));
        recommendationAdapter = new ProductAdapter(new ArrayList<>(), new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product p) {
                Intent intent = new Intent(ProductDetailActivity.this, ProductDetailActivity.class);
                intent.putExtra("product", p);
                startActivity(intent);
            }

            @Override
            public void onAddToCart(Product p) {
                addToCartForProduct(p, null, 1);
            }

            @Override
            public void onFavoriteClick(Product p) {
                toggleFavoriteForProduct(p);
            }
        });
        rvRecommendations.setAdapter(recommendationAdapter);
        fetchRandomRecommendations();
    }

    private void toggleFavoriteForProduct(Product p) {
        p.setFavorite(!p.isFavorite());
        // Ở đây có thể thêm logic lưu vào Firestore/WishlistManager
        recommendationAdapter.notifyDataSetChanged();
    }

    private void fetchRandomRecommendations() {
        FirestoreManager.getInstance().getProductsCollection().limit(4).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Product> list = queryDocumentSnapshots.toObjects(Product.class);
                    recommendationAdapter.updateData(list);
                });
    }

    private void fetchReviews() {
        // Logic tải đánh giá
    }

    private void toggleFavorite() {
        product.setFavorite(!product.isFavorite());
        updateWishlistIcon();
        // Cập nhật lên Firebase nếu cần
    }

    private void updateWishlistIcon() {
        btnWishlist.setImageResource(product.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        btnWishlist.setColorFilter(ContextCompat.getColor(this, product.isFavorite() ? R.color.primary_default : R.color.white));
    }

    private void showVariantSelection(boolean isBuyNow) {
        VariantBottomSheetFragment bottomSheet = VariantBottomSheetFragment.newInstance(product, isBuyNow, (variant, qty) -> {
            if (isBuyNow) {
                // Logic mua ngay
            } else {
                addToCartForProduct(product, variant, qty);
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "VariantSelection");
    }

    private void addToCartForProduct(Product product, Product.ProductVariant variant, int qty) {
        com.example.healthup.util.CartHelper.addToCart(this, product, variant, qty, new com.example.healthup.util.CartHelper.CartCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProductDetailActivity.this, "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(ProductDetailActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
