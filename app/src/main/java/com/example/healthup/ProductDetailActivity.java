package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityProductDetailBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ProductDetailActivity extends AppCompatActivity {
    private ActivityProductDetailBinding binding;
    private FirestoreManager firestoreManager;
    private String productId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firestoreManager = FirestoreManager.getInstance();
        productId = getIntent().getStringExtra("product_id");

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (productId != null) {
            loadProductDetail();
        }

        binding.btnAddToCartDetail.setOnClickListener(v -> {
            Toast.makeText(this, "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
        });

        binding.btnBuyNow.setOnClickListener(v -> {
            Toast.makeText(this, "Mua ngay", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProductDetail() {
        firestoreManager.getProductDetail(productId, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                try {
                    Product product = task.getResult().toObject(Product.class);
                    if (product != null) {
                        displayProduct(product);
                    }
                } catch (Exception e) {
                    android.util.Log.e("ProductDetail", "Lỗi nạp chi tiết sản phẩm: " + productId, e);
                    Toast.makeText(this, "Dữ liệu sản phẩm bị lỗi", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Không thể tải thông tin sản phẩm", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayProduct(Product product) {
        binding.tvProductName.setText(product.getName());
        DecimalFormat df = new DecimalFormat("#,###đ");
        binding.tvProductPrice.setText(df.format(product.getPrice()));
        
        if (product.getOriginalPrice() > product.getPrice()) {
            binding.tvOriginalPrice.setVisibility(View.VISIBLE);
            binding.tvOriginalPrice.setText(df.format(product.getOriginalPrice()));
            int discount = (int) ((1 - product.getPrice() / product.getOriginalPrice()) * 100);
            binding.tvDiscount.setText("-" + discount + "%");
            binding.tvDiscount.setVisibility(View.VISIBLE);
            
            double savings = product.getOriginalPrice() - product.getPrice();
            binding.tvSavings.setText("Tiết kiệm " + df.format(savings));
            binding.tvSavings.setVisibility(View.VISIBLE);
        } else {
            binding.tvOriginalPrice.setVisibility(View.GONE);
            binding.tvDiscount.setVisibility(View.GONE);
            binding.tvSavings.setVisibility(View.GONE);
        }

        binding.tvRating.setText(product.getStars() != null ? product.getStars() : "0.0");
        binding.tvSoldCountDetail.setText("Đã bán " + (product.getSoldCount() > 0 ? product.getSoldCount() : product.getSold()) + "+");
        binding.tvIngredients.setText(product.getDescription());
        
        // Image loading
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            ProductImageAdapter imageAdapter = new ProductImageAdapter(product.getImages());
            binding.vpProductImages.setAdapter(imageAdapter);
            
            binding.tvImageIndicator.setText("1/" + product.getImages().size());
            binding.vpProductImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    binding.tvImageIndicator.setText((position + 1) + "/" + product.getImages().size());
                }
            });
        }

        loadRecommendedProducts(product.getCat());
    }

    private void loadRecommendedProducts(String category) {
        binding.rvRecommended.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        firestoreManager.getProductsByCategory(category, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<Product> recommended = new ArrayList<>();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        Product p = doc.toObject(Product.class);
                        p.setId(doc.getId());
                        recommended.add(p);
                    } catch (Exception e) {
                        android.util.Log.e("ProductDetail", "Lỗi nạp sản phẩm gợi ý: " + doc.getId(), e);
                    }
                }
                ProductAdapter adapter = new ProductAdapter(recommended, true);
                binding.rvRecommended.setAdapter(adapter);
            }
        });
    }
}
