package com.example.healthup.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminDashboardFragment extends Fragment {

    private final AdminRepository repository = new AdminRepository();
    private final NumberFormat moneyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    private SwipeRefreshLayout swipeRefresh;
    private AdminRepository.DashboardData cachedData;
    private boolean suppressSpinnerCallback;

    private TextView tvTodayDeliveredRevenue;
    private TextView tvTodayPlacedGmv;
    private TextView tvTodayOrdersPlaced;
    private Spinner spinnerPrimaryPeriod;
    private Spinner spinnerComparePeriod;

    private View rowDeliveredRevenue;
    private View rowPlacedGmv;
    private View rowOrdersPlaced;
    private View rowOrdersDelivered;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeDashboard);
        tvTodayDeliveredRevenue = view.findViewById(R.id.tvTodayDeliveredRevenue);
        tvTodayPlacedGmv = view.findViewById(R.id.tvTodayPlacedGmv);
        tvTodayOrdersPlaced = view.findViewById(R.id.tvTodayOrdersPlaced);
        spinnerPrimaryPeriod = view.findViewById(R.id.spinnerPrimaryPeriod);
        spinnerComparePeriod = view.findViewById(R.id.spinnerComparePeriod);

        rowDeliveredRevenue = view.findViewById(R.id.rowDeliveredRevenue);
        rowPlacedGmv = view.findViewById(R.id.rowPlacedGmv);
        rowOrdersPlaced = view.findViewById(R.id.rowOrdersPlaced);
        rowOrdersDelivered = view.findViewById(R.id.rowOrdersDelivered);

        setupActionCards(view);
        setupPeriodSpinners();
        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData();
    }

    private void setupActionCards(@NonNull View view) {
        MaterialCardView cardPending = view.findViewById(R.id.cardActionPending);
        MaterialCardView cardCancel = view.findViewById(R.id.cardActionCancel);
        MaterialCardView cardOverdue = view.findViewById(R.id.cardActionOverdue);
        MaterialCardView cardReturn = view.findViewById(R.id.cardActionReturn);
        MaterialCardView cardLowStock = view.findViewById(R.id.cardActionLowStock);

        cardPending.setOnClickListener(v -> navigateToOrders(Order.STATUS_PENDING));
        cardCancel.setOnClickListener(v -> navigateToOrders(AdminOrderSearchHelper.FILTER_CANCEL_REQUESTED));
        cardOverdue.setOnClickListener(v -> navigateToOrders(AdminOrderSearchHelper.FILTER_OVERDUE));
        cardReturn.setOnClickListener(v -> navigateToOrders(AdminOrderSearchHelper.FILTER_RETURNED));
        cardLowStock.setOnClickListener(v -> navigateToProducts("low_stock"));
    }

    private void setupPeriodSpinners() {
        List<String> labels = new ArrayList<>();
        for (String key : AdminDashboardPeriodHelper.PRESET_KEYS) {
            labels.add(labelForPreset(key));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinnerPrimaryPeriod.setAdapter(adapter);
        spinnerComparePeriod.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(labels)));

        int defaultPrimaryIndex = AdminDashboardPeriodHelper.PRESET_KEYS.indexOf(AdminDashboardPeriodHelper.THIS_MONTH);
        int defaultCompareIndex = AdminDashboardPeriodHelper.PRESET_KEYS.indexOf(AdminDashboardPeriodHelper.LAST_MONTH);
        if (defaultPrimaryIndex < 0) defaultPrimaryIndex = 0;
        if (defaultCompareIndex < 0) defaultCompareIndex = 0;
        spinnerPrimaryPeriod.setSelection(defaultPrimaryIndex);
        spinnerComparePeriod.setSelection(defaultCompareIndex);

        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinnerCallback || cachedData == null) return;
                if (parent.getId() == R.id.spinnerPrimaryPeriod) {
                    String primaryKey = AdminDashboardPeriodHelper.PRESET_KEYS.get(position);
                    String suggested = AdminDashboardPeriodHelper.defaultCompareKey(primaryKey);
                    int compareIndex = AdminDashboardPeriodHelper.PRESET_KEYS.indexOf(suggested);
                    if (compareIndex >= 0 && spinnerComparePeriod.getSelectedItemPosition() == spinnerPrimaryPeriod.getSelectedItemPosition()) {
                        suppressSpinnerCallback = true;
                        spinnerComparePeriod.setSelection(compareIndex);
                        suppressSpinnerCallback = false;
                    }
                }
                renderPeriodComparison();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        spinnerPrimaryPeriod.setOnItemSelectedListener(listener);
        spinnerComparePeriod.setOnItemSelectedListener(listener);
    }

    private void loadData() {
        swipeRefresh.setRefreshing(true);
        repository.loadDashboardData(new AdminRepository.DashboardDataCallback() {
            @Override
            public void onSuccess(@NonNull AdminRepository.DashboardData data) {
                if (!isAdded()) return;
                cachedData = data;
                swipeRefresh.setRefreshing(false);
                bindDashboard(data);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void bindDashboard(@NonNull AdminRepository.DashboardData data) {
        bindActionCards(data);
        bindTodaySection(data.orders);
        renderPeriodComparison();
    }

    private void bindActionCards(@NonNull AdminRepository.DashboardData data) {
        if (getView() == null) return;
        setActionText(R.id.tvActionPending, R.string.admin_dashboard_action_pending, data.pendingOrders);
        setActionText(R.id.tvActionCancel, R.string.admin_dashboard_action_cancel, data.cancelRequestedOrders);
        setActionText(R.id.tvActionOverdue, R.string.admin_dashboard_action_overdue, data.overdueOrders);
        setActionText(R.id.tvActionReturn, R.string.admin_dashboard_action_return, data.returnRequests);
        setActionText(R.id.tvActionLowStock, R.string.admin_dashboard_action_low_stock, data.lowStockProducts);
    }

    private void setActionText(int textViewId, int labelRes, int count) {
        TextView tv = getView().findViewById(textViewId);
        if (tv != null) {
            tv.setText(getString(labelRes, count));
        }
    }

    private void bindTodaySection(@NonNull List<Order> orders) {
        AdminDashboardPeriodHelper.DateRange today = AdminDashboardPeriodHelper.resolve(AdminDashboardPeriodHelper.TODAY);
        AdminDashboardAnalytics.PeriodMetrics metrics = AdminDashboardAnalytics.compute(orders, today);
        tvTodayDeliveredRevenue.setText(AdminDashboardAnalytics.formatMoney(metrics.deliveredRevenue, moneyFormat));
        tvTodayPlacedGmv.setText(AdminDashboardAnalytics.formatMoney(metrics.placedGmv, moneyFormat));
        tvTodayOrdersPlaced.setText(String.valueOf(metrics.ordersPlaced));
    }

    private void renderPeriodComparison() {
        if (cachedData == null || !isAdded()) return;

        int primaryIndex = spinnerPrimaryPeriod.getSelectedItemPosition();
        int compareIndex = spinnerComparePeriod.getSelectedItemPosition();
        if (primaryIndex < 0 || compareIndex < 0) return;

        String primaryKey = AdminDashboardPeriodHelper.PRESET_KEYS.get(primaryIndex);
        String compareKey = AdminDashboardPeriodHelper.PRESET_KEYS.get(compareIndex);

        AdminDashboardPeriodHelper.DateRange primaryRange = AdminDashboardPeriodHelper.resolve(primaryKey);
        AdminDashboardPeriodHelper.DateRange compareRange = AdminDashboardPeriodHelper.resolve(compareKey);

        AdminDashboardAnalytics.PeriodMetrics primary = AdminDashboardAnalytics.compute(cachedData.orders, primaryRange);
        AdminDashboardAnalytics.PeriodMetrics compare = AdminDashboardAnalytics.compute(cachedData.orders, compareRange);

        String compareLabel = labelForPreset(compareKey);

        bindMetricRow(rowDeliveredRevenue,
                getString(R.string.admin_dashboard_delivered_revenue_short),
                primary.deliveredRevenue, compare.deliveredRevenue,
                compareLabel);
        bindMetricRow(rowPlacedGmv,
                getString(R.string.admin_dashboard_placed_gmv_short),
                primary.placedGmv, compare.placedGmv,
                compareLabel);
        bindMetricRow(rowOrdersPlaced,
                getString(R.string.admin_dashboard_orders_placed_short),
                primary.ordersPlaced, compare.ordersPlaced,
                compareLabel, true);
        bindMetricRow(rowOrdersDelivered,
                getString(R.string.admin_dashboard_orders_delivered_short),
                primary.ordersDelivered, compare.ordersDelivered,
                compareLabel, true);
    }

    private void bindMetricRow(@NonNull View row,
                               @NonNull String label,
                               double primaryValue,
                               double compareValue,
                               @NonNull String compareLabel) {
        bindMetricRow(row, label, primaryValue, compareValue, compareLabel, false);
    }

    private void bindMetricRow(@NonNull View row,
                               @NonNull String label,
                               double primaryValue,
                               double compareValue,
                               @NonNull String compareLabel,
                               boolean asCount) {
        TextView tvLabel = row.findViewById(R.id.tvMetricLabel);
        TextView tvPrimary = row.findViewById(R.id.tvMetricPrimary);
        TextView tvCompare = row.findViewById(R.id.tvMetricCompare);

        tvLabel.setText(label);
        if (asCount) {
            tvPrimary.setText(String.valueOf((int) primaryValue));
            String delta = AdminDashboardAnalytics.formatCountDelta((int) primaryValue, (int) compareValue);
            boolean positive = AdminDashboardAnalytics.isPositiveCountDelta((int) primaryValue, (int) compareValue);
            tvCompare.setText(getString(R.string.admin_dashboard_metric_vs_count,
                    compareLabel, (int) compareValue, delta));
            tintDelta(tvCompare, positive, delta);
        } else {
            tvPrimary.setText(AdminDashboardAnalytics.formatMoney(primaryValue, moneyFormat));
            String delta = AdminDashboardAnalytics.formatMoneyDelta(primaryValue, compareValue);
            boolean positive = AdminDashboardAnalytics.isPositiveDelta(primaryValue, compareValue);
            tvCompare.setText(getString(R.string.admin_dashboard_metric_vs_money,
                    compareLabel, AdminDashboardAnalytics.formatMoney(compareValue, moneyFormat), delta));
            tintDelta(tvCompare, positive, delta);
        }
    }

    private void tintDelta(@NonNull TextView tv, boolean positive, @NonNull String delta) {
        if ("—".equals(delta) || "mới".equals(delta)) {
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            return;
        }
        int color = positive ? R.color.brand_primary : R.color.error;
        tv.setTextColor(ContextCompat.getColor(requireContext(), color));
    }

    private String labelForPreset(@NonNull String key) {
        switch (key) {
            case AdminDashboardPeriodHelper.YESTERDAY:
                return getString(R.string.admin_period_yesterday);
            case AdminDashboardPeriodHelper.LAST_7_DAYS:
                return getString(R.string.admin_period_last_7_days);
            case AdminDashboardPeriodHelper.THIS_WEEK:
                return getString(R.string.admin_period_this_week);
            case AdminDashboardPeriodHelper.LAST_WEEK:
                return getString(R.string.admin_period_last_week);
            case AdminDashboardPeriodHelper.THIS_MONTH:
                return getString(R.string.admin_period_this_month);
            case AdminDashboardPeriodHelper.LAST_MONTH:
                return getString(R.string.admin_period_last_month);
            case AdminDashboardPeriodHelper.THIS_YEAR:
                return getString(R.string.admin_period_this_year);
            case AdminDashboardPeriodHelper.TODAY:
            default:
                return getString(R.string.admin_period_today);
        }
    }

    private void navigateToOrders(@Nullable String filter) {
        if (getActivity() instanceof AdminNavigator) {
            ((AdminNavigator) getActivity()).openOrders(filter);
        }
    }

    private void navigateToProducts(@Nullable String filter) {
        if (getActivity() instanceof AdminNavigator) {
            ((AdminNavigator) getActivity()).openProducts(filter);
        }
    }
}
