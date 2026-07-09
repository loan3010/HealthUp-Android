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

public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.ViewHolder> {

    public interface Listener {
        void onOrderClick(Order order);
    }

    private final List<Order> orders;
    private final Listener listener;
    private final NumberFormat priceFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminOrderAdapter(List<Order> orders, Listener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.tvCode.setText(order.getOrderCode() != null ? order.getOrderCode() : ("#" + order.getId()));
        holder.tvStatus.setText(AdminUiHelper.statusLabel(order.getStatus()));
        holder.tvTotal.setText("Tổng: " + priceFormat.format(order.getTotalPrice()) + " đ");
        holder.tvCustomer.setText("Khách: …");
        if (order.getCreatedAt() != null) {
            holder.tvDate.setText(dateFormat.format(order.getCreatedAt()));
        } else {
            holder.tvDate.setText("");
        }
        holder.itemView.setOnClickListener(v -> listener.onOrderClick(order));

        final int bindPosition = position;
        AdminCustomerResolver.resolve(order, label -> {
            if (bindPosition == holder.getBindingAdapterPosition()) {
                holder.tvCustomer.post(() -> holder.tvCustomer.setText("Khách: " + label));
            }
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvStatus, tvTotal, tvCustomer, tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvAdminOrderCode);
            tvStatus = itemView.findViewById(R.id.tvAdminOrderStatus);
            tvTotal = itemView.findViewById(R.id.tvAdminOrderTotal);
            tvCustomer = itemView.findViewById(R.id.tvAdminOrderCustomer);
            tvDate = itemView.findViewById(R.id.tvAdminOrderDate);
        }
    }
}
