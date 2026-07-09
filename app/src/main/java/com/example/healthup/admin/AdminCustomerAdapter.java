package com.example.healthup.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class AdminCustomerAdapter extends RecyclerView.Adapter<AdminCustomerAdapter.ViewHolder> {

    public interface Listener {
        void onToggleDisabled(AdminRepository.AdminCustomer customer, boolean disabled);
    }

    private final List<AdminRepository.AdminCustomer> customers;
    private final Listener listener;
    private final NumberFormat format = NumberFormat.getInstance(new Locale("vi", "VN"));

    public AdminCustomerAdapter(List<AdminRepository.AdminCustomer> customers, Listener listener) {
        this.customers = customers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminRepository.AdminCustomer customer = customers.get(position);
        holder.tvName.setText(customer.fullName != null ? customer.fullName : "Khách hàng");
        holder.tvPhone.setText(customer.phone != null ? customer.phone : (customer.email != null ? customer.email : "—"));
        holder.tvSpent.setText("Đã chi: " + format.format(customer.spentAmount) + " VND");

        holder.switchDisabled.setOnCheckedChangeListener(null);
        holder.switchDisabled.setChecked(customer.disabled);
        holder.switchDisabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                listener.onToggleDisabled(customer, isChecked));
    }

    @Override
    public int getItemCount() {
        return customers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvSpent;
        SwitchMaterial switchDisabled;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvAdminCustomerName);
            tvPhone = itemView.findViewById(R.id.tvAdminCustomerPhone);
            tvSpent = itemView.findViewById(R.id.tvAdminCustomerSpent);
            switchDisabled = itemView.findViewById(R.id.switchCustomerDisabled);
        }
    }
}
