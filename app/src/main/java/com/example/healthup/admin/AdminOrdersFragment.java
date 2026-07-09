package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Order;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminOrdersFragment extends Fragment implements AdminOrderAdapter.Listener {

    private final AdminRepository repository = new AdminRepository();
    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();
    private AdminOrderAdapter adapter;
    private TextView tvEmpty;
    private String currentFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvEmpty = view.findViewById(R.id.tvEmptyOrders);
        RecyclerView recyclerView = view.findViewById(R.id.rvAdminOrders);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupOrderStatus);

        adapter = new AdminOrderAdapter(filteredOrders, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            if (checkedId == R.id.chipPendingOrders) {
                currentFilter = "pending";
            } else if (checkedId == R.id.chipShippingOrders) {
                currentFilter = "shipping";
            } else if (checkedId == R.id.chipDeliveredOrders) {
                currentFilter = "delivered";
            } else {
                currentFilter = "all";
            }
            applyFilter();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
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
        if (!isAdded() || getView() == null || adapter == null || tvEmpty == null) {
            return;
        }
        filteredOrders.clear();
        for (Order order : allOrders) {
            String status = order.getStatus();
            if (status != null) {
                status = status.toLowerCase(Locale.ROOT).trim();
            }
            if ("all".equals(currentFilter) || (status != null && currentFilter.equals(status))) {
                filteredOrders.add(order);
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(requireContext(), AdminOrderDetailActivity.class);
        intent.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(intent);
    }
}
