package com.example.healthup.admin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminOrdersFragment extends Fragment implements AdminOrderAdapter.Listener {

    private static final int OVERDUE_HOURS = 24;
    private static final String PREFS_NAME = "admin_orders_alert";
    private static final String PREF_DISMISSED_OVERDUE_COUNT = "dismissed_overdue_count";

    private static final List<String> SORT_KEYS = Arrays.asList(
            AdminOrderListHelper.SORT_PRIORITY,
            AdminOrderListHelper.SORT_NEWEST,
            AdminOrderListHelper.SORT_OLDEST
    );

    private static final List<String> FILTERS = Arrays.asList(
            AdminOrderSearchHelper.FILTER_ALL,
            Order.STATUS_PENDING,
            AdminOrderSearchHelper.FILTER_OVERDUE,
            Order.STATUS_CONFIRMED,
            Order.STATUS_SHIPPING,
            Order.STATUS_DELIVERED,
            AdminOrderSearchHelper.FILTER_RETURNED,
            Order.STATUS_CANCELLED
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<Order> allOrders = new ArrayList<>();
    private final Map<String, AdminRepository.AdminCustomer> customerLookup = new HashMap<>();

    private TextView tvResultSummary;
    private TextView tvOrderAlert;
    private MaterialCardView cardOrderAlert;
    private ImageButton btnDismissOrderAlert;
    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private ViewPager2 viewPager;
    private TabLayout tabFilters;
    private TabLayoutMediator tabMediator;
    private OrderPagerAdapter pagerAdapter;

    private String currentFilter = AdminOrderSearchHelper.FILTER_ALL;
    private String currentSort = AdminOrderListHelper.SORT_PRIORITY;
    private int lastShownOverdueCount;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            currentFilter = FILTERS.get(position);
            updateTabLabels(currentQuery());
            updateSummaryAndAlert(currentQuery());
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvResultSummary = view.findViewById(R.id.tvOrderResultSummary);
        tvOrderAlert = view.findViewById(R.id.tvOrderAlert);
        cardOrderAlert = view.findViewById(R.id.cardOrderAlert);
        btnDismissOrderAlert = view.findViewById(R.id.btnDismissOrderAlert);
        etSearch = view.findViewById(R.id.etSearchOrders);
        spinnerSort = view.findViewById(R.id.spinnerOrderSort);
        viewPager = view.findViewById(R.id.vpAdminOrders);
        tabFilters = view.findViewById(R.id.tabOrderFilters);

        if (btnDismissOrderAlert != null) {
            btnDismissOrderAlert.setOnClickListener(v -> dismissOrderAlert());
        }

        List<String> sortLabels = Arrays.asList(
                getString(R.string.admin_sort_order_priority),
                getString(R.string.admin_sort_order_newest),
                getString(R.string.admin_sort_order_oldest)
        );
        spinnerSort.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, sortLabels));
        spinnerSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                if (position >= 0 && position < SORT_KEYS.size()) {
                    currentSort = SORT_KEYS.get(position);
                    refreshPagesAndSummary();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        pagerAdapter = new OrderPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(FILTERS.size());
        viewPager.registerOnPageChangeCallback(pageChangeCallback);

        tabMediator = new TabLayoutMediator(tabFilters, viewPager, (tab, position) ->
                tab.setText(baseLabelForFilter(FILTERS.get(position))));
        tabMediator.attach();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPagesAndSummary();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        viewPager.setCurrentItem(indexForFilter(currentFilter), false);
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

    public void applyStatusFilter(@Nullable String statusFilter) {
        currentFilter = statusFilter != null ? statusFilter : AdminOrderSearchHelper.FILTER_ALL;
        if (!isAdded() || getView() == null || viewPager == null) return;
        viewPager.setCurrentItem(indexForFilter(currentFilter), false);
        refreshPagesAndSummary();
    }

    private int indexForFilter(@NonNull String filter) {
        int index = FILTERS.indexOf(filter);
        return Math.max(0, index);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
        repository.loadCustomerLookup(new AdminRepository.CustomerLookupCallback() {
            @Override
            public void onSuccess(@NonNull Map<String, AdminRepository.AdminCustomer> lookup) {
                if (!isAdded()) return;
                customerLookup.clear();
                customerLookup.putAll(lookup);
                fetchOrders();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                fetchOrders();
            }
        });
    }

    private void fetchOrders() {
        repository.loadOrders(new AdminRepository.OrdersCallback() {
            @Override
            public void onSuccess(@NonNull List<Order> orders) {
                if (!isAdded()) return;
                allOrders.clear();
                allOrders.addAll(orders);
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
        updateTabLabels(currentQuery());
        updateSummaryAndAlert(currentQuery());
    }

    private String currentQuery() {
        return etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "";
    }

    private List<Order> buildPageOrders(@NonNull String filter) {
        String query = currentQuery();
        List<Order> result = new ArrayList<>();
        for (Order order : allOrders) {
            if (AdminOrderSearchHelper.matches(order, query, filter, customerLookup)) {
                result.add(order);
            }
        }
        AdminOrderListHelper.sort(result, currentSort);
        return result;
    }

    private void updateTabLabels(String query) {
        if (tabFilters == null) return;
        Map<String, Integer> counts = AdminOrderListHelper.buildFilterCounts(allOrders, query, customerLookup);
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

    private void updateSummaryAndAlert(String query) {
        if (tvResultSummary == null || cardOrderAlert == null || tvOrderAlert == null) return;

        List<Order> filtered = buildPageOrders(currentFilter);
        int showing = filtered.size();
        int totalInFilter = AdminOrderListHelper.countForFilter(allOrders, currentFilter, query, customerLookup);
        String filterLabel = filterLabelFor(currentFilter);
        tvResultSummary.setText(getString(R.string.admin_orders_result_summary, showing, totalInFilter, filterLabel));

        long threshold = AdminOrderListHelper.hoursToMillis(OVERDUE_HOURS);
        int overdueAll = AdminOrderListHelper.countOverdue(allOrders, threshold);
        int overdueInFilter = AdminOrderListHelper.countOverdueForFilter(allOrders, currentFilter, threshold);
        lastShownOverdueCount = overdueAll;

        if (overdueAll > 0 && !isAlertDismissedForCount(overdueAll)) {
            cardOrderAlert.setVisibility(View.VISIBLE);
            if (overdueInFilter > 0 && !AdminOrderSearchHelper.FILTER_ALL.equals(currentFilter)) {
                tvOrderAlert.setText(getString(R.string.admin_orders_overdue_filter, overdueInFilter, OVERDUE_HOURS, filterLabel));
            } else {
                tvOrderAlert.setText(getString(R.string.admin_orders_overdue_all, overdueAll, OVERDUE_HOURS));
            }
        } else {
            cardOrderAlert.setVisibility(View.GONE);
        }
    }

    private void dismissOrderAlert() {
        if (cardOrderAlert != null) {
            cardOrderAlert.setVisibility(View.GONE);
        }
        SharedPreferences prefs = alertPrefs();
        if (prefs != null) {
            prefs.edit().putInt(PREF_DISMISSED_OVERDUE_COUNT, lastShownOverdueCount).apply();
        }
    }

    private boolean isAlertDismissedForCount(int overdueCount) {
        SharedPreferences prefs = alertPrefs();
        if (prefs == null) {
            return false;
        }
        // Stay hidden until overdue count increases again (new delayed orders).
        return overdueCount > 0 && overdueCount <= prefs.getInt(PREF_DISMISSED_OVERDUE_COUNT, 0);
    }

    @Nullable
    private SharedPreferences alertPrefs() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String filterLabelFor(String filter) {
        if (Order.STATUS_PENDING.equals(filter)) return getString(R.string.admin_status_pending);
        if (AdminOrderSearchHelper.FILTER_OVERDUE.equals(filter)) return getString(R.string.admin_filter_overdue);
        if (Order.STATUS_CONFIRMED.equals(filter)) return getString(R.string.admin_status_confirmed);
        if (Order.STATUS_SHIPPING.equals(filter)) return getString(R.string.admin_status_shipping);
        if (Order.STATUS_DELIVERED.equals(filter)) return getString(R.string.admin_status_delivered);
        if (AdminOrderSearchHelper.FILTER_RETURNED.equals(filter)) return getString(R.string.admin_status_returned);
        if (Order.STATUS_CANCELLED.equals(filter)) return getString(R.string.admin_status_cancelled);
        return getString(R.string.admin_filter_all);
    }

    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(requireContext(), AdminOrderDetailActivity.class);
        intent.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(intent);
    }

    private class OrderPagerAdapter extends RecyclerView.Adapter<OrderPagerAdapter.PageHolder> {

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
            holder.bind(buildPageOrders(filter), getString(R.string.admin_empty_orders));
        }

        @Override
        public int getItemCount() {
            return FILTERS.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            private final List<Order> pageItems = new ArrayList<>();
            private final AdminOrderAdapter adapter;
            private final TextView tvEmpty;

            PageHolder(@NonNull View itemView) {
                super(itemView);
                RecyclerView rv = itemView.findViewById(R.id.rvFilterPage);
                tvEmpty = itemView.findViewById(R.id.tvFilterPageEmpty);
                adapter = new AdminOrderAdapter(pageItems, AdminOrdersFragment.this);
                adapter.setOverdueThresholdMs(AdminOrderListHelper.hoursToMillis(OVERDUE_HOURS));
                rv.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                rv.setAdapter(adapter);
            }

            void bind(@NonNull List<Order> orders, @NonNull String emptyText) {
                pageItems.clear();
                pageItems.addAll(orders);
                adapter.setOverdueThresholdMs(AdminOrderListHelper.hoursToMillis(OVERDUE_HOURS));
                adapter.notifyDataSetChanged();
                tvEmpty.setText(emptyText);
                tvEmpty.setVisibility(pageItems.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }
}
