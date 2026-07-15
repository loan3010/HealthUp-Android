package com.example.healthup;




import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.util.GuestLoginRequiredHelper;
import com.example.healthup.util.GuestRecommendationsHelper;
import com.example.healthup.util.UtilityHeaderHelper;
import com.example.healthup.ProductAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




public class WishlistFragment extends Fragment implements ProductAdapter.OnProductClickListener {




    private RecyclerView rvWishlist, rvRecommendations;
    private ProductAdapter wishlistAdapter, recommendationAdapter;
    private List<Product> wishlist = new ArrayList<>();
    private List<Product> filteredWishlist = new ArrayList<>();
    private List<Product> recommendationList = new ArrayList<>();
    private List<Product> recommendationBackupPool = new ArrayList<>();




    private View layoutEmpty, layoutList, cardDeleteBar;
    private View layoutHeaderActions, layoutSearchBar;
    private TextView tvTitle, btnEdit, tvRecommendationTitle, btnCancelSearch, tvViewAllWishlist;
    // FIX (đồng bộ với Giỏ hàng): nút Xóa giờ dùng TextView bo góc giống btnDeleteSelected
    // của Cart, thay vì MaterialButton to bản như trước — khớp với item mới trong
    // fragment_wishlist.xml.
    private TextView btnDelete;
    // FIX: checkbox "Tất cả" ở thanh dưới cùng khi vào chế độ Chỉnh sửa — cho phép chọn/bỏ
    // chọn toàn bộ sản phẩm yêu thích thay vì phải bấm từng sản phẩm một.
    private CheckBox cbSelectAll;
    private EditText etSearch;
    private android.widget.ImageButton btnSearch;
    private boolean isEditMode = false;
    private String currentSearchQuery = "";
    private List<Product> selectedProducts = new ArrayList<>();




    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);
        
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            setupGuestMode(view);
        } else {
            initViews(view);
            setupRecyclerViews();
            fetchWishlist();
        }
        return view;
    }

    private void setupGuestMode(View view) {
        UtilityHeaderHelper.bind(view, this, "Yêu thích");
        layoutEmpty = view.findViewById(R.id.layout_wishlist_empty);
        layoutHeaderActions = view.findViewById(R.id.layout_header_actions);
        if (layoutHeaderActions != null) {
            layoutHeaderActions.setVisibility(View.GONE);
        }
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(View.GONE);
        }

        View list = view.findViewById(R.id.rv_wishlist);
        if (list != null) {
            list.setVisibility(View.GONE);
        }

        View legacyRecommendations = view.findViewById(R.id.layout_wishlist_recommendations_legacy);
        if (legacyRecommendations != null) {
            legacyRecommendations.setVisibility(View.GONE);
        }

        View cardDeleteBarView = view.findViewById(R.id.card_delete_bar);
        if (cardDeleteBarView != null) {
            cardDeleteBarView.setVisibility(View.GONE);
        }

        GuestLoginRequiredHelper.bind(view, this);
        GuestRecommendationsHelper.bind(view, this);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            fetchWishlist();
        }
    }




    private void initViews(View view) {
        UtilityHeaderHelper.bind(view, this, "Yêu thích");
        rvWishlist = view.findViewById(R.id.rv_wishlist);
        rvRecommendations = view.findViewById(R.id.rv_wishlist_recommendations);
        layoutEmpty = view.findViewById(R.id.layout_wishlist_empty);
        layoutList = view.findViewById(R.id.rv_wishlist);
        cardDeleteBar = view.findViewById(R.id.card_delete_bar);
        tvTitle = view.findViewById(R.id.tv_title);
        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete_selected);
        btnSearch = view.findViewById(R.id.btn_search);
        tvRecommendationTitle = view.findViewById(R.id.tv_recommendation_title);
        tvViewAllWishlist = view.findViewById(R.id.tv_view_all_wishlist);
        cbSelectAll = view.findViewById(R.id.cb_select_all_wishlist);




        layoutHeaderActions = view.findViewById(R.id.layout_header_actions);
        layoutSearchBar = view.findViewById(R.id.layout_search_bar);
        etSearch = view.findViewById(R.id.et_search_wishlist);
        btnCancelSearch = view.findViewById(R.id.btn_cancel_search);




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




        view.findViewById(R.id.btn_shop_now).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).findViewById(R.id.nav_category).performClick();
            }
        });




        if (tvViewAllWishlist != null) {
            tvViewAllWishlist.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).findViewById(R.id.nav_category).performClick();
                }
            });
        }




        btnDelete.setOnClickListener(v -> {
            deleteSelected();
        });




        // FIX: gắn listener cho checkbox "Tất cả" — khi tick/bỏ tick, chọn hoặc bỏ chọn toàn
        // bộ sản phẩm đang hiển thị (theo bộ lọc tìm kiếm hiện tại).
        attachSelectAllListener();
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
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            wishlist.clear();
            updateUI();
            applyFilters();
            fetchRecommendations();
            return;
        }




        WishlistManager.loadWishlistProducts(uid, products -> {
            if (!isAdded()) {
                return;
            }
            wishlist.clear();
            wishlist.addAll(products);
            updateUI();
            applyFilters();
            fetchRecommendations();
        });
    }




    private void applyFilters() {
        List<Product> newList = new ArrayList<>();
        for (Product product : wishlist) {
            boolean matchesSearch = product.getName().toLowerCase().contains(currentSearchQuery);
            if (matchesSearch) {
                newList.add(product);
            }
        }
        filteredWishlist = newList;
        wishlistAdapter.updateData(new ArrayList<>(filteredWishlist));
        // FIX: danh sách hiển thị (sau khi lọc) vừa đổi, đồng bộ lại trạng thái checkbox
        // "Tất cả" cho khớp (ví dụ: đang chọn hết rồi gõ tìm kiếm làm số lượng hiển thị đổi).
        syncSelectAllCheckbox();
    }




    private void showSearchBar(boolean show) {
        if (show) {
            tvTitle.setVisibility(View.GONE);
            layoutHeaderActions.setVisibility(View.GONE);
            layoutSearchBar.setVisibility(View.VISIBLE);
            btnCancelSearch.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
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
        FirestoreManager.getInstance().getProductsCollection().limit(30)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    List<Product> pool = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Product product = Product.fromDocument(doc);
                        if (product != null) {




                            boolean alreadyInWishlist = false;
                            for (Product wp : wishlist) {
                                if (wp.getId() != null && wp.getId().equals(product.getId())) {
                                    alreadyInWishlist = true;
                                    break;
                                }
                            }




                            if (!alreadyInWishlist) {
                                pool.add(product);
                            }
                        }
                    }
                    Collections.shuffle(pool);
                    int pick = Math.min(6, pool.size());
                    recommendationList.clear();
                    recommendationList.addAll(pool.subList(0, pick));
                    recommendationBackupPool.clear();
                    recommendationBackupPool.addAll(pool.subList(pick, pool.size()));
                    syncRecommendationFavoriteState();
                });
    }




    private void syncRecommendationFavoriteState() {
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            for (Product p : recommendationList) p.setFavorite(false);
            recommendationAdapter.updateData(new ArrayList<>(recommendationList));
            return;
        }
        WishlistManager.loadFavoriteIds(uid, ids -> {
            if (!isAdded()) return;
            WishlistManager.applyFavoriteState(recommendationList, ids);
            recommendationAdapter.updateData(new ArrayList<>(recommendationList));
        });
    }




    private void updateUI() {
        if (wishlist.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            layoutList.setVisibility(View.GONE);
            btnEdit.setVisibility(View.GONE);
            btnSearch.setVisibility(View.GONE);
            cardDeleteBar.setVisibility(View.GONE);
            tvTitle.setText("Yêu thích");
        } else {
            layoutEmpty.setVisibility(View.GONE);
            layoutList.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.VISIBLE);




            if (isEditMode) {
                tvTitle.setText("Đã chọn (" + selectedProducts.size() + ")");
                btnEdit.setText("Xong");
                btnSearch.setVisibility(View.GONE);
                cardDeleteBar.setVisibility(View.VISIBLE);
            } else {
                tvTitle.setText("Yêu thích");
                btnEdit.setText("Chỉnh sửa");
                btnSearch.setVisibility(View.VISIBLE);
                cardDeleteBar.setVisibility(View.GONE);
            }
        }
        tvRecommendationTitle.setText("Có thể bạn quan tâm");
        // FIX: mỗi lần vẽ lại UI (bật/tắt chế độ Chỉnh sửa, xóa xong, v.v.) đều đồng bộ lại
        // trạng thái checkbox "Tất cả" cho khớp với selectedProducts hiện tại.
        syncSelectAllCheckbox();
    }




    private void toggleEditMode() {
        isEditMode = !isEditMode;
        wishlistAdapter.setSelectionMode(isEditMode);
        if (!isEditMode) {
            selectedProducts.clear();
        }
        updateUI();
        updateDeleteButtonText();
    }




    // FIX: logic xử lý khi người dùng tick/bỏ tick checkbox "Tất cả".
    private void attachSelectAllListener() {
        if (cbSelectAll == null) return;
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isEditMode) return;
            selectAllProducts(isChecked);
        });
    }




    private void selectAllProducts(boolean selectAll) {
        selectedProducts.clear();
        for (Product p : filteredWishlist) {
            p.setSelected(selectAll);
            if (selectAll) {
                selectedProducts.add(p);
            }
        }
        wishlistAdapter.notifyDataSetChanged();
        updateDeleteButtonText();
    }




    // FIX: đồng bộ 1 chiều từ selectedProducts -> checkbox "Tất cả", tránh vòng lặp gọi lại
    // listener (tạm gỡ listener trước khi setChecked(), sau đó gắn lại).
    private void syncSelectAllCheckbox() {
        if (cbSelectAll == null) return;
        boolean allSelected = !filteredWishlist.isEmpty() && selectedProducts.size() >= filteredWishlist.size();
        if (cbSelectAll.isChecked() != allSelected) {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(allSelected);
            attachSelectAllListener();
        }
    }




    private void deleteSelected() {
        if (selectedProducts.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng chọn sản phẩm cần xóa", Toast.LENGTH_SHORT).show();
            return;
        }




        List<Product> toRemove = new ArrayList<>(selectedProducts);
        int total = toRemove.size();
        final int[] successCount = {0};
        final int[] failCount = {0};




        for (Product p : toRemove) {
            String uid = WishlistManager.currentUserId();
            if (uid == null) {
                continue;
            }
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("wishlist").document(p.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        successCount[0]++;
                        if (successCount[0] + failCount[0] == total) {
                            handleDeleteResult(successCount[0], failCount[0]);
                        }
                    })
                    .addOnFailureListener(e -> {
                        failCount[0]++;
                        if (successCount[0] + failCount[0] == total) {
                            handleDeleteResult(successCount[0], failCount[0]);
                        }
                    });




            wishlist.remove(p);
        }




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
            fetchWishlist();
        } else {
            Toast.makeText(getContext(), "Đã xóa thành công " + success + " sản phẩm", Toast.LENGTH_SHORT).show();
        }
    }




    private void updateDeleteButtonText() {
        btnDelete.setText("Xóa (" + selectedProducts.size() + ")");
        tvTitle.setText("Đã chọn (" + selectedProducts.size() + ")");
        // FIX: mỗi lần số lượng đã chọn thay đổi (do bấm từng sản phẩm), đồng bộ lại
        // checkbox "Tất cả" — tự động tick khi đã chọn hết, tự động bỏ tick khi chưa đủ.
        syncSelectAllCheckbox();
    }




    @Override
    public void onProductClick(Product product) {
        if (isEditMode) {
            boolean isCurrentlySelected = product.isSelected();
            product.setSelected(!isCurrentlySelected);




            if (!isCurrentlySelected) {
                selectedProducts.add(product);
            } else {
                for (int i = 0; i < selectedProducts.size(); i++) {
                    if (selectedProducts.get(i).getId().equals(product.getId())) {
                        selectedProducts.remove(i);
                        break;
                    }
                }
            }
            wishlistAdapter.notifyDataSetChanged();
            updateDeleteButtonText();
        } else {
            android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
            intent.putExtra("productId", product.getId());
            startActivity(intent);
        }
    }




    @Override
    public void onAddToCart(Product product) {
        if (!isEditMode) {
            showVariantSheet(product);
        }
    }




    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity, selections) ->
                com.example.healthup.util.CartHelper.addToCart(requireContext(), product, variant, quantity, selections));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }




    @Override
    public void onFavoriteClick(Product product) {
        if (isEditMode) return;




        WishlistManager.toggle(requireContext(), product, success -> {
            if (!isAdded()) return;
            if (!success) {
                applyFilters();
                updateUI();
                syncRecommendationFavoriteState();
            }
        });




        if (product.isFavorite()) {
            boolean alreadyInList = false;
            for (Product p : wishlist) {
                if (p.getId() != null && p.getId().equals(product.getId())) {
                    alreadyInList = true;
                    break;
                }
            }
            if (!alreadyInList) {
                wishlist.add(0, product);
            }
            replaceFavoritedRecommendation(product);
        } else {
            for (int i = 0; i < wishlist.size(); i++) {
                if (wishlist.get(i).getId() != null && wishlist.get(i).getId().equals(product.getId())) {
                    wishlist.remove(i);
                    break;
                }
            }
        }
        applyFilters();
        updateUI();
    }




    private void replaceFavoritedRecommendation(Product favorited) {
        if (favorited == null || favorited.getId() == null || recommendationAdapter == null) {
            return;
        }




        int index = -1;
        for (int i = 0; i < recommendationList.size(); i++) {
            Product p = recommendationList.get(i);
            if (p.getId() != null && p.getId().equals(favorited.getId())) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }




        recommendationList.remove(index);
        if (!recommendationBackupPool.isEmpty()) {
            Product replacement = recommendationBackupPool.remove(0);
            replacement.setFavorite(false);
            recommendationList.add(index, replacement);
        }
        recommendationAdapter.updateData(new ArrayList<>(recommendationList));
    }
}