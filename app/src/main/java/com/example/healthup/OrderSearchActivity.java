package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.healthup.databinding.ActivityOrderSearchBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class OrderSearchActivity extends AppCompatActivity {
    private ActivityOrderSearchBinding binding;
    private List<Order> allOrders = new ArrayList<>();
    private List<Order> searchResults = new ArrayList<>();
    private OrderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrderSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        loadAllOrders();
        setupListeners();
        
        binding.etSearch.requestFocus();
    }

    private void setupRecyclerView() {
        adapter = new OrderAdapter(this, searchResults);
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchResults.setAdapter(adapter);
    }

    private void loadAllOrders() {
        FirebaseManager.getInstance().getOrders().addOnSuccessListener(queryDocumentSnapshots -> {
            allOrders.clear();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                Order o = doc.toObject(Order.class);
                if (o != null) {
                    o.setId(doc.getId());
                    allOrders.add(o);
                }
            }
            // Show initial state
            updateEmptyState("Nhập mã đơn hoặc tên sản phẩm để tìm kiếm");
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void performSearch(String query) {
        searchResults.clear();
        if (query.isEmpty()) {
            adapter.notifyDataSetChanged();
            updateEmptyState("Nhập từ khóa để tìm kiếm");
            return;
        }

        String lowerQuery = query.toLowerCase();
        for (Order order : allOrders) {
            boolean match = false;
            
            // Match order code
            if (order.getOrderCode() != null && order.getOrderCode().toLowerCase().contains(lowerQuery)) {
                match = true;
            }
            
            // Match product name
            if (!match && order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    if (item.getName() != null && item.getName().toLowerCase().contains(lowerQuery)) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                searchResults.add(order);
            }
        }

        adapter.notifyDataSetChanged();
        if (searchResults.isEmpty()) {
            updateEmptyState("Không tìm thấy đơn hàng nào khớp với '" + query + "'");
        } else {
            binding.lnEmptyState.setVisibility(View.GONE);
            binding.rvSearchResults.setVisibility(View.VISIBLE);
        }
    }

    private void updateEmptyState(String message) {
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.lnEmptyState.setVisibility(View.VISIBLE);
        binding.tvEmptyMessage.setText(message);
    }
}
