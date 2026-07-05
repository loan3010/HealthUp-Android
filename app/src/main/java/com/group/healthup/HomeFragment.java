package com.group.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.group.healthup.R;
import com.group.adapters.ProductAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Category;
import com.group.models.Product;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener, CategoryAdapter.OnCategoryClickListener {

    private RecyclerView rvHomeProducts, rvCategories;
    private ProductAdapter productAdapter;
    private CategoryAdapter categoryAdapter;
    private List<Product> productList = new ArrayList<>();
    private List<Category> categoryList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        fetchCategories();
        fetchProducts();
        return view;
    }

    private void initViews(View view) {
        rvHomeProducts = view.findViewById(R.id.rvNewProducts);
        productAdapter = new ProductAdapter(productList, this);
        rvHomeProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvHomeProducts.setAdapter(productAdapter);

        rvCategories = view.findViewById(R.id.rvCategories);
        categoryAdapter = new CategoryAdapter(categoryList, this);
        rvCategories.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        view.findViewById(R.id.tvViewAllNew).setOnClickListener(v -> navigateToCategory(null));
    }

    private void fetchCategories() {
        FirestoreManager.getInstance().getFirestore().collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    categoryList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Category category = doc.toObject(Category.class);
                        if (category != null) {
                            category.setId(doc.getId());
                            categoryList.add(category);
                        }
                    }
                    if (categoryList.isEmpty()) {
                        // Dummy data if Firestore is empty
                        categoryList.add(new Category("1", "Hạt dinh dưỡng", "fruit.png"));
                        categoryList.add(new Category("2", "Granola", "fruit.png"));
                        categoryList.add(new Category("3", "Trái cây sấy", "fruit.png"));
                        categoryList.add(new Category("4", "Đồ ăn vặt", "fruit.png"));
                    }
                    categoryAdapter.notifyDataSetChanged();
                });
    }

    private void fetchProducts() {
        FirestoreManager.getInstance().getProductsCollection()
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    productList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            productList.add(product);
                        }
                    }
                    productAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Không thể tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            
            // Pass category filter to ProductListFragment
            ProductListFragment fragment = new ProductListFragment();
            if (categoryName != null) {
                Bundle args = new Bundle();
                args.putString("category", categoryName);
                fragment.setArguments(args);
            }
            mainActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    public void onProductClick(Product product) {
        if (product == null || product.getId() == null) {
            Toast.makeText(getContext(), "Dữ liệu sản phẩm không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
        // CHỈ truyền productId để tránh lỗi TransactionTooLargeException (văng app)
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }

    @Override
    public void onAddToCart(Product product) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), getString(R.string.login_required_cart), Toast.LENGTH_SHORT).show();
            return;
        }

        if (product.isHasVariants()) {
            showVariantSheet(product);
        } else {
            performAddToCart(product, null, 1);
        }
    }

    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) -> {
            performAddToCart(product, variant, quantity);
        });
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }

    private void performAddToCart(Product product, Product.ProductVariant variant, int quantity) {
        String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        String productId = product.getId();
        String variantId = (variant != null) ? variant.getId() : null;

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
                        com.group.models.CartItem newItem = new com.group.models.CartItem(
                                productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                        }
                        FirestoreManager.getInstance().getFirestore().collection("cart")
                                .add(newItem);
                    }
                    Toast.makeText(getContext(), getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onFavoriteClick(Product product) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để yêu thích sản phẩm", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean oldFavoriteState = product.isFavorite();
        boolean newFavoriteState = !oldFavoriteState;
        
        // 1. Cập nhật UI ngay lập tức
        product.setFavorite(newFavoriteState);
        productAdapter.notifyDataSetChanged();

        // 2. Gửi lệnh update duy nhất trường "favorite" bằng Map để vượt qua Rules
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("favorite", newFavoriteState);

        FirestoreManager.getInstance().getProductsCollection()
                .document(product.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Thành công
                })
                .addOnFailureListener(e -> {
                    // 3. Rollback nếu lỗi (đặc biệt là lỗi Permission Denied)
                    product.setFavorite(oldFavoriteState);
                    productAdapter.notifyDataSetChanged();
                    
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("PERMISSION_DENIED")) {
                        Toast.makeText(getContext(), "Lỗi quyền: Rules của bạn chỉ cho phép sửa field 'favorite'. Hãy kiểm tra tên field trong DB.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "Lỗi: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
