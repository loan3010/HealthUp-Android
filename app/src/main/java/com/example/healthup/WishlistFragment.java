package com.example.healthup;

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
import com.example.healthup.R;
import com.example.healthup.ProductAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
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
                .get() // Chuyển sang get() thay vì addSnapshotListener để tránh auto-revert khi lỗi
                .addOnSuccessListener(value -> {
                    if (value == null) return;
                    
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
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Không thể tải danh sách yêu thích", Toast.LENGTH_SHORT).show();
                });
    }

    private void applyFilters() {
        List<Product> newList = new ArrayList<>();
        for (Product product : wishlist) {
            boolean matchesSearch = product.getName().toLowerCase().contains(currentSearchQuery);
            boolean matchesSale = !showOnlyOnSale || (product.getOriginalPrice() > product.getPrice());
            
            if (matchesSearch && matchesSale) {
                newList.add(product);
            }
        }
        filteredWishlist = newList;
        wishlistAdapter.updateData(new ArrayList<>(filteredWishlist));
        
        // Handle empty filtered results vs overall empty wishlist
        if (filteredWishlist.isEmpty() && !wishlist.isEmpty()) {
            // Show a "no results found" if needed
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
        if (selectedProducts.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng chọn sản phẩm cần xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo bản sao danh sách cần xóa
        List<Product> toRemove = new ArrayList<>(selectedProducts);
        int total = toRemove.size();
        final int[] successCount = {0};
        final int[] failCount = {0};

        for (Product p : toRemove) {
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("favorite", false);

            FirestoreManager.getInstance().getProductsCollection().document(p.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        successCount[0]++;
                        if (successCount[0] + failCount[0] == total) {
                            handleDeleteResult(successCount[0], failCount[0]);
                        }
                    })
                    .addOnFailureListener(e -> {
                        failCount[0]++;
                        // Nếu lỗi (do Rules), chúng ta nên nạp lại dữ liệu để đảm bảo UI đồng bộ với Server
                        if (successCount[0] + failCount[0] == total) {
                            handleDeleteResult(successCount[0], failCount[0]);
                        }
                    });
            
            // Tạm thời xóa khỏi danh sách local để tạo cảm giác mượt mà (Optimistic UI)
            wishlist.remove(p);
        }
        
        // Reset trạng thái chỉnh sửa ngay lập tức
        selectedProducts.clear();
        isEditMode = false;
        wishlistAdapter.setSelectionMode(false);
        applyFilters();
        updateUI();
    }

    private void handleDeleteResult(int success, int fail) {
        if (getContext() == null) return;
        if (fail > 0) {
            Toast.makeText(getContext(), "Đã xóa " + success + " sản phẩm. Lỗi " + fail + " sản phẩm (có thể do quyền truy cập)", Toast.LENGTH_LONG).show();
            // Nạp lại dữ liệu từ Server để hiện lại những sản phẩm xóa lỗi
            fetchWishlist();
        } else {
            Toast.makeText(getContext(), "Đã xóa thành công " + success + " sản phẩm", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDeleteButtonText() {
        btnDelete.setText("Xóa (" + selectedProducts.size() + ")");
        tvTitle.setText("Đã chọn (" + selectedProducts.size() + ")");
    }

    @Override
    public void onProductClick(Product product) {
        if (isEditMode) {
            boolean isCurrentlySelected = product.isSelected();
            product.setSelected(!isCurrentlySelected);
            
            if (!isCurrentlySelected) {
                selectedProducts.add(product);
            } else {
                // Remove by ID to be safe
                for (int i = 0; i < selectedProducts.size(); i++) {
                    if (selectedProducts.get(i).getId().equals(product.getId())) {
                        selectedProducts.remove(i);
                        break;
                    }
                }
            }
            wishlistAdapter.notifyDataSetChanged(); // Dùng notifyDataSetChanged để ép CheckBox vẽ lại màu
            updateDeleteButtonText();
        } else {
            android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
            // CHỈ truyền productId để tránh lỗi TransactionTooLargeException
            intent.putExtra("productId", product.getId());
            startActivity(intent);
        }
    }

    @Override
    public void onAddToCart(Product product) {
        if (!isEditMode) {
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
                        com.example.models.CartItem newItem = new com.example.models.CartItem(
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
                });
    }

    @Override
    public void onFavoriteClick(Product product) {
        if (!isEditMode) {
            boolean currentFavorite = product.isFavorite();
            
            // 1. Cập nhật UI ngay lập tức để người dùng thấy sản phẩm biến mất
            product.setFavorite(false);
            wishlist.remove(product);
            applyFilters();
            updateUI();

            // 2. Gửi yêu cầu lên Firestore bằng Map
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("favorite", false);

            FirestoreManager.getInstance().getProductsCollection()
                    .document(product.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        // 3. Chỉ khi thất bại hoàn toàn mới hiện lại sản phẩm (Rollback)
                        product.setFavorite(true);
                        if (!wishlist.contains(product)) {
                            wishlist.add(product);
                        }
                        applyFilters();
                        updateUI();
                        
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && errorMsg.contains("PERMISSION_DENIED")) {
                            Toast.makeText(getContext(), "Lỗi quyền truy cập: Bạn cần cập nhật Firestore Rules để cho phép sửa sản phẩm.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Không thể cập nhật: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
}
