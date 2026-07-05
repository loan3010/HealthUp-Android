package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.healthup.databinding.FragmentWishlistBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class WishlistFragment extends Fragment {
    private FragmentWishlistBinding binding;
    private FirestoreManager firestoreManager;
    private ProductAdapter adapter;
    private final List<Product> wishlistProducts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWishlistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firestoreManager = FirestoreManager.getInstance();

        setupRecyclerView();
        loadWishlist();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        binding.btnGoShopping.setOnClickListener(v -> {
            // Navigate to Home or Product List
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(wishlistProducts, false);
        binding.rvWishlist.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.rvWishlist.setAdapter(adapter);
    }

    private void loadWishlist() {
        firestoreManager.getWishlist(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                wishlistProducts.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    // In a real app, wishlist items might just be product IDs
                    // Here we assume the wishlist collection stores basic product info or we fetch details
                    Product p = new Product();
                    p.setId(doc.getString("productId"));
                    p.setName(doc.getString("productName"));
                    Double price = doc.getDouble("productPrice");
                    p.setPrice(price != null ? price : 0.0);
                    List<String> imgs = new ArrayList<>();
                    imgs.add(doc.getString("productImage"));
                    p.setImages(imgs);
                    
                    wishlistProducts.add(p);
                }
                adapter.notifyDataSetChanged();
                updateUI();
            }
        });
    }

    private void updateUI() {
        if (wishlistProducts.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.rvWishlist.setVisibility(View.GONE);
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.rvWishlist.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
