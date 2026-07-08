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
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.R;
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
    // FIX (yêu cầu #4): pool dự phòng các sản phẩm chưa được chọn vào danh sách gợi ý ban đầu,
    // dùng để thay thế ngay khi 1 sản phẩm trong danh sách gợi ý được người dùng bấm yêu thích,
    // tránh việc sản phẩm đó tiếp tục hiển thị trùng ở cả lưới "Yêu thích" lẫn "Có thể bạn quan tâm".
    private List<Product> recommendationBackupPool = new ArrayList<>();








    private View layoutEmpty, layoutList, cardDeleteBar;
    private View layoutHeaderActions, layoutSearchBar;
    private TextView tvTitle, btnEdit, tvRecommendationTitle, btnCancelSearch, tvViewAllWishlist;
    private com.google.android.material.button.MaterialButton btnDelete;
    private EditText etSearch;
    private android.widget.ImageButton btnSearch;
    private boolean isEditMode = false;
    private String currentSearchQuery = "";
    private List<Product> selectedProducts = new ArrayList<>();








    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);
        initViews(view);
        setupRecyclerViews();
        // FIX (yêu cầu #4 - race condition): không còn gọi fetchRecommendations() song song ở
        // đây nữa. fetchWishlist() giờ sẽ tự gọi fetchRecommendations() SAU KHI danh sách yêu
        // thích đã tải xong, đảm bảo bước lọc trùng trong fetchRecommendations() luôn dùng đúng
        // dữ liệu wishlist mới nhất (trước đây 2 lệnh gọi Firestore chạy độc lập, ai xong trước
        // không xác định, khiến đôi khi lọc trùng bằng 1 wishlist rỗng -> sản phẩm hiện trùng).
        fetchWishlist();
        return view;
    }








    @Override
    public void onResume() {
        super.onResume();
        fetchWishlist();
    }








    private void initViews(View view) {
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
        String uid = WishlistManager.currentUserId();
        if (uid == null) {
            wishlist.clear();
            updateUI();
            applyFilters();
            // FIX (yêu cầu #4): gọi gợi ý sau khi đã biết chắc wishlist rỗng (khách chưa đăng nhập).
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
            // FIX (yêu cầu #4): chỉ tải danh sách gợi ý SAU KHI wishlist đã có dữ liệu mới nhất,
            // để bước lọc trùng bên dưới luôn chính xác.
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
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setId(doc.getId());

                            // Check if already in wishlist to avoid duplicates
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
                    // FIX (yêu cầu #4): giữ lại phần còn dư của pool để dùng thay thế ngay khi
                    // có sản phẩm trong danh sách gợi ý bị bấm yêu thích (xem replaceFavoritedRecommendation).
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








    // FIX ROOT CAUSE (yêu cầu #4): nhánh "wishlist rỗng" trước đây KHÔNG hề đụng tới
    // cardDeleteBar -> nếu xóa hết sản phẩm trong khi đang ở chế độ sửa, thanh "Xóa (n)"
    // giữ nguyên trạng thái VISIBLE từ trước đó, không bao giờ tự ẩn. Thêm dòng ẩn nó ở đây.
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








    // FIX (yêu cầu #3): luôn hiển thị popup chọn số lượng/phân loại, bất kể có phân loại hay không.
    @Override
    public void onAddToCart(Product product) {
        if (!isEditMode) {
            showVariantSheet(product);
        }
    }


    private void showVariantSheet(Product product) {
        VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) ->
                com.example.healthup.util.CartHelper.addToCart(requireContext(), product, variant, quantity));
        sheet.show(getChildFragmentManager(), "VariantSelection");
    }








    @Override
    public void onFavoriteClick(Product product) {
        if (isEditMode) return;








        WishlistManager.toggle(requireContext(), product, success -> {
            if (!isAdded() || !success) {
                return;
            }
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
                // FIX (yêu cầu #4): sản phẩm vừa được yêu thích có thể đang nằm trong khu vực
                // "Có thể bạn quan tâm" (ví dụ người dùng bấm ♥ ngay trên item gợi ý). Nếu không
                // xử lý, sản phẩm này sẽ hiển thị trùng lặp ở cả lưới "Yêu thích" lẫn khu gợi ý.
                // Gỡ nó khỏi danh sách gợi ý và thay bằng 1 sản phẩm khác từ pool dự phòng.
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
        });
    }








    // FIX (yêu cầu #4): xem chú thích tại nơi gọi (onFavoriteClick).
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