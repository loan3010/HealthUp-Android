package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.adapters.CartAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.CartItem;
import com.example.models.Product;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment implements CartAdapter.OnCartItemChangeListener {

    private RecyclerView rvCartItems;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItems = new ArrayList<>();
    private TextView tvTotalPrice;
    private MaterialButton btnCheckout;
    private View layoutEmpty;
    private String userId = "temp_user_id"; // Replace with actual Auth ID

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);
        initViews(view);
        fetchCartItems();
        return view;
    }

    private void initViews(View view) {
        rvCartItems = view.findViewById(R.id.rv_cart_items);
        tvTotalPrice = view.findViewById(R.id.tv_total_price);
        btnCheckout = view.findViewById(R.id.btn_checkout);
        layoutEmpty = view.findViewById(R.id.layout_cart_empty);

        cartAdapter = new CartAdapter(cartItems, this);
        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCartItems.setAdapter(cartAdapter);

        btnCheckout.setOnClickListener(v -> {
            // Proceed to checkout logic
            Toast.makeText(getContext(), "Tiến hành thanh toán", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchCartItems() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        }

        FirestoreManager.getInstance().getFirestore().collection("cart")
                .whereEqualTo("userId", userId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    cartItems.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        CartItem item = doc.toObject(CartItem.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            cartItems.add(item);
                        }
                    }
                    updateUI();
                });
    }

    private void updateUI() {
        if (cartItems.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvCartItems.setVisibility(View.VISIBLE);
        }
        cartAdapter.notifyDataSetChanged();
        calculateTotal();
    }

    private void calculateTotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            double price = (item.getPrice() > 0) ? item.getPrice() : 
                          (item.getProduct() != null ? item.getProduct().getPrice() : 0);
            total += price * item.getQuantity();
        }
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvTotalPrice.setText(formatter.format(total) + "đ");
    }

    @Override
    public void onQuantityChange(CartItem item, int newQuantity) {
        FirestoreManager.getInstance().getFirestore().collection("cart")
                .document(item.getId())
                .update("quantity", newQuantity);
    }

    @Override
    public void onDeleteItem(CartItem item) {
        FirestoreManager.getInstance().getFirestore().collection("cart")
                .document(item.getId())
                .delete();
    }

    @Override
    public void onItemClick(CartItem item) {
        if (item.getProductId() != null) {
            android.content.Intent intent = new android.content.Intent(getContext(), ProductDetailActivity.class);
            // CHỈ truyền productId để tránh lỗi crash do quá tải dữ liệu Intent
            intent.putExtra("productId", item.getProductId());
            startActivity(intent);
        }
    }
}
