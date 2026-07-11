package com.example.healthup;


import android.content.Intent;
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
import com.example.models.Voucher;
import com.example.healthup.firebase.FirestoreManager;
import com.example.healthup.util.CartHelper;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneVerifiedHelper;
import com.example.models.CartItem;
import com.example.models.Product;
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
    private List<Voucher> selectedVouchers = new ArrayList<>();
    private CartAdapter adapter;
    private ProductAdapter recommendAdapter;


    private RecyclerView rvCartItems, rvCartRecommendations;
    private View emptyState, footer, rowVoucher, btnContinueShopping, guestSyncBanner;
    private View totalsRow, editRow;
    private View btnSaveToWishlist, btnDeleteSelected;
    private CheckBox cbSelectAllEdit;
    private TextView tvTotalPrice, btnCheckout, btnBackFooter, tvCartTitle, tvEditToggle, tvViewAllRecommend;
    private ImageButton btnBack;


    private FirebaseFirestore db;
    private String userId;
    private boolean editMode = false;


    // FIX (bug #4): true khi Cart được mở từ nút "Xem giỏ hàng" trong ProductDetailActivity.
    // Khi đó nút "Quay lại"/mũi tên back phải finish() Activity này để trở về đúng màn Chi
    // tiết sản phẩm, thay vì chuyển tab Trang chủ như luồng vào Giỏ hàng bình thường.
    private boolean returnToPreviousActivity = false;


    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Nhận kết quả chọn voucher để hiển thị giảm giá ngay tại Giỏ hàng
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
            // ✅ Bỏ basePaddingTop dư thừa để header đi lên cao nhất có thể
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }


    // FIX (bug #5): footer trước đây không nhận padding bottom theo system bar, nên bị thanh
    // điều hướng cử chỉ / thanh nav hệ thống che khuất một phần. Áp dụng inset bottom cho
    // footer giống cách header đã nhận inset top.
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
        btnSaveToWishlist = view.findViewById(R.id.btnSaveToWishlist);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);
        tvTotalPrice = view.findViewById(R.id.tvTotalPrice);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        btnBackFooter = view.findViewById(R.id.btnBackFooter);
        btnBack = view.findViewById(R.id.btnBack);
        tvCartTitle = view.findViewById(R.id.tvCartTitle);
        tvEditToggle = view.findViewById(R.id.tvEditToggle);
        tvViewAllRecommend = view.findViewById(R.id.tvViewAllRecommend);
        rowVoucher = view.findViewById(R.id.rowVoucher);
        btnContinueShopping = view.findViewById(R.id.btnContinueShopping);
        guestSyncBanner = view.findViewById(R.id.guestSyncBanner);


        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCartItems.setNestedScrollingEnabled(false);


        rvCartRecommendations.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvCartRecommendations.setNestedScrollingEnabled(false);
    }


    private void setupListeners() {
        if (cbSelectAllEdit != null) {
            cbSelectAllEdit.setOnCheckedChangeListener((buttonView, isChecked) -> {
                for (CartItem item : cartItems) item.setSelected(isChecked);
                if (adapter != null) adapter.notifyDataSetChanged();
                updateFooter();
            });
        }


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
        if (btnBackFooter != null) {
            btnBackFooter.setOnClickListener(v -> goBack());
        }
        if (tvEditToggle != null) {
            tvEditToggle.setOnClickListener(v -> setEditMode(!editMode));
        }
        if (rowVoucher != null) {
            rowVoucher.setOnClickListener(v -> openVoucherList());
        }
        if (btnContinueShopping != null) {
            btnContinueShopping.setOnClickListener(v -> {
                if (!isAdded()) return;
                com.google.android.material.bottomnavigation.BottomNavigationView navView =
                        requireActivity().findViewById(R.id.bottom_navigation);
                if (navView != null) {
                    navView.setSelectedItemId(R.id.nav_home);
                }
            });
        }
        if (tvViewAllRecommend != null) {
            tvViewAllRecommend.setOnClickListener(v -> goToCategoryTab());
        }
    }


    // FIX (bug #4): xử lý dùng chung cho cả mũi tên back ở header và nút "Quay lại" ở footer,
    // đúng theo layout header kiểu Shopee (Quay lại | Giỏ hàng (n) | Sửa).
    private void goBack() {
        if (!isAdded()) return;


        if (returnToPreviousActivity) {
            requireActivity().finish();
            return;
        }


        boolean movedBack = requireActivity().getSupportFragmentManager().popBackStackImmediate();
        if (!movedBack) {
            com.google.android.material.bottomnavigation.BottomNavigationView navView =
                    requireActivity().findViewById(R.id.bottom_navigation);
            if (navView != null) {
                navView.setSelectedItemId(R.id.nav_home);
            }
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


    // FIX (bug #7): toggle chế độ "Sửa" (giống Shopee) — đổi header text Sửa/Xong và đổi
    // footer giữa 2 trạng thái: bình thường (Tổng tiền/Quay lại/Tiếp tục) và chỉnh sửa
    // (Tất cả/Lưu vào Đã thích/Xóa).
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
            Toast.makeText(getContext(), "Vui lòng chọn sản phẩm để xem voucher áp dụng", Toast.LENGTH_SHORT).show();
            return;
        }


        PromoCouponFragment fragment = new PromoCouponFragment();
        Bundle bundle = new Bundle();
        // Truyền các mã đang chọn để trang Voucher hiển thị đúng trạng thái tích chọn
        bundle.putSerializable("selected_vouchers", new ArrayList<>(selectedVouchers));
        bundle.putDouble("order_total", selectedTotal);
        bundle.putDouble("shipping_fee", 21000); 
        bundle.putBoolean("has_visited", !selectedVouchers.isEmpty());
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
            StringBuilder sb = new StringBuilder("Đã chọn: ");
            for (int i = 0; i < selectedVouchers.size(); i++) {
                sb.append(selectedVouchers.get(i).getCode());
                if (i < selectedVouchers.size() - 1) sb.append(", ");
            }
            tvVoucherInfo.setText(sb.toString());
            tvVoucherInfo.setTextColor(getResources().getColor(R.color.text_dark));
        }
    }


    // FIX (bug #6): thiết lập adapter gợi ý "Có thể bạn quan tâm" ngay trong Giỏ hàng, tham
    // khảo Shopee — luôn hiển thị bất kể giỏ hàng có sản phẩm hay không, tái sử dụng đúng
    // ProductAdapter (có nút yêu thích + thêm giỏ hàng) như các trang khác trong app.
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
                VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(product, (variant, quantity) ->
                        CartHelper.addToCart(requireContext(), product, variant, quantity));
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
                    List<Product> selected = new ArrayList<>(pool.subList(0, Math.min(6, pool.size())));
                    applyWishlistStateToRecommendations(selected);
                });
    }


    private void applyWishlistStateToRecommendations(List<Product> products) {
        if (userId == null) {
            if (recommendAdapter != null) recommendAdapter.updateData(products);
            return;
        }
        WishlistManager.loadFavoriteIds(userId, ids -> {
            if (!isAdded()) return;
            WishlistManager.applyFavoriteState(products, ids);
            if (recommendAdapter != null) recommendAdapter.updateData(products);
        });
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
                        Toast.makeText(getContext(), "Lỗi tải giỏ hàng", Toast.LENGTH_SHORT).show();
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
                        Boolean wasSelected = selection.get(item.getId());
                        item.setSelected(wasSelected != null ? wasSelected : true);
                    }
                    loadedItems.add(item);
                }
            }
        }


        Collections.sort(loadedItems, (o1, o2) -> {
            com.google.firebase.Timestamp t1 = o1.getUpdatedAt();
            com.google.firebase.Timestamp t2 = o2.getUpdatedAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });


        cartItems.clear();
        cartItems.addAll(loadedItems);
        renderList();
        updateFooter();
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
        renderList();
        updateFooter();
    }


    private void updateGuestBanner() {
        if (guestSyncBanner != null) {
            guestSyncBanner.setVisibility(userId == null && !cartItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
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


    // FIX (bug #5/#6/#7): giỏ hàng trống vẫn hiển thị đầy đủ header + footer (Quay lại/Tiếp
    // tục) để không chặn luồng người dùng; đồng thời tự thoát chế độ "Sửa" nếu cartItems rỗng.
    private void renderList() {
        if (!isAdded() || emptyState == null || rvCartItems == null || footer == null) {
            return;
        }


        boolean isEmpty = cartItems.isEmpty();
        if (isEmpty && editMode) {
            setEditMode(false);
        }


        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvCartItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        // ✅ ẨN THANH TỔNG TIỀN (footer) KHI GIỎ HÀNG TRỐNG
        footer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);


        if (tvEditToggle != null) {
            tvEditToggle.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
        }
        if (rowVoucher != null) {
            rowVoucher.setVisibility(!isEmpty && !editMode ? View.VISIBLE : View.GONE);
        }


        updateGuestBanner();


        if (adapter == null) {
            adapter = new CartAdapter(cartItems, this);
        } else {
            adapter.notifyDataSetChanged();
        }
        rvCartItems.setAdapter(adapter);
    }


    @Override
    public void onSelectChanged(CartItem item, boolean selected) {
        updateFooter();


        boolean allSelected = true;
        for (CartItem i : cartItems) {
            if (!i.isSelected()) { allSelected = false; break; }
        }
        if (cbSelectAllEdit != null) {
            cbSelectAllEdit.setOnCheckedChangeListener(null);
            cbSelectAllEdit.setChecked(allSelected);
            cbSelectAllEdit.setOnCheckedChangeListener((buttonView, isChecked) -> {
                for (CartItem i : cartItems) i.setSelected(isChecked);
                if (adapter != null) adapter.notifyDataSetChanged();
                updateFooter();
            });
        }
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
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng?")
                .setPositiveButton("Xóa", (dialog, which) -> {
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
                .setNegativeButton("Hủy", null)
                .show();
    }


    @Override
    public void onEditVariant(CartItem item) {
        EditCartItemBottomSheet sheet = new EditCartItemBottomSheet(item,
                (weight, flavor, packageType, quantity, price) -> {
                    item.setWeight(weight);
                    item.setFlavor(flavor);
                    item.setPackageType(packageType);
                    item.setQuantity(quantity);
                    item.setPrice(price);
                    item.setOriginalPrice(price);
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
                        updates.put("updatedAt", com.google.firebase.Timestamp.now());
                        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                            updates.put("imageUrl", item.getImageUrl());
                        }
                        db.collection("users").document(userId).collection("cart")
                                .document(item.getId())
                                .update(updates);
                    } else if (userId == null) {
                        GuestCartManager.getInstance(requireContext()).updateItem(item);
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


    private void updateFooter() {
        if (!isAdded() || tvTotalPrice == null || btnCheckout == null) {
            return;
        }


        double itemsTotal = 0;
        int selectedCount = 0;


        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                itemsTotal += item.getPrice() * item.getQuantity();
                selectedCount++;
            }
        }


        // Tính toán giảm giá giống bên Checkout
        double totalDiscount = 0;
        for (Voucher v : selectedVouchers) {
            totalDiscount += calculateSavingForFooter(v, itemsTotal);
        }


        double finalTotal = Math.max(0, itemsTotal - totalDiscount);


        tvTotalPrice.setText(currencyFormat.format(finalTotal) + "đ");
        btnCheckout.setText("Tiếp tục (" + selectedCount + ")");


        if (tvCartTitle != null) {
            tvCartTitle.setText("Giỏ hàng (" + cartItems.size() + ")");
        }
    }


    private double calculateSavingForFooter(Voucher v, double itemsTotal) {
        double val = v.getDiscountAmount();
        if (v.getType() == Voucher.Type.SHIPPING) {
            // Tại Giỏ hàng chưa tính phí ship thực tế nên chỉ tính theo phí ship giả định
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
            Toast.makeText(getContext(), "Chưa chọn sản phẩm nào để xóa", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "Đã xóa sản phẩm thành công", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }


    // FIX (bug #7): "Lưu vào Đã thích" ở footer chế độ Sửa — chuyển các sản phẩm đang chọn
    // sang wishlist (users/{uid}/wishlist/{productId}) rồi xóa khỏi giỏ hàng, giống hành vi
    // Shopee. Yêu cầu đăng nhập vì wishlist là dữ liệu theo tài khoản.
    private void saveSelectedItemsToWishlist() {
        List<CartItem> toSave = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.isSelected()) toSave.add(item);
        }
        if (toSave.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.cart_select_items_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId == null) {
            Toast.makeText(getContext(), getString(R.string.wishlist_login_required), Toast.LENGTH_SHORT).show();
            return;
        }


        WriteBatch batch = db.batch();
        for (CartItem item : toSave) {
            if (item.getProductId() == null || item.getProductId().isEmpty()) continue;


            Map<String, Object> data = new HashMap<>();
            data.put("productId", item.getProductId());
            data.put("addedAt", FieldValue.serverTimestamp());
            batch.set(db.collection("users").document(userId)
                    .collection("wishlist").document(item.getProductId()), data);


            // ✅ KHÔNG XOÁ sản phẩm khỏi giỏ hàng nữa
        }


        batch.commit().addOnSuccessListener(unused -> {
            if (!isAdded()) return;
            // Chỉ cần update UI để bỏ chọn các item vừa lưu (hoặc giữ nguyên tuỳ ý)
            Toast.makeText(getContext(), getString(R.string.cart_saved_to_wishlist), Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), getString(R.string.wishlist_update_failed), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void goToCheckout() {
        List<CartItem> selectedItems = new ArrayList<>();
        for (CartItem item : cartItems) {
            if (item.isSelected()) selectedItems.add(item);
        }


        if (selectedItems.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng chọn ít nhất 1 sản phẩm", Toast.LENGTH_SHORT).show();
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
                bundle.putSerializable("selected_items", (Serializable) selectedItems);

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