package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.healthup.databinding.FragmentProductListBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class ProductListFragment extends Fragment {
    private FragmentProductListBinding binding;
    private FirestoreManager firestoreManager;
    private ProductAdapter adapter;
    private final List<Product> productList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProductListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firestoreManager = FirestoreManager.getInstance();

        setupRecyclerView();
        handleArguments();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(productList, false);
        binding.rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvProducts.setAdapter(adapter);
    }

    private void handleArguments() {
        if (getArguments() != null) {
            String type = getArguments().getString("filter_type");
            String value = getArguments().getString("filter_value");

            binding.tvTitle.setText(value);

            if ("category".equals(type)) {
                fetchByCategory(value);
            } else if ("search".equals(type)) {
                searchProducts(value);
            } else if ("all".equals(type)) {
                fetchAllProducts();
            }
        }
    }

    private void fetchAllProducts() {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getNewProducts(100, task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                productList.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Product p = doc.toObject(Product.class);
                    p.setId(doc.getId());
                    productList.add(p);
                }
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void fetchByCategory(String categoryName) {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getProductsByCategory(categoryName, task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                productList.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Product p = doc.toObject(Product.class);
                    p.setId(doc.getId());
                    productList.add(p);
                }
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private String removeAccents(String s) {
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").toLowerCase().replace('đ', 'd').replace('Đ', 'd');
    }

    private void searchProducts(String query) {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreManager.getNewProducts(100, task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                productList.clear();
                String normalizedQuery = removeAccents(query);
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Product p = doc.toObject(Product.class);
                    p.setId(doc.getId());
                    if (p.getName() != null) {
                        String normalizedName = removeAccents(p.getName());
                        if (normalizedName.contains(normalizedQuery)) {
                            productList.add(p);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(productList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
