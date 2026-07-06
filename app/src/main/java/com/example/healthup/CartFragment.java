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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.CartAdapter;
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
    private View emptyState, footer, btnDeleteSelected, rowVoucher;
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
        setupListeners();
        loadCartFromFirestore();

        return view;
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
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }
        if (rowVoucher != null) {
            rowVoucher.setOnClickListener(v -> openVoucherList());
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
        cartItems.clear();
        renderList();
        updateFooter();

        if (userId == null) {
            return;
        }

        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnSuccessListener(this::applyFirestoreCart)
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Không thể tải giỏ hàng", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        userId = FirebaseAuth.getInstance().getUid();
        loadCartFromFirestore();
    }

    private void applyFirestoreCart(com.google.firebase.firestore.QuerySnapshot snapshot) {
        if (!isAdded()) {
            return;
        }

        cartItems.clear();
        for (QueryDocumentSnapshot doc : snapshot) {
            CartItem item = parseCartItem(doc);
            if (item != null) {
                item.setSelected(true);
                cartItems.add(item);
            }
        }
        refreshCartUi();
    }

    private CartItem parseCartItem(QueryDocumentSnapshot doc) {
        CartItem item = null;
        try {
            item = doc.toObject(CartItem.class);
        } catch (RuntimeException ignored) {
            // Nested product maps from add-to-cart can fail CustomClassMapper deserialization.
        }

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
                    if (productName instanceof String) {
                        name = (String) productName;
                    }
                }
            }
            item.setName(name);
        }
        if (item.getVariantName() == null) {
            item.setVariantName(doc.getString("variantName"));
        }
        if (item.getWeight() == null) {
            item.setWeight(doc.getString("weight"));
        }
        if (item.getFlavor() == null) {
            item.setFlavor(doc.getString("flavor"));
        }
        if (item.getPackageType() == null) {
            item.setPackageType(doc.getString("packageType"));
        }
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
        Long stock = doc.getLong("stock");
        if (stock != null) {
            item.setStock(stock.intValue());
        }

        hydrateImageUrl(item, doc);
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

        if (adapter == null) {
            adapter = new CartAdapter(cartItems, this);
            rvCartItems.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
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
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onEditVariant(CartItem item) {
        EditCartItemBottomSheet sheet = new EditCartItemBottomSheet(item,
                (weight, flavor, packageType, quantity) -> {
                    item.setWeight(weight);
                    item.setFlavor(flavor);
                    item.setPackageType(packageType);
                    item.setQuantity(quantity);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    updateFooter();

                    if (userId != null && item.getId() != null) {
                        db.collection("users").document(userId).collection("cart")
                                .document(item.getId())
                                .update("weight", weight, "flavor", flavor,
                                        "packageType", packageType, "quantity", quantity);
                    }
                });
        sheet.show(getChildFragmentManager(), "edit_cart_item");
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
}
