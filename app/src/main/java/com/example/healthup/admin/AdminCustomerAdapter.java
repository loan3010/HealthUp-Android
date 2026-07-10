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
        void onCustomerClick(AdminRepository.AdminCustomer customer);
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
        holder.tvPhone.setText(formatContact(customer));
        holder.tvSpent.setText("Đã chi: " + format.format(customer.spentAmount) + " VND");

        holder.switchDisabled.setOnCheckedChangeListener(null);
        holder.switchDisabled.setChecked(customer.disabled);
        holder.switchDisabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                listener.onToggleDisabled(customer, isChecked));

        View mainArea = holder.itemView.findViewById(R.id.layoutCustomerMain);
        if (mainArea != null) {
            mainArea.setOnClickListener(v -> listener.onCustomerClick(customer));
        } else {
            holder.itemView.setOnClickListener(v -> listener.onCustomerClick(customer));
        }
    }

    @Override
    public int getItemCount() {
        return customers.size();
    }

    private String formatContact(AdminRepository.AdminCustomer customer) {
        StringBuilder builder = new StringBuilder();
        if (customer.phone != null && !customer.phone.trim().isEmpty()) {
            builder.append(customer.phone.trim());
        }
        if (customer.email != null && !customer.email.trim().isEmpty()) {
            if (builder.length() > 0) builder.append(" • ");
            builder.append(customer.email.trim());
        }
        return builder.length() > 0 ? builder.toString() : "—";
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
