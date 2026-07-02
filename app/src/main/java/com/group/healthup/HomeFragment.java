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
import com.google.firebase.firestore.DocumentSnapshot;
import com.group.healthup.R;
import com.group.adapters.ProductAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Product;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ProductAdapter.OnProductClickListener {

    private RecyclerView rvHomeProducts;
    private ProductAdapter productAdapter;
    private List<Product> productList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(view);
        fetchProducts();
        return view;
    }

    private void initViews(View view) {
        rvHomeProducts = view.findViewById(R.id.rv_home_products);
        productAdapter = new ProductAdapter(productList, this);
        rvHomeProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvHomeProducts.setAdapter(productAdapter);
    }

    private void fetchProducts() {
        FirestoreManager.getInstance().getProductsCollection()
                .limit(10) // Lấy 10 sản phẩm mới nhất hoặc nổi bật
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
    public void onProductClick(Product product) {
        android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
        intent.putExtra("product", product);
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
                        long currentQty = doc.getLong("quantity");
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
        boolean newFavoriteState = !product.isFavorite();
        product.setFavorite(newFavoriteState);
        productAdapter.notifyDataSetChanged();

        FirestoreManager.getInstance().getProductsCollection()
                .document(product.getId())
                .update("favorite", newFavoriteState)
                .addOnFailureListener(e -> {
                    product.setFavorite(!newFavoriteState);
                    productAdapter.notifyDataSetChanged();
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
