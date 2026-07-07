package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.healthup.databinding.FragmentOrderListBinding; 
import com.example.models.Order;
import com.example.models.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.models.CartItem;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class OrderListFragment extends Fragment {
    private FragmentOrderListBinding binding;
    private String tabFilter = "all";
    private List<Product> allRecommendProducts;
    private List<Product> displayedRecommendProducts;
    private ProductAdapter recommendAdapter;

    public static OrderListFragment newInstance(String status) {
        OrderListFragment fragment = new OrderListFragment();
        Bundle args = new Bundle();
        args.putString("STATUS_KEY", status);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabFilter = getArguments().getString("STATUS_KEY", "all");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOrderListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupOrderList();
        setupRecommendList();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupOrderList();
    }

    private void setupOrderList() {
        if (binding == null) return;
        
        final String filter = tabFilter.toLowerCase().trim();

        FirebaseManager.getInstance().getOrders().addOnSuccessListener(queryDocumentSnapshots -> {
            if (binding == null) return;
            
            List<Order> filteredOrders = new ArrayList<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                try {
                    Order o = doc.toObject(Order.class);
                    if (o == null) continue;
                    o.setId(doc.getId());

                    String orderStatus = o.getStatus().toLowerCase().trim();
                    
                    if ("all".equals(filter)) {
                        filteredOrders.add(o);
                    } else if ("delivered".equals(filter)) {
                        if ("delivered".equals(orderStatus) || "returned".equals(orderStatus) || "refunded".equals(orderStatus) || "reshipped".equals(orderStatus)) {
                            filteredOrders.add(o);
                        }
                    } else if ("returned".equals(filter)) {
                        if ("returned".equals(orderStatus) || "refunded".equals(orderStatus) || "reshipped".equals(orderStatus)) {
                            filteredOrders.add(o);
                        }
                    } else {
                        if (orderStatus.equals(filter)) {
                            filteredOrders.add(o);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("OrderListFragment", "Lỗi nạp đơn hàng: " + doc.getId(), e);
                }
            }

            // Sắp xếp đơn hàng theo thời gian: Mới nhất lên đầu (Dựa trên updatedAt hoặc createdAt)
            Collections.sort(filteredOrders, (o1, o2) -> {
                long t1 = 0;
                if (o1.getUpdatedAt() != null) t1 = o1.getUpdatedAt().getSeconds();
                else if (o1.getCreatedAt() != null) t1 = o1.getCreatedAt().getSeconds();

                long t2 = 0;
                if (o2.getUpdatedAt() != null) t2 = o2.getUpdatedAt().getSeconds();
                else if (o2.getCreatedAt() != null) t2 = o2.getCreatedAt().getSeconds();

                return Long.compare(t2, t1); // Đảo ngược t2, t1 để lấy DESC (mới nhất lên trước)
            });

            if (filteredOrders.isEmpty()) {
                binding.rvOrders.setVisibility(View.GONE);
                binding.lnEmptyState.setVisibility(View.VISIBLE);
            } else {
                binding.rvOrders.setVisibility(View.VISIBLE);
                binding.lnEmptyState.setVisibility(View.GONE);
                binding.rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.rvOrders.setAdapter(new OrderAdapter(getContext(), filteredOrders));
            }

            boolean showRecommend = "pending".equals(filter) || "confirmed".equals(filter) || "shipping".equals(filter) || filteredOrders.isEmpty();
            binding.lnRecommend.setVisibility(showRecommend ? View.VISIBLE : View.GONE);
        }).addOnFailureListener(e -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Lỗi tải đơn hàng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) ->
                performAddToCart(product, variant, quantity));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }

    private void performAddToCart(Product product, Product.ProductVariant variant, int quantity) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        String productId = product.getId();
        String variantId = (variant != null) ? variant.getId() : null;

        com.google.firebase.firestore.CollectionReference cartRef =
                com.example.healthup.firebase.FirestoreManager.getInstance().getFirestore()
                        .collection("users").document(userId).collection("cart");

        cartRef.whereEqualTo("productId", productId)
                .whereEqualTo("variantId", variantId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Long currentQtyLong = doc.getLong("quantity");
                        long currentQty = (currentQtyLong != null) ? currentQtyLong : 0;
                        doc.getReference().update("quantity", currentQty + quantity, "updatedAt", com.google.firebase.Timestamp.now());
                    } else {
                        CartItem newItem = new CartItem(productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                            newItem.setOriginalPrice(variant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                            newItem.setOriginalPrice(product.getOriginalPrice());
                        }
                        newItem.setUpdatedAt(com.google.firebase.Timestamp.now());
                        cartRef.add(newItem);
                    }
                    Toast.makeText(getContext(), "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupRecommendList() {
        FirebaseManager.getInstance().getProducts().addOnSuccessListener(queryDocumentSnapshots -> {
            if (binding == null) return;
            
            allRecommendProducts = new ArrayList<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                try {
                    Product p = doc.toObject(Product.class);
                    if (p != null) {
                        p.setId(doc.getId());
                        allRecommendProducts.add(p);
                    }
                } catch (Exception e) {
                    android.util.Log.e("OrderListFragment", "Lỗi nạp sản phẩm gợi ý: " + doc.getId(), e);
                }
            }
            
            displayedRecommendProducts = new ArrayList<>();
            int limit = Math.min(allRecommendProducts.size(), 4);
            for (int i = 0; i < limit; i++) {
                displayedRecommendProducts.add(allRecommendProducts.get(i));
            }

            recommendAdapter = new ProductAdapter(displayedRecommendProducts, new ProductAdapter.OnProductClickListener() {
                @Override
                public void onProductClick(Product p) {
                    android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
                    intent.putExtra("productId", p.getId());
                    startActivity(intent);
                }

                @Override
                public void onAddToCart(Product p) {
                    com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        Toast.makeText(getContext(), "Vui lòng đăng nhập để thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (p.isHasVariants()) {
                        showVariantSheet(p);
                    } else {
                        performAddToCart(p, null, 1);
                    }
                }

                @Override
                public void onFavoriteClick(Product p) {
                    p.setFavorite(!p.isFavorite());
                    recommendAdapter.notifyDataSetChanged();
                }
            });
            binding.rvRecommend.setLayoutManager(new GridLayoutManager(getContext(), 2));
            binding.rvRecommend.setAdapter(recommendAdapter);

            if (allRecommendProducts.size() <= 4) {
                binding.btnMoreRecommend.setVisibility(View.GONE);
            } else {
                binding.btnMoreRecommend.setVisibility(View.VISIBLE);
            }

            binding.btnMoreRecommend.setOnClickListener(v -> {
                binding.btnMoreRecommend.setVisibility(View.GONE);
                int currentSize = displayedRecommendProducts.size();
                for (int i = currentSize; i < allRecommendProducts.size(); i++) {
                    displayedRecommendProducts.add(allRecommendProducts.get(i));
                }
                recommendAdapter.updateData(new ArrayList<>(displayedRecommendProducts));
            });

            binding.tvViewAllRecommend.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    com.google.android.material.bottomnavigation.BottomNavigationView navView = mainActivity.findViewById(R.id.bottom_navigation);
                    navView.setSelectedItemId(R.id.nav_category);

                    ProductListFragment fragment = new ProductListFragment();
                    Bundle args = new Bundle();
                    args.putString("category", "Tất cả");
                    fragment.setArguments(args);

                    mainActivity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit();
                }
            });
        });
    }
}
