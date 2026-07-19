package com.example.healthup;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.CartAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.CartHelper;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.GuestWishlistUiHelper;
import com.example.healthup.util.PhoneVerifiedHelper;
import com.example.healthup.util.ReviewStatsHelper;
import com.example.healthup.util.StockManager;
import com.example.healthup.util.ToastUtils;
import com.example.models.CartItem;
import com.example.models.Product;
import com.example.models.Voucher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartFragment extends Fragment implements CartAdapter.Listener {

    private List<CartItem> cartItems = new ArrayList<>();
    private List<CartItem> displayItems = new ArrayList<>(); // Items including potential header
    private List<Voucher> selectedVouchers = new ArrayList<>();
    private CartAdapter adapter;
    private ProductAdapter recommendAdapter;

    private List<Product> recommendDisplayed = new ArrayList<>();
    private List<Product> recommendPool = new ArrayList<>();

    private RecyclerView rvCartItems, rvCartRecommendations;
    private View emptyState, footer, rowVoucher, btnContinueShopping, guestSyncBanner;
    private View scrollContentInner;
    private View totalsRow, editRow;
    private View btnSaveToWishlist, btnDeleteSelected;
    private CheckBox cbSelectAllEdit, cbSelectAll;
    private TextView tvTotalPrice, tvSavings, btnCheckout, tvCartTitle, tvEditToggle, tvViewAllRecommend;
    private com.google.android.material.button.MaterialButton btnMoreCartRecommend;
    private ImageButton btnBack;

    private FirebaseFirestore db;
    private String userId;
    private boolean editMode = false;

    private boolean returnToPreviousActivity = false;

    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener("voucher_result", this, (requestKey, result) -> {
            List<Voucher> vouchers = (List<Voucher>) result.getSerializable("selected_vouchers");
            if (vouchers != null) {
                this.selectedVouchers = new ArrayList<>(vouchers);
                renderVouchers();
                updateFooter();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        returnToPreviousActivity = getArguments() != null && getArguments().getBoolean("return_to_previous", false);

        bindViews(view);
        applyHeaderWindowInsets(view);
        applyFooterWindowInsets(view);
        setupListeners();
        setupRecommendationAdapter();
        
        loadCartFromFirestore();
        fetchRecommendations();

        return view;
    }

    @Override
    public void onDestroyView() {
        adapter = null;
        recommendAdapter = null;
        super.onDestroyView();
    }

    private void applyHeaderWindowInsets(View view) {
        View header = view.findViewById(R.id.header);
        if (header == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }

    private void applyFooterWindowInsets(View view) {
        View footerView = view.findViewById(R.id.footer);
        if (footerView == null) return;

        final int basePaddingBottom = footerView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(footerView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    basePaddingBottom + systemBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(footerView);
    }

    private void bindViews(View view) {
        rvCartItems = view.findViewById(R.id.rvCartItems);
        rvCartRecommendations = view.findViewById(R.id.rvCartRecommendations);
        emptyState = view.findViewById(R.id.emptyState);
        footer = view.findViewById(R.id.footer);
        totalsRow = view.findViewById(R.id.totalsRow);
        editRow = view.findViewById(R.id.editRow);
        cbSelectAllEdit = view.findViewById(R.id.cbSelectAllEdit);
        cbSelectAll = view.findViewById(R.id.cbSelectAll);
        btnSaveToWishlist = view.findViewById(R.id.btnSaveToWishlist);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);
        tvTotalPrice = view.findViewById(R.id.tvTotalPrice);
        tvSavings = view.findViewById(R.id.tvSavings);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        btnBack = view.findViewById(R.id.btnBack);
        tvCartTitle = view.findViewById(R.id.tvCartTitle);
        tvEditToggle = view.findViewById(R.id.tvEditToggle);
        tvViewAllRecommend = view.findViewById(R.id.tvViewAllRecommend);
        btnMoreCartRecommend = view.findViewById(R.id.btnMoreCartRecommend);
        rowVoucher = view.findViewById(R.id.rowVoucher);
        btnContinueShopping = view.findViewById(R.id.btnContinueShopping);
        guestSyncBanner = view.findViewById(R.id.guestSyncBanner);
        scrollContentInner = view.findViewById(R.id.scrollContentInner);

        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCartItems.setNestedScrollingEnabled(false);

        rvCartRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvCartRecommendations.setNestedScrollingEnabled(false);
    }

    private void setupListeners() {
        attachSelectAllListener(cbSelectAllEdit);
        attachSelectAllListener(cbSelectAll);

        if (btnDeleteSelected != null) {
            btnDeleteSelected.setOnClickListener(v -> deleteSelectedItems());
        }
        if (btnSaveToWishlist != null) {
            btnSaveToWishlist.setOnClickListener(v -> saveSelectedItemsToWishlist());
        }
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(v -> goToCheckout());
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> goBack());
        }
        if (tvEditToggle != null) {
            tvEditToggle.setOnClickListener(v -> setEditMode(!editMode));
        }
        if (rowVoucher != null) {
            rowVoucher.setOnClickListener(v -> openVoucherList());
        }
        if (btnContinueShopping != null) {
            btnContinueShopping.setOnClickListener(v -> showHomeTab());
        }
        if (tvViewAllRecommend != null) {
            tvViewAllRecommend.setOnClickListener(v -> goToCategoryTab());
        }
        if (btnMoreCartRecommend != null) {
            btnMoreCartRecommend.setOnClickListener(v -> loadMoreCartRecommendations());
        }
        if (guestSyncBanner != null) {
            guestSyncBanner.setOnClickListener(v -> {
                Intent loginIntent = new Intent(requireContext(), LoginActivity.class);
                startActivity(loginIntent);
            });
        }
    }

    private void showHomeTab() {
        if (!isAdded()) return;
        com.google.android.material.bottomnavigation.BottomNavigationView navView =
                requireActivity().findViewById(R.id.bottom_navigation);
        if (navView != null) {
            navView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void attachSelectAllListener(CheckBox checkBox) {
        if (checkBox == null) return;
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CartItem item : cartItems) {
                if (item.getStock() > 0) {
                    item.setSelected(isChecked);
                } else {
                    item.setSelected(false);
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
            updateFooter();
            syncSelectAllCheckboxes(isChecked);
        });
    }

    private void syncSelectAllCheckboxes(boolean allSelected) {
        if (cbSelectAllEdit != null && cbSelectAllEdit.isChecked() != allSelected) {
            cbSelectAllEdit.setOnCheckedChangeListener(null);
            cbSelectAllEdit.setChecked(allSelected);
            attachSelectAllListener(cbSelectAllEdit);
        }
        if (cbSelectAll != null && cbSelectAll.isChecked() != allSelected) {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(allSelected);
            attachSelectAllListener(cbSelectAll);
        }
    }

    private void goBack() {
        if (!isAdded()) return;

        if (returnToPreviousActivity) {
            requireActivity().finish();
            return;
        }

        boolean movedBack = requireActivity().getSupportFragmentManager().popBackStackImmediate();
        if (!movedBack) {
            showHomeTab();
        }
    }

    private void goToCategoryTab() {
        if (!isAdded()) return;
        com.google.android.material.bottomnavigation.BottomNavigationView navView =
                requireActivity().findViewById(R.id.bottom_navigation);
        if (navView != null) {
            navView.setSelectedItemId(R.id.nav_category);
        }
    }

    private void setEditMode(boolean enabled) {
        editMode = enabled;
        if (tvEditToggle != null) {
            tvEditToggle.setText(enabled ? getString(R.string.cart_done) : getString(R.string.cart_edit));
        }
        if (totalsRow != null) {
            totalsRow.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (editRow != null) {
            editRow.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (rowVoucher != null) {
            boolean isEmpty = cartItems.isEmpty();
            rowVoucher.setVisibility(!enabled && !isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void openVoucherList() {
        double selectedTotal = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                selectedTotal += item.getPrice() * item.getQuantity();
            }
        }

        if (selectedTotal == 0) {
            ToastUtils.show(getContext(), "Vui lòng chọn sản phẩm để xem voucher áp dụng");
            return;
        }

        PromoCouponFragment fragment = new PromoCouponFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable("selected_vouchers", new ArrayList<>(selectedVouchers));
        bundle.putDouble("order_total", selectedTotal);
        bundle.putDouble("shipping_fee", 21000);
        bundle.putBoolean("has_visited", true);
        fragment.setArguments(bundle);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void renderVouchers() {
        if (rowVoucher == null) return;
        TextView tvVoucherInfo = rowVoucher.findViewById(R.id.tvVoucherInfo);
        if (tvVoucherInfo == null) return;


        if (selectedVouchers.isEmpty()) {
            tvVoucherInfo.setText("Chọn hoặc nhập mã khuyến mãi");
            tvVoucherInfo.setTextColor(getResources().getColor(R.color.green_button));
        } else {
            // ✅ Theo yêu cầu: Chỉ ghi nhãn "Đã áp dụng mã vận chuyển" và số tiền giảm hàng
            boolean hasShipping = false;
            double itemDiscount = 0;
            double itemsTotal = 0;
            for (CartItem ci : cartItems) if (ci.isSelected()) itemsTotal += ci.getPrice() * ci.getQuantity();


            for (Voucher v : selectedVouchers) {
                // Kiểm tra điều kiện ngay tại đây để nhãn hiển thị chính xác
                if (itemsTotal >= v.getMinOrderAmount()) {
                    if (v.getType() == Voucher.Type.SHIPPING) hasShipping = true;
                    else itemDiscount += calculateSavingForFooter(v, itemsTotal);
                }
            }


            StringBuilder sb = new StringBuilder();
            if (hasShipping) {
                sb.append("Đã áp dụng mã vận chuyển");
            }

            if (itemDiscount > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("Giảm sản phẩm ").append(currencyFormat.format(itemDiscount)).append("đ");
            }
            
            if (sb.length() == 0) {
                tvVoucherInfo.setText("Đơn hàng chưa đủ điều kiện để áp mã, bạn cần mua thêm");
                tvVoucherInfo.setTextColor(Color.RED);
            } else {
                tvVoucherInfo.setText(sb.toString());
                tvVoucherInfo.setTextColor(getResources().getColor(R.color.text_dark));
            }
        }
    }

    private void setupRecommendationAdapter() {
        recommendAdapter = new ProductAdapter(new ArrayList<>(), new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product) {
                if (product == null || product.getId() == null || !isAdded()) return;
                Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }

            @Override
            public void onAddToCart(Product product) {
                if (!isAdded()) return;
                VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity, selections) ->
                        CartHelper.addToCart(requireContext(), product, variant, quantity, selections, new CartHelper.CartCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                ToastUtils.show(getContext(), R.string.added_to_cart);
                                replaceRecommendation(product);
                                refreshCartList();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                if (isAdded()) {
                                    ToastUtils.show(getContext(), R.string.register_error_generic);
                                }
                            }
                        }));
                sheet.show(getChildFragmentManager(), "VariantSelectionCartRecommend");
            }

            @Override
            public void onFavoriteClick(Product product) {
                if (!isAdded()) return;
                WishlistManager.toggle(requireContext(), product, success -> {
                    if (success && isAdded() && recommendAdapter != null) {
                        recommendAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
        GuestWishlistUiHelper.applyTo(recommendAdapter);
        rvCartRecommendations.setAdapter(recommendAdapter);
    }

    private void fetchRecommendations() {
        if (rvCartRecommendations == null) return;
        FirestoreManager.getInstance().getProductsCollection()
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    List<Product> pool = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        if (!Product.isVisibleToBuyers(doc)) continue;
                        Product p = Product.fromDocument(doc);
                        if (p != null) pool.add(p);
                    }
                    Collections.shuffle(pool);
                    applyWishlistStateToPool(pool);
                });
    }

    private void applyWishlistStateToPool(List<Product> pool) {
        if (userId == null) {
            splitRecommendationPool(pool);
            return;
        }
        WishlistManager.loadFavoriteIds(userId, ids -> {
            if (!isAdded()) return;
            WishlistManager.applyFavoriteState(pool, ids);
            splitRecommendationPool(pool);
        });
    }

    private void splitRecommendationPool(List<Product> pool) {
        if (!isAdded()) return;
        int showCount = Math.min(6, pool.size());
        recommendDisplayed = new ArrayList<>(pool.subList(0, showCount));
        recommendPool = new ArrayList<>(pool.subList(showCount, pool.size()));
        if (recommendAdapter != null) {
            recommendAdapter.updateData(recommendDisplayed);
        }
        enrichRecommendationStats();
        updateCartRecommendMoreButton();
    }

    private void enrichRecommendationStats() {
        ReviewStatsHelper.enrichProducts(recommendDisplayed, () -> {
            if (isAdded() && recommendAdapter != null) {
                recommendAdapter.notifyDataSetChanged();
            }
        });
    }

    private void loadMoreCartRecommendations() {
        if (!isAdded() || recommendPool.isEmpty()) return;
        int batch = Math.min(4, recommendPool.size());
        for (int i = 0; i < batch; i++) {
            recommendDisplayed.add(recommendPool.remove(0));
        }
        if (recommendAdapter != null) {
            recommendAdapter.updateData(new ArrayList<>(recommendDisplayed));
        }
        enrichRecommendationStats();
        updateCartRecommendMoreButton();
    }

    private void updateCartRecommendMoreButton() {
        if (btnMoreCartRecommend == null) return;
        btnMoreCartRecommend.setVisibility(recommendPool.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void replaceRecommendation(Product addedProduct) {
        if (!isAdded() || addedProduct == null || addedProduct.getId() == null) return;

        int index = -1;
        for (int i = 0; i < recommendDisplayed.size(); i++) {
            Product p = recommendDisplayed.get(i);
            if (p.getId() != null && p.getId().equals(addedProduct.getId())) {
                index = i;
                break;
            }
        }
        if (index == -1) return;

        recommendDisplayed.remove(index);
        if (!recommendPool.isEmpty()) {
            recommendDisplayed.add(index, recommendPool.remove(0));
        }
        if (recommendAdapter != null) {
            recommendAdapter.updateData(recommendDisplayed);
        }
        updateCartRecommendMoreButton();
    }

    private void refreshCartList() {
        if (userId != null) {
            loadCartFromFirestore();
        } else {
            loadGuestCart();
        }
    }

    private void loadCartFromFirestore() {
        if (userId == null) {
            loadGuestCart();
            return;
        }

        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isAdded()) {
                        applyFirestoreCartSnapshot(snapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        ToastUtils.show(getContext(), "Lỗi tải giỏ hàng");
                    }
                });
    }

    private void applyFirestoreCartSnapshot(com.google.firebase.firestore.QuerySnapshot snapshot) {
        Map<String, Boolean> selection = new HashMap<>();
        for (CartItem ci : cartItems) {
            if (ci.getId() != null) {
                selection.put(ci.getId(), ci.isSelected());
            }
        }


        boolean isRebuyFlow = getArguments() != null && getArguments().getBoolean("is_rebuy_flow", false);


        List<CartItem> loadedItems = new ArrayList<>();
        if (snapshot != null && !snapshot.isEmpty()) {
            for (QueryDocumentSnapshot doc : snapshot) {
                CartItem item = parseCartItem(doc);
                if (item != null) {
                    if (isRebuyFlow) {
                        Boolean dbSelected = doc.getBoolean("selected");
                        item.setSelected(dbSelected != null ? dbSelected : false);
                    } else {
                        // ✅ GIỮ LẠI TRẠNG THÁI CHỌN NẾU ĐÃ CÓ TRONG BỘ NHỚ (Khi back từ Checkout)
                        // Nếu là lần đầu vào giỏ hàng (selection trống) thì mới mặc định chọn hết
                        if (selection.containsKey(item.getId())) {
                            item.setSelected(selection.get(item.getId()));
                        } else {
                            item.setSelected(true);
                        }
                    }
                    loadedItems.add(item);
                }
            }
        }


        loadedItems.sort((o1, o2) -> {
            com.google.firebase.Timestamp t1 = o1.getUpdatedAt();
            com.google.firebase.Timestamp t2 = o2.getUpdatedAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });


        cartItems.clear();
        cartItems.addAll(loadedItems);
        
        // ✅ Cập nhật trạng thái checkbox "Tất cả" dựa trên thực tế
        boolean allSelected = !cartItems.isEmpty();
        for (CartItem i : cartItems) {
            if (!i.isSelected()) { allSelected = false; break; }
        }

        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(allSelected);
            attachSelectAllListener(cbSelectAll);
        }
        if (cbSelectAllEdit != null) {
            cbSelectAllEdit.setOnCheckedChangeListener(null);
            cbSelectAllEdit.setChecked(allSelected);
            attachSelectAllListener(cbSelectAllEdit);
        }

        hydrateCartStock(() -> {
            if (!isAdded()) return;
            renderList();
            updateFooter();
            applyFavoriteStateToCartItems();
        });
    }

    private void hydrateCartStock(@NonNull Runnable onComplete) {
        if (cartItems.isEmpty()) {
            onComplete.run();
            return;
        }

        List<String> productIds = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.getProductId() != null && !item.getProductId().isEmpty()
                    && !productIds.contains(item.getProductId())) {
                productIds.add(item.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            onComplete.run();
            return;
        }

        final int[] remaining = {productIds.size()};
        for (String productId : productIds) {
            db.collection("products").document(productId).get()
                    .addOnSuccessListener(doc -> {
                        if (isAdded()) {
                            for (CartItem item : cartItems) {
                                if (productId.equals(item.getProductId())) {
                                    item.setStock(StockManager.resolveAvailableStock(doc, item.getVariantId()));
                                }
                            }
                        }
                        if (--remaining[0] == 0 && isAdded()) {
                            onComplete.run();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (--remaining[0] == 0 && isAdded()) {
                            onComplete.run();
                        }
                    });
        }
    }

    private void applyFavoriteStateToCartItems() {
        if (userId == null || cartItems.isEmpty()) return;
        WishlistManager.loadFavoriteIds(userId, ids -> {
            if (!isAdded()) return;
            boolean changed = false;
            for (CartItem item : cartItems) {
                boolean fav = item.getProductId() != null && ids.contains(item.getProductId());
                if (item.isFavorite() != fav) {
                    item.setFavorite(fav);
                    changed = true;
                }
            }
            if (changed && adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void loadGuestCart() {
        cartItems.clear();
        cartItems.addAll(GuestCartManager.getInstance(requireContext()).getItems());
        for (CartItem item : cartItems) {
            if (!item.isSelected()) {
                item.setSelected(true);
            }
        }
        updateGuestBanner();
        hydrateCartStock(() -> {
            if (!isAdded()) return;
            renderList();
            updateFooter();
        });
    }

    private void updateGuestBanner() {
        if (guestSyncBanner == null) return;
        boolean showBanner = userId == null && !cartItems.isEmpty();
        guestSyncBanner.setVisibility(showBanner ? View.VISIBLE : View.GONE);
        if (showBanner) {
            guestSyncBanner.bringToFront();
            guestSyncBanner.post(this::applyGuestBannerScrollPadding);
        } else if (scrollContentInner != null) {
            scrollContentInner.setPadding(
                    scrollContentInner.getPaddingLeft(),
                    0,
                    scrollContentInner.getPaddingRight(),
                    scrollContentInner.getPaddingBottom());
        }
    }

    private void applyGuestBannerScrollPadding() {
        if (!isAdded() || guestSyncBanner == null || scrollContentInner == null) return;
        if (guestSyncBanner.getVisibility() != View.VISIBLE) return;
        int bannerSpace = guestSyncBanner.getHeight() + dpToPx(8);
        scrollContentInner.setPadding(
                scrollContentInner.getPaddingLeft(),
                bannerSpace,
                scrollContentInner.getPaddingRight(),
                scrollContentInner.getPaddingBottom());
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        String newUserId = FirebaseAuth.getInstance().getUid();
        if (newUserId != null && !newUserId.equals(userId)) {
            userId = newUserId;
            GuestCartManager.getInstance(requireContext()).mergeToFirestore(userId, () -> {
                if (isAdded()) {
                    loadCartFromFirestore();
                }
            });
            return;
        }
        userId = newUserId;
        GuestWishlistUiHelper.applyTo(recommendAdapter);
        loadCartFromFirestore();
    }

    private String getStringOrMapLabel(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof String) return (String) val;
        if (val instanceof java.util.Map) {
            Object label = ((java.util.Map<?, ?>) val).get("label");
            if (label != null) return String.valueOf(label);
        }
        return null;
    }

    private CartItem parseCartItem(QueryDocumentSnapshot doc) {
        CartItem item = null;
        try {
            item = doc.toObject(CartItem.class);
        } catch (RuntimeException ignored) {}

        if (item == null) {
            item = new CartItem();
        }
        item.setId(doc.getId());

        if (item.getProductId() == null) {
            item.setProductId(doc.getString("productId"));
        }
        if (item.getName() == null || item.getName().isEmpty()) {
            String name = doc.getString("name");
            if (name == null || name.isEmpty()) {
                Object productObj = doc.get("product");
                if (productObj instanceof java.util.Map) {
                    Object productName = ((java.util.Map<?, ?>) productObj).get("name");
                    if (productName instanceof String) name = (String) productName;
                }
            }
            item.setName(name);
        }

        item.setWeight(getStringOrMapLabel(doc, "weight"));
        item.setFlavor(getStringOrMapLabel(doc, "flavor"));
        item.setPackageType(getStringOrMapLabel(doc, "packageType"));

        if (item.getQuantity() <= 0) {
            Long quantity = doc.getLong("quantity");
            item.setQuantity(quantity != null ? quantity.intValue() : 1);
        }
        if (item.getPrice() <= 0) {
            item.setPrice(readDouble(doc, "price"));
        }
        if (item.getOriginalPrice() <= 0) {
            double original = readDouble(doc, "originalPrice");
            item.setOriginalPrice(original > 0 ? original : item.getPrice());
        }

        hydrateImageUrl(item, doc);

        if (item.getProductId() == null || item.getProductId().isEmpty() ||
                item.getName() == null || item.getName().isEmpty()) {
            return null;
        }

        return item;
    }

    private double readDouble(QueryDocumentSnapshot doc, String field) {
        Double value = doc.getDouble(field);
        if (value != null) {
            return value;
        }
        Long longValue = doc.getLong(field);
        return longValue != null ? longValue.doubleValue() : 0d;
    }

    private void hydrateImageUrl(CartItem item, QueryDocumentSnapshot doc) {
        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            return;
        }
        String image = doc.getString("imageUrl");
        if (image == null || image.isEmpty()) {
            image = doc.getString("image");
        }
        if (image != null && !image.isEmpty()) {
            item.setImageUrl(image);
            return;
        }
        if (item.getProduct() != null && item.getProduct().getImageUrl() != null) {
            item.setImageUrl(item.getProduct().getImageUrl());
            return;
        }
        Object imagesObj = doc.get("images");
        if (imagesObj instanceof String) {
            String imagePath = (String) imagesObj;
            if (!imagePath.isEmpty()) {
                item.setImageUrl(imagePath);
                return;
            }
        }
        if (imagesObj instanceof List) {
            List<?> images = (List<?>) imagesObj;
            if (!images.isEmpty()) {
                Object first = images.get(0);
                if (first instanceof String) {
                    item.setImageUrl((String) first);
                }
            }
        }
    }

    private void renderList() {
        if (!isAdded() || emptyState == null || rvCartItems == null || footer == null) {
            return;
        }


        // Prepare display list: in-stock first, then header, then out-of-stock
        displayItems.clear();
        List<CartItem> inStock = new ArrayList<>();
        List<CartItem> outOfStock = new ArrayList<>();

        for (CartItem item : cartItems) {
            if (item.getStock() > 0) {
                inStock.add(item);
            } else {
                outOfStock.add(item);
            }
        }

        displayItems.addAll(inStock);
        if (!outOfStock.isEmpty()) {
            CartItem header = new CartItem();
            header.setHeader(true);
            displayItems.add(header);
            displayItems.addAll(outOfStock);
        }


        boolean isEmpty = cartItems.isEmpty();
        if (isEmpty && editMode) {
            setEditMode(false);
        }

        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvCartItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        footer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (tvEditToggle != null) {
            tvEditToggle.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
        }
        if (rowVoucher != null) {
            rowVoucher.setVisibility(!isEmpty && !editMode ? View.VISIBLE : View.GONE);
        }

        updateGuestBanner();

        if (adapter == null) {
            adapter = new CartAdapter(displayItems, this);
            rvCartItems.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onClearOutOfStock() {
        List<CartItem> toRemove = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.getStock() <= 0) toRemove.add(item);
        }
        if (toRemove.isEmpty()) return;

        if (userId != null) {
            WriteBatch batch = db.batch();
            for (CartItem item : toRemove) {
                if (item.getId() != null) {
                    batch.delete(db.collection("users").document(userId)
                            .collection("cart").document(item.getId()));
                }
            }
            batch.commit();
        } else {
            GuestCartManager.getInstance(requireContext()).removeItems(toRemove);
        }

        cartItems.removeAll(toRemove);
        renderList();
        updateFooter();
        ToastUtils.show(getContext(), "Đã xóa sản phẩm hết hàng");
    }

    @Override
    public void onSelectChanged(CartItem item, boolean selected) {
        updateFooter();

        boolean allSelected = !cartItems.isEmpty();
        for (CartItem i : cartItems) {
            if (!i.isSelected()) { allSelected = false; break; }
        }
        syncSelectAllCheckboxes(allSelected);
    }

    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        updateFooter();

        if (userId != null && item.getId() != null) {
            db.collection("users").document(userId).collection("cart")
                    .document(item.getId())
                    .update("quantity", newQuantity);
        } else if (userId == null) {
            GuestCartManager.getInstance(requireContext()).updateQuantity(item.getId(), newQuantity);
        }
    }

    @Override
    public void onRemove(CartItem item) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.cart_remove_title)
                .setMessage(R.string.cart_remove_msg)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    cartItems.remove(item);
                    if (adapter != null) adapter.notifyDataSetChanged();
                    renderList();
                    updateFooter();

                    if (userId != null && item.getId() != null) {
                        db.collection("users").document(userId).collection("cart")
                                .document(item.getId())
                                .delete();
                    } else if (userId == null) {
                        GuestCartManager.getInstance(requireContext()).removeItem(item.getId());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEditVariant(CartItem item) {
        EditCartItemBottomSheet sheet = new EditCartItemBottomSheet(item,
                (weight, flavor, packageType, quantity, price, variantId, variantName, stock) -> {
                    item.setWeight(weight);
                    item.setFlavor(flavor);
                    item.setPackageType(packageType);
                    item.setQuantity(quantity);
                    item.setPrice(price);
                    item.setOriginalPrice(price);
                    item.setVariantId(variantId);
                    item.setVariantName(variantName);
                    item.setStock(Math.max(0, stock));
                    
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    updateFooter();

                    if (userId != null && item.getId() != null) {
                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                        updates.put("weight", weight);
                        updates.put("flavor", flavor);
                        updates.put("packageType", packageType);
                        updates.put("quantity", quantity);
                        updates.put("price", price);
                        updates.put("originalPrice", price);
                        updates.put("variantId", variantId);
                        updates.put("variantName", variantName);
                        updates.put("stock", Math.max(0, stock));
                        updates.put("updatedAt", com.google.firebase.Timestamp.now());
                        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                            updates.put("imageUrl", item.getImageUrl());
                        }
                        db.collection("users").document(userId).collection("cart")
                                .document(item.getId())
                                .update(updates)
                                .addOnSuccessListener(v -> hydrateCartStock(this::renderList));
                    } else if (userId == null) {
                        GuestCartManager.getInstance(requireContext()).updateItem(item);
                        hydrateCartStock(this::renderList);
                    }
                });
        sheet.show(getChildFragmentManager(), "edit_cart_item");
    }

    @Override
    public void onItemClick(CartItem item) {
        if (item.getProductId() != null) {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra("productId", item.getProductId());
            startActivity(intent);
        }
    }

    @Override
    public void onToggleFavorite(CartItem item) {
        if (!isAdded() || item == null || item.getProductId() == null) return;
        if (userId == null) {
            Toast.makeText(getContext(), getString(R.string.wishlist_login_required), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean newState = !item.isFavorite();
        item.setFavorite(newState);
        if (adapter != null) adapter.notifyDataSetChanged();

        if (newState) {
            Map<String, Object> data = new HashMap<>();
            data.put("productId", item.getProductId());
            data.put("addedAt", FieldValue.serverTimestamp());
            db.collection("users").document(userId)
                    .collection("wishlist").document(item.getProductId())
                    .set(data)
                    .addOnFailureListener(e -> revertFavoriteState(item, !newState));
        } else {
            db.collection("users").document(userId)
                    .collection("wishlist").document(item.getProductId())
                    .delete()
                    .addOnFailureListener(e -> revertFavoriteState(item, !newState));
        }
    }

    private void revertFavoriteState(CartItem item, boolean previousState) {
        if (!isAdded()) return;
        item.setFavorite(previousState);
        if (adapter != null) adapter.notifyDataSetChanged();
        Toast.makeText(getContext(), getString(R.string.wishlist_update_failed), Toast.LENGTH_SHORT).show();
    }

    private void updateFooter() {
        if (!isAdded() || tvTotalPrice == null || btnCheckout == null) {
            return;
        }


        double itemsTotal = 0;
        double savings = 0;
        int selectedCount = 0;


        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                itemsTotal += item.getPrice() * item.getQuantity();
                if (item.getOriginalPrice() > item.getPrice()) {
                    savings += (item.getOriginalPrice() - item.getPrice()) * item.getQuantity();
                }
                selectedCount++;
            }
        }


        // ✅ ĐỒNG BỘ LOGIC: Tại giỏ hàng, chỉ trừ tiền giảm của Voucher Sản phẩm vào tổng tiền
        // Vì phí vận chuyển chưa được cộng vào, nên không được trừ voucher vận chuyển ở đây.
        double totalItemDiscount = 0;
        int validVoucherCount = 0;
        for (Voucher v : selectedVouchers) {
            if (itemsTotal >= v.getMinOrderAmount()) {
                validVoucherCount++;
                if (v.getType() != Voucher.Type.SHIPPING) {
                    totalItemDiscount += calculateSavingForFooter(v, itemsTotal);
                }
            }
        }


        double finalTotal = Math.max(0, itemsTotal - totalItemDiscount);


        tvTotalPrice.setText(String.format(Locale.getDefault(), "%sđ", currencyFormat.format(finalTotal)));
        
        View view = getView();
        if (view != null) {
            TextView tvVoucherHint = view.findViewById(R.id.tvVoucherHint);
            if (tvVoucherHint != null) {
                if (validVoucherCount > 0) {
                    tvVoucherHint.setText(String.format(Locale.getDefault(), "Đã áp dụng %d voucher", validVoucherCount));
                    tvVoucherHint.setTextColor(Color.parseColor("#36873A"));
                } else {
                    tvVoucherHint.setText(R.string.cart_voucher_hint);
                    tvVoucherHint.setTextColor(Color.parseColor("#36873A"));
                }
            }
        }
        btnCheckout.setText(String.format(Locale.getDefault(), "Tiếp tục (%d)", selectedCount));

        if (tvSavings != null) {
            if (savings > 0) {
                tvSavings.setVisibility(View.VISIBLE);
                tvSavings.setText(getString(R.string.cart_savings_format, currencyFormat.format(savings) + "đ"));
            } else {
                tvSavings.setVisibility(View.GONE);
            }
        }

        if (tvCartTitle != null) {
            tvCartTitle.setText(String.format(Locale.getDefault(), "Giỏ hàng (%d)", cartItems.size()));
        }

        // Cập nhật badge ở Navbar ngay lập tức
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).updateCartBadge(cartItems.size());
        }
    }

    private double calculateSavingForFooter(Voucher v, double itemsTotal) {
        double val = v.getDiscountAmount();
        if (v.getType() == Voucher.Type.SHIPPING) {
            if (val == 100) return 21000;
            if (val > 0 && val < 100) return (val / 100.0) * 21000;
            return Math.min(val, 21000);
        } else {
            if (val > 0 && val <= 100) return (val / 100.0) * itemsTotal;
            return val;
        }
    }

    private void deleteSelectedItems() {
        List<CartItem> toRemove = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.isSelected()) toRemove.add(item);
        }
        if (toRemove.isEmpty()) {
            ToastUtils.show(getContext(), "Chưa chọn sản phẩm nào để xóa");
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xóa các mục đã chọn")
                .setMessage("Bạn có chắc chắn muốn xóa " + toRemove.size() + " sản phẩm đã chọn?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    if (userId != null) {
                        WriteBatch batch = db.batch();
                        for (CartItem item : toRemove) {
                            if (item.getId() != null) {
                                batch.delete(db.collection("users").document(userId)
                                        .collection("cart").document(item.getId()));
                            }
                        }
                        batch.commit();
                    } else {
                        GuestCartManager.getInstance(requireContext()).removeItems(toRemove);
                    }

                    cartItems.removeAll(toRemove);
                    renderList();
                    updateFooter();
                    ToastUtils.show(getContext(), "Đã xóa sản phẩm thành công");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveSelectedItemsToWishlist() {
        List<CartItem> toSave = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.isSelected()) toSave.add(item);
        }
        if (toSave.isEmpty()) {
            ToastUtils.show(getContext(), getString(R.string.cart_select_items_required));
            return;
        }
        if (userId == null) {
            ToastUtils.show(getContext(), getString(R.string.wishlist_login_required));
            return;
        }

        List<CartItem> validItems = new ArrayList<>();
        WriteBatch batch = db.batch();
        for (CartItem item : toSave) {
            if (item.getProductId() == null || item.getProductId().isEmpty()) continue;

            Map<String, Object> data = new HashMap<>();
            data.put("productId", item.getProductId());
            data.put("addedAt", FieldValue.serverTimestamp());
            batch.set(db.collection("users").document(userId)
                    .collection("wishlist").document(item.getProductId()), data);

            validItems.add(item);
        }

        if (validItems.isEmpty()) {
            return;
        }

        batch.commit().addOnSuccessListener(unused -> {
            if (!isAdded()) return;
            for (CartItem item : validItems) {
                item.setFavorite(true);
            }
            if (adapter != null) adapter.notifyDataSetChanged();
            ToastUtils.show(getContext(), getString(R.string.cart_saved_to_wishlist));
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                ToastUtils.show(getContext(), getString(R.string.wishlist_update_failed));
            }
        });
    }

    private void goToCheckout() {
        List<CartItem> selectedItems = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.isSelected()) selectedItems.add(item);
        }

        if (selectedItems.isEmpty()) {
            ToastUtils.show(getContext(), "Vui lòng chọn ít nhất 1 sản phẩm");
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openPhoneVerification(selectedItems);
            return;
        }

        PhoneVerifiedHelper.requireForCheckout(new PhoneVerifiedHelper.Callback() {
            @Override
            public void onVerified() {
                if (!isAdded()) {
                    return;
                }
                Bundle bundle = new Bundle();
                bundle.putSerializable("selected_items",
                        (Serializable) com.example.healthup.util.CheckoutIntentHelper.toIntentSafeItems(selectedItems));
                bundle.putSerializable("selected_vouchers", (Serializable) selectedVouchers);

                CheckoutFragment fragment = new CheckoutFragment();
                fragment.setArguments(bundle);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onNeedPhoneVerification() {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(getContext(), R.string.checkout_need_phone_verified, Toast.LENGTH_LONG).show();
                openPhoneVerification(selectedItems);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(getContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openPhoneVerification(List<CartItem> selectedItems) {
        CheckoutIntentHelper.savePendingCheckout(requireContext(), selectedItems);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new PhoneVerificationFragment())
                .addToBackStack(null)
                .commit();
    }
}
