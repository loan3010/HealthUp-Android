package com.example.healthup.util;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.FirebaseManager;
import com.example.healthup.MainActivity;
import com.example.healthup.ProductAdapter;
import com.example.healthup.ProductDetailActivity;
import com.example.healthup.ProductListFragment;
import com.example.healthup.R;
import com.example.healthup.VariantBottomSheetFragment;
import com.example.models.Product;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the shared "Gợi ý cho bạn" section for guest utility screens
 * (address book, wishlist, loyalty) with disabled wishlist and guest cart support.
 */
public final class GuestRecommendationsHelper {

    private GuestRecommendationsHelper() {
    }

    public static void bind(@NonNull View root, @NonNull Fragment host) {
        View section = root.findViewById(R.id.lnRecommend);
        RecyclerView rvRecommend = root.findViewById(R.id.rvRecommend);
        View tvViewAll = root.findViewById(R.id.tvViewAllRecommend);
        MaterialButton btnMore = root.findViewById(R.id.btnMoreRecommend);

        if (section == null || rvRecommend == null || !host.isAdded()) {
            return;
        }

        section.setVisibility(View.VISIBLE);

        final List<Product> allProducts = new ArrayList<>();
        final List<Product> displayed = new ArrayList<>();
        final ProductAdapter[] adapterHolder = new ProductAdapter[1];

        ProductAdapter.OnProductClickListener listener = new ProductAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product) {
                if (!host.isAdded() || product == null || product.getId() == null) return;
                Intent intent = new Intent(host.requireContext(), ProductDetailActivity.class);
                intent.putExtra("productId", product.getId());
                host.startActivity(intent);
            }

            @Override
            public void onAddToCart(Product product) {
                if (!host.isAdded() || product == null) return;
                VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(
                        product,
                        (variant, quantity) -> CartHelper.addToCart(
                                host.requireContext(), product, variant, quantity,
                                new CartHelper.CartCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (host.isAdded()) {
                                            Toast.makeText(host.requireContext(),
                                                    R.string.added_to_cart, Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                    }
                                }));
                sheet.show(host.getChildFragmentManager(), "GuestRecommendVariant");
            }

            @Override
            public void onFavoriteClick(Product product) {
                if (!host.isAdded()) return;
                Toast.makeText(
                        host.requireContext(),
                        R.string.wishlist_login_required,
                        Toast.LENGTH_SHORT).show();
            }
        };

        ProductAdapter adapter = new ProductAdapter(displayed, listener);
        adapter.setWishlistDisabled(true);
        adapterHolder[0] = adapter;
        rvRecommend.setLayoutManager(new GridLayoutManager(host.getContext(), 2));
        rvRecommend.setAdapter(adapter);
        rvRecommend.setNestedScrollingEnabled(false);

        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v -> openCategoryTab(host));
        }

        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                btnMore.setVisibility(View.GONE);
                int currentSize = displayed.size();
                for (int i = currentSize; i < allProducts.size(); i++) {
                    displayed.add(allProducts.get(i));
                }
                if (adapterHolder[0] != null) {
                    adapterHolder[0].updateData(new ArrayList<>(displayed));
                }
            });
        }

        FirebaseManager.getInstance().getProducts().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!host.isAdded()) return;

            allProducts.clear();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                try {
                    Product product = Product.fromDocument(doc);
                    if (product != null) {
                        product.setFavorite(false);
                        allProducts.add(product);
                    }
                } catch (Exception ignored) {
                }
            }

            displayed.clear();
            int limit = Math.min(allProducts.size(), 4);
            for (int i = 0; i < limit; i++) {
                displayed.add(allProducts.get(i));
            }

            if (adapterHolder[0] != null) {
                adapterHolder[0].updateData(new ArrayList<>(displayed));
            }

            if (btnMore != null) {
                btnMore.setVisibility(allProducts.size() <= 4 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private static void openCategoryTab(@NonNull Fragment host) {
        if (!(host.getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) host.getActivity();
        BottomNavigationView navView = activity.findViewById(R.id.bottom_navigation);
        if (navView != null) {
            navView.setSelectedItemId(R.id.nav_category);
        }

        ProductListFragment fragment = new ProductListFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putString("category", "Tất cả");
        fragment.setArguments(args);

        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
