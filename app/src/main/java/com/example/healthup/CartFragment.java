package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.CartAdapter;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneVerifiedHelper;
import com.example.models.CartItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment implements CartAdapter.Listener {

    private List<CartItem> cartItems = new ArrayList<>();
    private CartAdapter adapter;

    private RecyclerView rvCartItems;
    private View emptyState, footer, btnDeleteSelected, rowVoucher, btnContinueShopping, guestSyncBanner;
    private ProgressBar progressBar;
    private CheckBox cbSelectAll;
    private TextView tvTotalPrice, btnCheckout, btnBackFooter, tvCartTitle;

    private FirebaseFirestore db;
    private String userId;

    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        bindViews(view);
        applyHeaderWindowInsets(view);
        setupListeners();
        loadCartFromFirestore();

        return view;
    }

    @Override
    public void onDestroyView() {
        adapter = null;
        super.onDestroyView();
    }

    private void applyHeaderWindowInsets(View view) {
        View header = view.findViewById(R.id.header);
        if (header == null) {
            return;
        }

        final int basePaddingStart = header.getPaddingStart();
        final int basePaddingTop = header.getPaddingTop();
        final int basePaddingEnd = header.getPaddingEnd();
        final int basePaddingBottom = header.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPaddingRelative(
                    basePaddingStart,
                    basePaddingTop + systemBars.top,
                    basePaddingEnd,
                    basePaddingBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(header);
    }

    private void bindViews(View view) {
        rvCartItems = view.findViewById(R.id.rvCartItems);
        emptyState = view.findViewById(R.id.emptyState);
        footer = view.findViewById(R.id.footer);
        cbSelectAll = view.findViewById(R.id.cbSelectAll);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);
        tvTotalPrice = view.findViewById(R.id.tvTotalPrice);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        btnBackFooter = view.findViewById(R.id.btnBackFooter);
        tvCartTitle = view.findViewById(R.id.tvCartTitle);
        rowVoucher = view.findViewById(R.id.rowVoucher);
        btnContinueShopping = view.findViewById(R.id.btnContinueShopping);
        guestSyncBanner = view.findViewById(R.id.guestSyncBanner);

        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupListeners() {
        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                for (CartItem item : cartItems) item.setSelected(isChecked);
                if (adapter != null) adapter.notifyDataSetChanged();
                updateFooter();
            });
        }

        if (btnDeleteSelected != null) {
            btnDeleteSelected.setOnClickListener(v -> deleteSelectedItems());
        }
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(v -> goToCheckout());
        }
        if (btnBackFooter != null) {
            btnBackFooter.setOnClickListener(v -> {
                if (!isAdded()) return;
                
                // Thử quay lại màn hình trước đó trong stack
                boolean movedBack = requireActivity().getSupportFragmentManager().popBackStackImmediate();
                
                // Nếu không có gì để quay lại (đang ở tab giỏ hàng), chuyển về tab Trang chủ
                if (!movedBack) {
                    com.google.android.material.bottomnavigation.BottomNavigationView navView = 
                        requireActivity().findViewById(R.id.bottom_navigation);
                    if (navView != null) {
                        navView.setSelectedItemId(R.id.nav_home);
                    }
                }
            });
        }
        if (rowVoucher != null) {
            rowVoucher.setOnClickListener(v -> openVoucherList());
        }
        if (btnContinueShopping != null) {
            btnContinueShopping.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ProductListFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }
    }

    private void openVoucherList() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new PromoCouponFragment())
                .addToBackStack(null)
                .commit();
    }

    private void loadCartFromFirestore() {
        if (userId == null) {
            loadGuestCart();
            return;
        }

        // Bỏ orderBy ở query Firestore vì nếu 1 document thiếu field 'updatedAt', 
        // nó sẽ bị Firestore loại bỏ khỏi kết quả trả về, gây lệch số lượng với Badge.
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
        java.util.Map<String, Boolean> selection = new java.util.HashMap<>();
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

        // Sắp xếp theo updatedAt giảm dần trong bộ nhớ (để document không có field này vẫn hiện ở cuối)
        java.util.Collections.sort(loadedItems, (o1, o2) -> {
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

    private String getStringOrMapLabel(com.google.firebase.firestore.DocumentSnapshot doc, String field) {
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

        // Fix: Trích xuất nhãn sạch từ Firestore (nếu là Map)
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

        // Kiểm tra tính hợp lệ: Phải có productId và name
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

    private void refreshCartUi() {
        if (!isAdded()) {
            return;
        }
        renderList();
        updateFooter();
    }

    private void renderList() {
        if (!isAdded() || emptyState == null || rvCartItems == null || footer == null) {
            return;
        }

        if (cartItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        rvCartItems.setVisibility(View.VISIBLE);
        footer.setVisibility(View.VISIBLE);
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
        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(allSelected);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
            android.content.Intent intent = new android.content.Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra("productId", item.getProductId());
            startActivity(intent);
        }
    }

    private void updateFooter() {
        if (!isAdded() || tvTotalPrice == null || btnCheckout == null) {
            return;
        }

        double total = 0;
        int selectedCount = 0;

        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                total += item.getPrice() * item.getQuantity();
                selectedCount++;
            }
        }

        tvTotalPrice.setText(currencyFormat.format(total) + "đ");
        btnCheckout.setText("Tiếp tục (" + selectedCount + ")");

        if (tvCartTitle != null) {
            tvCartTitle.setText("Giỏ hàng (" + cartItems.size() + ")");
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
                        com.google.firebase.firestore.WriteBatch batch = db.batch();
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
