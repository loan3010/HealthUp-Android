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

import com.example.healthup.adapters.CartAdapter;
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
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CartItem item : cartItems) item.setSelected(isChecked);
            if (adapter != null) adapter.notifyDataSetChanged();
            updateFooter();
        });

        btnDeleteSelected.setOnClickListener(v -> deleteSelectedItems());
        btnCheckout.setOnClickListener(v -> goToCheckout());
        btnBackFooter.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
        rowVoucher.setOnClickListener(v -> openVoucherList());
    }

    private void openVoucherList() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, new PromoCouponFragment())
                .addToBackStack(null)
                .commit();
    }

    // ================== LẤY DỮ LIỆU TỪ FIRESTORE ==================
    private void loadCartFromFirestore() {
        // Dữ liệu mẫu để test giao diện
        cartItems.clear();
        
        CartItem item1 = new CartItem();
        item1.setProductId("p1");
        item1.setName("Hạt Granola siêu ngon");
        item1.setWeight("500g");
        item1.setPackageType("Vị Socola");
        item1.setPrice(150000);
        item1.setOriginalPrice(200000);
        item1.setQuantity(2);
        item1.setSelected(true);
        cartItems.add(item1);

        CartItem item2 = new CartItem();
        item2.setProductId("p2");
        item2.setName("Sữa hạt điều nguyên chất");
        item2.setWeight("1000ml");
        item2.setPrice(85000);
        item2.setOriginalPrice(85000);
        item2.setQuantity(1);
        item2.setSelected(true);
        cartItems.add(item2);

        renderList();
        updateFooter();

        if (userId == null) return;
        // Tiếp tục load từ Firestore nếu có user
        db.collection("users").document(userId).collection("cart")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        cartItems.clear();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            CartItem item = doc.toObject(CartItem.class);
                            item.setId(doc.getId());
                            item.setSelected(true);
                            cartItems.add(item);
                        }
                        renderList();
                        updateFooter();
                    }
                });
    }

    // ================== HIỂN THỊ DANH SÁCH ==================
    private void renderList() {
        if (cartItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        rvCartItems.setVisibility(View.VISIBLE);
        footer.setVisibility(View.VISIBLE);

        adapter = new CartAdapter(cartItems, this);
        rvCartItems.setAdapter(adapter);
    }

    // ================== CALLBACK TỪ ADAPTER ==================
    @Override
    public void onSelectChanged(CartItem item, boolean selected) {
        updateFooter();

        boolean allSelected = true;
        for (CartItem i : cartItems) {
            if (!i.isSelected()) { allSelected = false; break; }
        }
        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(allSelected);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CartItem i : cartItems) i.setSelected(isChecked);
            adapter.notifyDataSetChanged();
            updateFooter();
        });
    }

    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        updateFooter();

        // Cập nhật xuống Firestore
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

                    // Xóa khỏi Firestore
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
                    adapter.notifyDataSetChanged();
                    updateFooter();

                    // Cập nhật xuống Firestore
                    if (userId != null && item.getId() != null) {
                        db.collection("users").document(userId).collection("cart")
                                .document(item.getId())
                                .update("weight", weight, "flavor", flavor,
                                        "packageType", packageType, "quantity", quantity);
                    }
                });
        sheet.show(getChildFragmentManager(), "edit_cart_item");
    }

    // ================== TÍNH TOÁN FOOTER ==================
    private void updateFooter() {
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
                    // Xóa hàng loạt trên Firestore bằng Batch
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

    // ================== CHUYỂN SANG CHECKOUT ==================
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
                .replace(R.id.main_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}