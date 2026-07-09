package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Product;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AdminProductsFragment extends Fragment implements AdminProductAdapter.Listener {

    private static final int REQUEST_EDIT = 1001;

    private final AdminRepository repository = new AdminRepository();
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> filteredProducts = new ArrayList<>();
    private AdminProductAdapter adapter;
    private TextView tvEmpty;
    private TextInputEditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyProducts);
        etSearch = view.findViewById(R.id.etSearchProducts);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminProducts);
        FloatingActionButton fab = view.findViewById(R.id.fabAddProduct);

        adapter = new AdminProductAdapter(filteredProducts, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        fab.setOnClickListener(v -> openEditScreen(null));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterProducts(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProducts();
    }

    private void loadProducts() {
        repository.loadProducts(new AdminRepository.ProductsCallback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded()) return;
                allProducts.clear();
                allProducts.addAll(products);
                filterProducts(etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "");
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterProducts(String query) {
        filteredProducts.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (Product product : allProducts) {
            if (q.isEmpty()
                    || product.getName().toLowerCase().contains(q)
                    || product.getCategory().toLowerCase().contains(q)) {
                filteredProducts.add(product);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredProducts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditScreen(@Nullable Product product) {
        Intent intent = new Intent(requireContext(), AdminProductEditActivity.class);
        if (product != null) {
            intent.putExtra(AdminProductEditActivity.EXTRA_PRODUCT_ID, product.getId());
        }
        startActivityForResult(intent, REQUEST_EDIT);
    }

    @Override
    public void onProductClick(Product product) {
        openEditScreen(product);
    }

    @Override
    public void onDeleteClick(Product product) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.admin_delete_confirm)
                .setPositiveButton("Xóa", (d, w) -> repository.deleteProduct(product.getId(), new AdminRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Đã xóa", Toast.LENGTH_SHORT).show();
                        loadProducts();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("Hủy", null)
                .show();
    }
}
