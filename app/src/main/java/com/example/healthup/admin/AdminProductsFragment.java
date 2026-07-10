package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminProductsFragment extends Fragment implements AdminProductAdapter.Listener {

    public static final String FILTER_ALL = "all";
    public static final String FILTER_ACTIVE = "active";
    public static final String FILTER_HIDDEN = "hidden";
    public static final String FILTER_DRAFT = "draft";
    public static final String FILTER_LOW_STOCK = "low_stock";

    private static final int REQUEST_EDIT = 1001;
    private static final int LOW_STOCK_THRESHOLD = 10;

    private static final List<String> SORT_KEYS = Arrays.asList(
            "name_asc", "name_desc", "price_asc", "price_desc", "stock_asc", "stock_desc"
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> filteredProducts = new ArrayList<>();
    private AdminProductAdapter adapter;
    private TextView tvEmpty;
    private TextView tvResultSummary;
    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private Chip chipAll;
    private Chip chipActive;
    private Chip chipHidden;
    private Chip chipDraft;
    private String productFilter = FILTER_ALL;
    private boolean suppressChipCallback;

    public void applyProductFilter(@Nullable String filter) {
        productFilter = filter != null ? filter : FILTER_ALL;
        if (!isAdded() || getView() == null) return;
        ChipGroup chipGroup = getView().findViewById(R.id.chipGroupProductFilter);
        if (chipGroup == null) return;
        suppressChipCallback = true;
        if (FILTER_LOW_STOCK.equals(productFilter) || FILTER_ACTIVE.equals(productFilter)) {
            chipGroup.check(R.id.chipActiveProducts);
        } else if (FILTER_HIDDEN.equals(productFilter)) {
            chipGroup.check(R.id.chipHiddenProducts);
        } else if (FILTER_DRAFT.equals(productFilter)) {
            chipGroup.check(R.id.chipDraftProducts);
        } else {
            chipGroup.check(R.id.chipAllProducts);
        }
        suppressChipCallback = false;
        applyFilters();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyProducts);
        tvResultSummary = view.findViewById(R.id.tvProductResultSummary);
        etSearch = view.findViewById(R.id.etSearchProducts);
        spinnerSort = view.findViewById(R.id.spinnerProductSort);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminProducts);
        FloatingActionButton fab = view.findViewById(R.id.fabAddProduct);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupProductFilter);
        chipAll = view.findViewById(R.id.chipAllProducts);
        chipActive = view.findViewById(R.id.chipActiveProducts);
        chipHidden = view.findViewById(R.id.chipHiddenProducts);
        chipDraft = view.findViewById(R.id.chipDraftProducts);

        adapter = new AdminProductAdapter(filteredProducts, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        List<String> sortLabels = Arrays.asList(
                getString(R.string.admin_sort_name_asc),
                getString(R.string.admin_sort_name_desc),
                getString(R.string.admin_sort_price_asc),
                getString(R.string.admin_sort_price_desc),
                getString(R.string.admin_sort_stock_asc),
                getString(R.string.admin_sort_stock_desc)
        );
        spinnerSort.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels));
        spinnerSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view1, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID || suppressChipCallback) return;
            if (checkedId == R.id.chipActiveProducts) {
                productFilter = FILTER_ACTIVE;
            } else if (checkedId == R.id.chipHiddenProducts) {
                productFilter = FILTER_HIDDEN;
            } else if (checkedId == R.id.chipDraftProducts) {
                productFilter = FILTER_DRAFT;
            } else {
                productFilter = FILTER_ALL;
            }
            applyFilters();
        });

        fab.setOnClickListener(v -> openEditScreen(null));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
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
                applyFilters();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        filteredProducts.clear();
        String q = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        for (Product product : allProducts) {
            if (!matchesProductFilter(product)) continue;
            if (!q.isEmpty()) {
                String name = product.getName() != null ? product.getName().toLowerCase(Locale.ROOT) : "";
                String category = product.getCategory() != null ? product.getCategory().toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !category.contains(q)) {
                    continue;
                }
            }
            filteredProducts.add(product);
        }

        sortProducts();
        adapter.notifyDataSetChanged();
        updateSummaryAndChips(q);
        tvEmpty.setVisibility(filteredProducts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesProductFilter(Product product) {
        if (FILTER_LOW_STOCK.equals(productFilter)) {
            return !product.isDraft() && !product.isHidden() && product.getStock() < LOW_STOCK_THRESHOLD;
        }
        if (FILTER_ACTIVE.equals(productFilter)) {
            return !product.isHidden() && !product.isDraft();
        }
        if (FILTER_HIDDEN.equals(productFilter)) {
            return product.isHidden() && !product.isDraft();
        }
        if (FILTER_DRAFT.equals(productFilter)) {
            return product.isDraft();
        }
        return true;
    }

    private void updateSummaryAndChips(String query) {
        if (tvResultSummary != null) {
            tvResultSummary.setText(getString(R.string.admin_products_result_summary,
                    filteredProducts.size(), filterLabelFor(productFilter)));
        }
        Map<String, Integer> counts = buildFilterCounts(query);
        if (chipAll != null) {
            chipAll.setText(AdminOrderListHelper.formatChipLabel(getString(R.string.admin_filter_all),
                    counts.getOrDefault(FILTER_ALL, 0)));
        }
        if (chipActive != null) {
            chipActive.setText(AdminOrderListHelper.formatChipLabel(getString(R.string.admin_filter_active),
                    counts.getOrDefault(FILTER_ACTIVE, 0)));
        }
        if (chipHidden != null) {
            chipHidden.setText(AdminOrderListHelper.formatChipLabel(getString(R.string.admin_filter_hidden),
                    counts.getOrDefault(FILTER_HIDDEN, 0)));
        }
        if (chipDraft != null) {
            chipDraft.setText(AdminOrderListHelper.formatChipLabel(getString(R.string.admin_filter_draft),
                    counts.getOrDefault(FILTER_DRAFT, 0)));
        }
    }

    private Map<String, Integer> buildFilterCounts(String query) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> filters = Arrays.asList(FILTER_ALL, FILTER_ACTIVE, FILTER_HIDDEN, FILTER_DRAFT);
        for (String filter : filters) {
            int count = 0;
            for (Product product : allProducts) {
                String savedFilter = productFilter;
                productFilter = filter;
                boolean matches = matchesProductFilter(product);
                productFilter = savedFilter;
                if (!matches) continue;
                if (!query.isEmpty()) {
                    String name = product.getName() != null ? product.getName().toLowerCase(Locale.ROOT) : "";
                    String category = product.getCategory() != null ? product.getCategory().toLowerCase(Locale.ROOT) : "";
                    if (!name.contains(query) && !category.contains(query)) continue;
                }
                count++;
            }
            counts.put(filter, count);
        }
        return counts;
    }

    private String filterLabelFor(String filter) {
        if (FILTER_ACTIVE.equals(filter)) return getString(R.string.admin_filter_active);
        if (FILTER_HIDDEN.equals(filter)) return getString(R.string.admin_filter_hidden);
        if (FILTER_DRAFT.equals(filter)) return getString(R.string.admin_filter_draft);
        if (FILTER_LOW_STOCK.equals(filter)) return getString(R.string.admin_filter_low_stock);
        return getString(R.string.admin_filter_all);
    }

    private void sortProducts() {
        int sortIndex = spinnerSort != null ? spinnerSort.getSelectedItemPosition() : 0;
        if (sortIndex < 0 || sortIndex >= SORT_KEYS.size()) sortIndex = 0;
        String sortKey = SORT_KEYS.get(sortIndex);
        Comparator<Product> comparator;
        switch (sortKey) {
            case "name_desc":
                comparator = (a, b) -> safeName(b).compareToIgnoreCase(safeName(a));
                break;
            case "price_asc":
                comparator = Comparator.comparingDouble(Product::getPrice);
                break;
            case "price_desc":
                comparator = (a, b) -> Double.compare(b.getPrice(), a.getPrice());
                break;
            case "stock_asc":
                comparator = Comparator.comparingInt(Product::getStock);
                break;
            case "stock_desc":
                comparator = (a, b) -> Integer.compare(b.getStock(), a.getStock());
                break;
            case "name_asc":
            default:
                comparator = (a, b) -> safeName(a).compareToIgnoreCase(safeName(b));
                break;
        }
        filteredProducts.sort(comparator);
    }

    private String safeName(Product product) {
        return product.getName() != null ? product.getName() : "";
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
                .setTitle(R.string.admin_product_action_title)
                .setMessage(R.string.admin_product_action_message)
                .setPositiveButton(R.string.admin_hide_product, (d, w) ->
                        repository.setProductHidden(product.getId(), true, callbackAfterHide()))
                .setNeutralButton(R.string.admin_delete_permanent, (d, w) ->
                        confirmPermanentDelete(product))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private AdminRepository.SimpleCallback callbackAfterHide() {
        return new AdminRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.admin_product_hidden_success, Toast.LENGTH_SHORT).show();
                loadProducts();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void confirmPermanentDelete(Product product) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.admin_delete_permanent_confirm)
                .setPositiveButton(R.string.admin_delete_permanent, (d, w) ->
                        repository.deleteProduct(product.getId(), new AdminRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Đã xóa vĩnh viễn", Toast.LENGTH_SHORT).show();
                                loadProducts();
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
