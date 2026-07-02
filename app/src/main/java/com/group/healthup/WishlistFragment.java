package com.group.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.group.healthup.R;
import com.group.adapters.ProductAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Product;
import java.util.ArrayList;
import java.util.List;

public class WishlistFragment extends Fragment implements ProductAdapter.OnProductClickListener {

    private RecyclerView rvWishlist, rvRecommendations;
    private ProductAdapter wishlistAdapter, recommendationAdapter;
    private List<Product> wishlist = new ArrayList<>();
    private List<Product> filteredWishlist = new ArrayList<>();
    private List<Product> recommendationList = new ArrayList<>();
    
    private View layoutEmpty, layoutList, cardDeleteBar, scrollChips;
    private View layoutHeaderActions, layoutSearchBar;
    private TextView tvTitle, btnEdit, btnDelete, tvRecommendationTitle, btnCancelSearch;
    private EditText etSearch;
    private ChipGroup chipGroup;
    private android.widget.ImageButton btnSearch;
    private boolean isEditMode = false;
    private String currentSearchQuery = "";
    private boolean showOnlyOnSale = false;
    private List<Product> selectedProducts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);
        initViews(view);
        setupRecyclerViews();
        fetchWishlist();
        fetchRecommendations();
        return view;
    }

    private void initViews(View view) {
        rvWishlist = view.findViewById(R.id.rv_wishlist);
        rvRecommendations = view.findViewById(R.id.rv_wishlist_recommendations);
        layoutEmpty = view.findViewById(R.id.layout_wishlist_empty);
        layoutList = view.findViewById(R.id.rv_wishlist);
        cardDeleteBar = view.findViewById(R.id.card_delete_bar);
        scrollChips = view.findViewById(R.id.scroll_chips);
        tvTitle = view.findViewById(R.id.tv_title);
        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete_selected);
        btnSearch = view.findViewById(R.id.btn_search);
        tvRecommendationTitle = view.findViewById(R.id.tv_recommendation_title);
        
        layoutHeaderActions = view.findViewById(R.id.layout_header_actions);
        layoutSearchBar = view.findViewById(R.id.layout_search_bar);
        etSearch = view.findViewById(R.id.et_search_wishlist);
        btnCancelSearch = view.findViewById(R.id.btn_cancel_search);
        chipGroup = view.findViewById(R.id.chip_group_wishlist);

        btnEdit.setOnClickListener(v -> toggleEditMode());
        
        btnSearch.setOnClickListener(v -> showSearchBar(true));
        btnCancelSearch.setOnClickListener(v -> showSearchBar(false));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            showOnlyOnSale = (checkedId == R.id.chip_on_sale);
            applyFilters();
        });

        view.findViewById(R.id.btn_shop_now).setOnClickListener(v -> {
            // Navigate to category/shop
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).findViewById(R.id.nav_category).performClick();
            }
        });

        btnDelete.setOnClickListener(v -> {
            deleteSelected();
        });

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });
    }

    private void setupRecyclerViews() {
        wishlistAdapter = new ProductAdapter(filteredWishlist, this);
        rvWishlist.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvWishlist.setAdapter(wishlistAdapter);
        rvWishlist.setNestedScrollingEnabled(false);

        recommendationAdapter = new ProductAdapter(recommendationList, this);
        rvRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvRecommendations.setAdapter(recommendationAdapter);
        rvRecommendations.setNestedScrollingEnabled(false);
    }

    private void fetchWishlist() {
        FirestoreManager.getInstance().getProductsCollection()
                .whereEqualTo("favorite", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    wishlist.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            wishlist.add(product);
                        }
                    }
                    updateUI();
                    applyFilters();
                });
    }

    private void applyFilters() {
        filteredWishlist.clear();
        for (Product product : wishlist) {
            boolean matchesSearch = product.getName().toLowerCase().contains(currentSearchQuery);
            boolean matchesSale = !showOnlyOnSale || (product.getOriginalPrice() > product.getPrice());
            
            if (matchesSearch && matchesSale) {
                filteredWishlist.add(product);
            }
        }
        wishlistAdapter.notifyDataSetChanged();
        
        // Handle empty filtered results vs overall empty wishlist
        if (filteredWishlist.isEmpty() && !wishlist.isEmpty()) {
            // Show a "no results found" if needed, or just let it be empty
        }
    }

    private void showSearchBar(boolean show) {
        if (show) {
            tvTitle.setVisibility(View.GONE);
            layoutHeaderActions.setVisibility(View.GONE);
            layoutSearchBar.setVisibility(View.VISIBLE);
            btnCancelSearch.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
            // Show keyboard logic could go here
        } else {
            tvTitle.setVisibility(View.VISIBLE);
            layoutHeaderActions.setVisibility(View.VISIBLE);
            layoutSearchBar.setVisibility(View.GONE);
            btnCancelSearch.setVisibility(View.GONE);
            etSearch.setText("");
            currentSearchQuery = "";
            applyFilters();
        }
    }

    private void fetchRecommendations() {
        FirestoreManager.getInstance().getProductsCollection().limit(4)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recommendationList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());
                            recommendationList.add(product);
                        }
                    }
                    recommendationAdapter.notifyDataSetChanged();
                });
    }

    private void updateUI() {
        if (wishlist.isEmpty()) {
            // State: Empty Wishlist
            layoutEmpty.setVisibility(View.VISIBLE);
            layoutList.setVisibility(View.GONE);
            scrollChips.setVisibility(View.GONE);
            btnEdit.setVisibility(View.GONE);
            btnSearch.setVisibility(View.GONE);
            tvTitle.setText("Yêu thích");
            tvRecommendationTitle.setText("Gợi ý cho bạn");
        } else {
            // State: Wishlist with items
            layoutEmpty.setVisibility(View.GONE);
            layoutList.setVisibility(View.VISIBLE);
            scrollChips.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.VISIBLE);
            tvRecommendationTitle.setText("Có thể bạn quan tâm");
            
            if (isEditMode) {
                // State: Edit Mode
                tvTitle.setText("Đã chọn (" + selectedProducts.size() + ")");
                btnEdit.setText("Xong");
                btnSearch.setVisibility(View.GONE);
                cardDeleteBar.setVisibility(View.VISIBLE);
            } else {
                // State: Normal Mode
                tvTitle.setText("Yêu thích");
                btnEdit.setText("Chỉnh sửa");
                btnSearch.setVisibility(View.VISIBLE);
                cardDeleteBar.setVisibility(View.GONE);
            }
            wishlistAdapter.notifyDataSetChanged();
        }
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        wishlistAdapter.setSelectionMode(isEditMode);
        if (!isEditMode) {
            selectedProducts.clear();
        }
        updateUI();
    }

    private void deleteSelected() {
        // Logic to remove from favorites in Firestore
        Toast.makeText(getContext(), "Đã xóa " + selectedProducts.size() + " sản phẩm", Toast.LENGTH_SHORT).show();
        wishlist.removeAll(selectedProducts);
        
        // In reality, loop through selectedProducts and update Firestore
        for (Product p : selectedProducts) {
             FirestoreManager.getInstance().getProductsCollection().document(p.getId())
                     .update("favorite", false);
        }
        
        selectedProducts.clear();
        isEditMode = false;
        wishlistAdapter.setSelectionMode(false);
        updateUI();
    }

    private void updateDeleteButtonText() {
        btnDelete.setText("Xóa (" + selectedProducts.size() + ")");
        tvTitle.setText("Đã chọn (" + selectedProducts.size() + ")");
    }

    @Override
    public void onProductClick(Product product) {
        if (isEditMode) {
            wishlistAdapter.toggleSelection(product.getId());
            if (selectedProducts.contains(product)) {
                selectedProducts.remove(product);
            } else {
                selectedProducts.add(product);
            }
            updateDeleteButtonText();
        } else {
            android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
            intent.putExtra("product", product);
            startActivity(intent);
        }
    }

    @Override
    public void onAddToCart(Product product) {
        if (!isEditMode) {
            com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập để thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
                return;
            }
            String userId = user.getUid();
            
            FirestoreManager.getInstance().getFirestore().collection("cart")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("productId", product.getId())
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                            long currentQty = doc.getLong("quantity");
                            doc.getReference().update("quantity", currentQty + 1);
                        } else {
                            com.group.models.CartItem newItem = new com.group.models.CartItem(
                                    product.getId(), product, 1, userId);
                            FirestoreManager.getInstance().getFirestore().collection("cart")
                                    .add(newItem);
                        }
                        Toast.makeText(getContext(), "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    @Override
    public void onFavoriteClick(Product product) {
        if (!isEditMode) {
            FirestoreManager.getInstance().getProductsCollection()
                    .document(product.getId())
                    .update("favorite", false)
                    .addOnSuccessListener(aVoid -> {
                        wishlist.remove(product);
                        applyFilters();
                        updateUI();
                        Toast.makeText(getContext(), "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
