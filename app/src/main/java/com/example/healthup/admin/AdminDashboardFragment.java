package com.example.healthup.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.healthup.R;

import java.text.NumberFormat;
import java.util.Locale;

public class AdminDashboardFragment extends Fragment {

    private AdminRepository repository = new AdminRepository();
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvProducts, tvOrders, tvRevenue, tvPending, tvLowStock;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeDashboard);
        tvProducts = view.findViewById(R.id.tvStatProducts);
        tvOrders = view.findViewById(R.id.tvStatOrders);
        tvRevenue = view.findViewById(R.id.tvStatRevenue);
        tvPending = view.findViewById(R.id.tvStatPending);
        tvLowStock = view.findViewById(R.id.tvStatLowStock);

        swipeRefresh.setOnRefreshListener(this::loadStats);
        loadStats();
    }

    private void loadStats() {
        swipeRefresh.setRefreshing(true);
        repository.loadDashboard(new AdminRepository.DashboardCallback() {
            @Override
            public void onSuccess(@NonNull AdminRepository.DashboardStats stats) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                NumberFormat format = NumberFormat.getInstance(new Locale("vi", "VN"));
                tvProducts.setText(String.valueOf(stats.productCount));
                tvOrders.setText(String.valueOf(stats.orderCount));
                tvRevenue.setText(format.format(stats.revenue) + " đ");
                tvPending.setText(String.valueOf(stats.pendingOrders));
                tvLowStock.setText(String.valueOf(stats.lowStockProducts));
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
            }
        });
    }
}
