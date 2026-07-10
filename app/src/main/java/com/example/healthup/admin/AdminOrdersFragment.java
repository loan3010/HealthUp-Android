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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminOrdersFragment extends Fragment implements AdminOrderAdapter.Listener {

    private static final int OVERDUE_HOURS = 24;
    private static final List<String> SORT_KEYS = Arrays.asList(
            AdminOrderListHelper.SORT_NEWEST,
            AdminOrderListHelper.SORT_OLDEST
    );

    private final AdminRepository repository = new AdminRepository();
    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();
    private final Map<String, AdminRepository.AdminCustomer> customerLookup = new HashMap<>();

    private AdminOrderAdapter adapter;
    private TextView tvEmpty;
    private TextView tvResultSummary;
    private TextView tvOrderAlert;
    private MaterialCardView cardOrderAlert;
    private TextInputEditText etSearch;
    private Spinner spinnerSort;
    private ChipGroup chipGroup;
    private Chip chipAll, chipPending, chipCancelRequested, chipOverdue, chipConfirmed;
    private Chip chipShipping, chipDelivered, chipReturned, chipCancelled;

    private String currentFilter = AdminOrderSearchHelper.FILTER_ALL;
    private String currentSort = AdminOrderListHelper.SORT_NEWEST;
    private boolean suppressChipCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyOrders);
        tvResultSummary = view.findViewById(R.id.tvOrderResultSummary);
        tvOrderAlert = view.findViewById(R.id.tvOrderAlert);
        cardOrderAlert = view.findViewById(R.id.cardOrderAlert);
        etSearch = view.findViewById(R.id.etSearchOrders);
        spinnerSort = view.findViewById(R.id.spinnerOrderSort);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminOrders);
        chipGroup = view.findViewById(R.id.chipGroupOrderStatus);

        chipAll = view.findViewById(R.id.chipAllOrders);
        chipPending = view.findViewById(R.id.chipPendingOrders);
        chipCancelRequested = view.findViewById(R.id.chipCancelRequestedOrders);
        chipOverdue = view.findViewById(R.id.chipOverdueOrders);
        chipConfirmed = view.findViewById(R.id.chipConfirmedOrders);
        chipShipping = view.findViewById(R.id.chipShippingOrders);
        chipDelivered = view.findViewById(R.id.chipDeliveredOrders);
        chipReturned = view.findViewById(R.id.chipReturnedOrders);
        chipCancelled = view.findViewById(R.id.chipCancelledOrders);

        adapter = new AdminOrderAdapter(filteredOrders, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        List<String> sortLabels = Arrays.asList(
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
                    applyFilter();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID || suppressChipCallback) return;
            if (checkedId == R.id.chipPendingOrders) {
                currentFilter = Order.STATUS_PENDING;
            } else if (checkedId == R.id.chipCancelRequestedOrders) {
                currentFilter = AdminOrderSearchHelper.FILTER_CANCEL_REQUESTED;
            } else if (checkedId == R.id.chipOverdueOrders) {
                currentFilter = AdminOrderSearchHelper.FILTER_OVERDUE;
            } else if (checkedId == R.id.chipConfirmedOrders) {
                currentFilter = Order.STATUS_CONFIRMED;
            } else if (checkedId == R.id.chipShippingOrders) {
                currentFilter = Order.STATUS_SHIPPING;
            } else if (checkedId == R.id.chipDeliveredOrders) {
                currentFilter = Order.STATUS_DELIVERED;
            } else if (checkedId == R.id.chipReturnedOrders) {
                currentFilter = AdminOrderSearchHelper.FILTER_RETURNED;
            } else if (checkedId == R.id.chipCancelledOrders) {
                currentFilter = Order.STATUS_CANCELLED;
            } else {
                currentFilter = AdminOrderSearchHelper.FILTER_ALL;
            }
            applyFilter();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        syncChipToCurrentFilter();
    }

    public void applyStatusFilter(@Nullable String statusFilter) {
        currentFilter = statusFilter != null ? statusFilter : AdminOrderSearchHelper.FILTER_ALL;
        if (!isAdded() || getView() == null || chipGroup == null) return;
        syncChipToCurrentFilter();
        applyFilter();
    }

    private void syncChipToCurrentFilter() {
        if (chipGroup == null) return;
        suppressChipCallback = true;
        chipGroup.check(resolveChipIdForFilter(currentFilter));
        suppressChipCallback = false;
    }

    private int resolveChipIdForFilter(@NonNull String filter) {
        if (Order.STATUS_PENDING.equals(filter)) {
            return R.id.chipPendingOrders;
        }
        if (AdminOrderSearchHelper.FILTER_CANCEL_REQUESTED.equals(filter)) {
            return R.id.chipCancelRequestedOrders;
        }
        if (AdminOrderSearchHelper.FILTER_OVERDUE.equals(filter)) {
            return R.id.chipOverdueOrders;
        }
        if (Order.STATUS_CONFIRMED.equals(filter)) {
            return R.id.chipConfirmedOrders;
        }
        if (Order.STATUS_SHIPPING.equals(filter)) {
            return R.id.chipShippingOrders;
        }
        if (Order.STATUS_DELIVERED.equals(filter)) {
            return R.id.chipDeliveredOrders;
        }
        if (AdminOrderSearchHelper.FILTER_RETURNED.equals(filter)) {
            return R.id.chipReturnedOrders;
        }
        if (Order.STATUS_CANCELLED.equals(filter)) {
            return R.id.chipCancelledOrders;
        }
        return R.id.chipAllOrders;
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
                applyFilter();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        if (!isAdded() || getView() == null || adapter == null || tvEmpty == null) return;

        String query = etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "";
        filteredOrders.clear();
        for (Order order : allOrders) {
            if (AdminOrderSearchHelper.matches(order, query, currentFilter, customerLookup)) {
                filteredOrders.add(order);
            }
        }
        AdminOrderListHelper.sort(filteredOrders, currentSort);
        adapter.setOverdueThresholdMs(AdminOrderListHelper.hoursToMillis(OVERDUE_HOURS));
        adapter.notifyDataSetChanged();

        updateChipCounts(query);
        updateSummaryAndAlert(query);

        tvEmpty.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateChipCounts(String query) {
        Map<String, Integer> counts = AdminOrderListHelper.buildFilterCounts(allOrders, query, customerLookup);
        if (chipAll != null) {
            chipAll.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_filter_all), counts.getOrDefault(AdminOrderSearchHelper.FILTER_ALL, 0)));
        }
        if (chipPending != null) {
            chipPending.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_pending), counts.getOrDefault(Order.STATUS_PENDING, 0)));
        }
        if (chipCancelRequested != null) {
            chipCancelRequested.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_cancel_requested),
                    counts.getOrDefault(AdminOrderSearchHelper.FILTER_CANCEL_REQUESTED, 0)));
        }
        if (chipOverdue != null) {
            chipOverdue.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_filter_overdue),
                    counts.getOrDefault(AdminOrderSearchHelper.FILTER_OVERDUE, 0)));
        }
        if (chipConfirmed != null) {
            chipConfirmed.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_confirmed), counts.getOrDefault(Order.STATUS_CONFIRMED, 0)));
        }
        if (chipShipping != null) {
            chipShipping.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_shipping), counts.getOrDefault(Order.STATUS_SHIPPING, 0)));
        }
        if (chipDelivered != null) {
            chipDelivered.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_delivered), counts.getOrDefault(Order.STATUS_DELIVERED, 0)));
        }
        if (chipReturned != null) {
            chipReturned.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_returned), counts.getOrDefault(AdminOrderSearchHelper.FILTER_RETURNED, 0)));
        }
        if (chipCancelled != null) {
            chipCancelled.setText(AdminOrderListHelper.formatChipLabel(
                    getString(R.string.admin_status_cancelled), counts.getOrDefault(Order.STATUS_CANCELLED, 0)));
        }
    }

    private void updateSummaryAndAlert(String query) {
        if (tvResultSummary == null || cardOrderAlert == null || tvOrderAlert == null) return;

        int showing = filteredOrders.size();
        int totalInFilter = AdminOrderListHelper.countForFilter(allOrders, currentFilter, query, customerLookup);
        String filterLabel = filterLabelFor(currentFilter);
        tvResultSummary.setText(getString(R.string.admin_orders_result_summary, showing, totalInFilter, filterLabel));

        long threshold = AdminOrderListHelper.hoursToMillis(OVERDUE_HOURS);
        int overdueAll = AdminOrderListHelper.countOverdue(allOrders, threshold);
        int overdueInFilter = AdminOrderListHelper.countOverdueForFilter(allOrders, currentFilter, threshold);

        if (overdueAll > 0) {
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

    private String filterLabelFor(String filter) {
        if (Order.STATUS_PENDING.equals(filter)) return getString(R.string.admin_status_pending);
        if (AdminOrderSearchHelper.FILTER_CANCEL_REQUESTED.equals(filter)) return getString(R.string.admin_status_cancel_requested);
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
}
