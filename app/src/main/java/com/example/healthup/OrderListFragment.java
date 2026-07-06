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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

            // Sắp xếp lại phía client (dù server đã order by, nhưng có thể cần thiết nếu logic phức tạp)
            Collections.sort(filteredOrders, (o1, o2) -> {
                long t1 = (o1.getUpdatedAt() != null) ? o1.getUpdatedAt().getSeconds() : 0;
                long t2 = (o2.getUpdatedAt() != null) ? o2.getUpdatedAt().getSeconds() : 0;
                return Long.compare(t2, t1);
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
                    // Reuse existing logic from HomeFragment if needed, or simple toast for demo
                    Toast.makeText(getContext(), "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
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
