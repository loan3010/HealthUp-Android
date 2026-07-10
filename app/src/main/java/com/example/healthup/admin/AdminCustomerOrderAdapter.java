package com.example.healthup.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Order;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AdminCustomerOrderAdapter extends RecyclerView.Adapter<AdminCustomerOrderAdapter.ViewHolder> {

    public interface Listener {
        void onOrderClick(Order order);
    }

    private final List<Order> orders;
    private final Listener listener;
    private final NumberFormat priceFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminCustomerOrderAdapter(List<Order> orders, Listener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_customer_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.tvCode.setText(order.getOrderCode() != null ? order.getOrderCode() : ("#" + order.getId()));
        holder.tvStatus.setText(AdminUiHelper.statusLabel(order.getStatus()));
        String date = order.getCreatedAt() != null ? dateFormat.format(order.getCreatedAt()) : "";
        holder.tvMeta.setText(date + " • " + priceFormat.format(order.getTotalPrice()) + " đ");
        holder.itemView.setOnClickListener(v -> listener.onOrderClick(order));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvMeta, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvCustomerOrderCode);
            tvMeta = itemView.findViewById(R.id.tvCustomerOrderMeta);
            tvStatus = itemView.findViewById(R.id.tvCustomerOrderStatus);
        }
    }
}
