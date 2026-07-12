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
import androidx.viewpager2.widget.ViewPager2;

import com.example.healthup.R;
import com.example.models.Product;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
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

    private static final List<String> FILTERS = Arrays.asList(
            FILTER_ALL, FILTER_ACTIVE, FILTER_HIDDEN, FILTER_DRAFT
    );

    private static final List<String> SORT_KEYS = Arrays.asList(
            "name_asc", "name_desc", "price_asc", "price_desc", "stock_asc", "stock_desc"
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<Product> allProducts = new ArrayList<>();

    private TextView tvResultSummary;
    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private ViewPager2 viewPager;
    private TabLayout tabFilters;
    private TabLayoutMediator tabMediator;
    private ProductPagerAdapter pagerAdapter;

    private String productFilter = FILTER_ALL;
    private boolean lowStockMode;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            String next = FILTERS.get(position);
            if (!FILTER_ACTIVE.equals(next)) {
                lowStockMode = false;
            }
            productFilter = lowStockMode ? FILTER_LOW_STOCK : next;
            updateSummaryAndTabs(currentQuery());
        }
    };

    public void applyProductFilter(@Nullable String filter) {
        productFilter = filter != null ? filter : FILTER_ALL;
        lowStockMode = FILTER_LOW_STOCK.equals(productFilter);
        if (!isAdded() || getView() == null || viewPager == null) return;
        int index = indexForFilter(lowStockMode ? FILTER_ACTIVE : productFilter);
        viewPager.setCurrentItem(index, false);
        refreshPagesAndSummary();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvResultSummary = view.findViewById(R.id.tvProductResultSummary);
        etSearch = view.findViewById(R.id.etSearchProducts);
        spinnerSort = view.findViewById(R.id.spinnerProductSort);
        viewPager = view.findViewById(R.id.vpAdminProducts);
        FloatingActionButton fab = view.findViewById(R.id.fabAddProduct);
        tabFilters = view.findViewById(R.id.tabProductFilters);

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
                refreshPagesAndSummary();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        pagerAdapter = new ProductPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(FILTERS.size());
        viewPager.registerOnPageChangeCallback(pageChangeCallback);

        tabMediator = new TabLayoutMediator(tabFilters, viewPager, (tab, position) ->
                tab.setText(baseLabelForFilter(FILTERS.get(position))));
        tabMediator.attach();

        fab.setOnClickListener(v -> openEditScreen(null));
        fab.setOnLongClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.admin_backfill_codes)
                    .setMessage(R.string.admin_backfill_codes_confirm)
                    .setPositiveButton(R.string.admin_confirm, (d, w) ->
                            repository.backfillMissingProductCodes(new AdminRepository.SimpleCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(requireContext(),
                                            R.string.admin_backfill_codes_done, Toast.LENGTH_SHORT).show();
                                    loadProducts();
                                }

                                @Override
                                public void onError(@NonNull String message) {
                                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                                }
                            }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPagesAndSummary();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        int initialIndex = indexForFilter(lowStockMode ? FILTER_ACTIVE : productFilter);
        viewPager.setCurrentItem(initialIndex, false);
    }

    @Override
    public void onDestroyView() {
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        if (tabMediator != null) {
            tabMediator.detach();
            tabMediator = null;
        }
        super.onDestroyView();
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
                refreshPagesAndSummary();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshPagesAndSummary() {
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }
        updateSummaryAndTabs(currentQuery());
    }

    private String currentQuery() {
        return etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
    }

    private List<Product> buildPageProducts(@NonNull String pageFilter) {
        String effectiveFilter = pageFilter;
        if (FILTER_ACTIVE.equals(pageFilter) && lowStockMode) {
            effectiveFilter = FILTER_LOW_STOCK;
        }
        String q = currentQuery();
        List<Product> result = new ArrayList<>();
        for (Product product : allProducts) {
            if (!matchesProductFilter(product, effectiveFilter)) continue;
            if (!q.isEmpty()) {
                String name = product.getName() != null ? product.getName().toLowerCase(Locale.ROOT) : "";
                String category = product.getCategory() != null ? product.getCategory().toLowerCase(Locale.ROOT) : "";
                if (!name.contains(q) && !category.contains(q)) continue;
            }
            result.add(product);
        }
        sortProducts(result);
        return result;
    }

    private boolean matchesProductFilter(Product product, String filter) {
        if (FILTER_LOW_STOCK.equals(filter)) {
            return !product.isDraft() && !product.isHidden() && product.getStock() < LOW_STOCK_THRESHOLD;
        }
        if (FILTER_ACTIVE.equals(filter)) {
            return !product.isHidden() && !product.isDraft();
        }
        if (FILTER_HIDDEN.equals(filter)) {
            return product.isHidden() && !product.isDraft();
        }
        if (FILTER_DRAFT.equals(filter)) {
            return product.isDraft();
        }
        return true;
    }

    private void updateSummaryAndTabs(String query) {
        List<Product> current = buildPageProducts(FILTERS.get(Math.max(0,
                Math.min(viewPager != null ? viewPager.getCurrentItem() : 0, FILTERS.size() - 1))));
        if (tvResultSummary != null) {
            tvResultSummary.setText(getString(R.string.admin_products_result_summary,
                    current.size(), filterLabelFor(productFilter)));
        }
        if (tabFilters == null) return;
        Map<String, Integer> counts = buildFilterCounts(query);
        for (int i = 0; i < FILTERS.size(); i++) {
            TabLayout.Tab tab = tabFilters.getTabAt(i);
            if (tab == null) continue;
            String filter = FILTERS.get(i);
            tab.setText(AdminOrderListHelper.formatChipLabel(
                    baseLabelForFilter(filter), counts.getOrDefault(filter, 0)));
        }
    }

    private String baseLabelForFilter(String filter) {
        return filterLabelFor(filter);
    }

    private Map<String, Integer> buildFilterCounts(String query) {
        Map<String, Integer> counts = new HashMap<>();
        for (String filter : FILTERS) {
            int count = 0;
            for (Product product : allProducts) {
                if (!matchesProductFilter(product, filter)) continue;
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

    private int indexForFilter(@NonNull String filter) {
        int index = FILTERS.indexOf(filter);
        return Math.max(0, index);
    }

    private void sortProducts(List<Product> products) {
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
        products.sort(comparator);
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

    private class ProductPagerAdapter extends RecyclerView.Adapter<ProductPagerAdapter.PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View page = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_filter_page, parent, false);
            return new PageHolder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            String filter = FILTERS.get(position);
            List<Product> pageProducts = buildPageProducts(filter);
            holder.bind(pageProducts, getString(R.string.admin_empty_products));
        }

        @Override
        public int getItemCount() {
            return FILTERS.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            private final List<Product> pageItems = new ArrayList<>();
            private final AdminProductAdapter adapter;
            private final TextView tvEmpty;

            PageHolder(@NonNull View itemView) {
                super(itemView);
                RecyclerView rv = itemView.findViewById(R.id.rvFilterPage);
                tvEmpty = itemView.findViewById(R.id.tvFilterPageEmpty);
                adapter = new AdminProductAdapter(pageItems, AdminProductsFragment.this);
                rv.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                rv.setAdapter(adapter);
            }

            void bind(@NonNull List<Product> products, @NonNull String emptyText) {
                pageItems.clear();
                pageItems.addAll(products);
                adapter.notifyDataSetChanged();
                tvEmpty.setText(emptyText);
                tvEmpty.setVisibility(pageItems.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }
}
