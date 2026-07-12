package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.PaymentAccount;

import java.util.List;

public class PaymentAccountAdapter extends RecyclerView.Adapter<PaymentAccountAdapter.ViewHolder> {

    private final List<PaymentAccount> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PaymentAccount item);
        void onDeleteClick(PaymentAccount item);
    }

    public PaymentAccountAdapter(List<PaymentAccount> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment_account, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentAccount item = items.get(position);
        holder.tvProvider.setText(item.getProviderName());
        holder.tvIdentifier.setText(item.getAccountIdentifier());

        int iconRes = R.drawable.ic_payment_wallet;
        switch (item.getType()) {
            case PaymentAccount.TYPE_MOMO: iconRes = R.drawable.ic_momo; break;
            case PaymentAccount.TYPE_ZALOPAY: iconRes = R.drawable.ic_zalopay; break;
            case PaymentAccount.TYPE_CARD: iconRes = R.drawable.ic_payment_card; break;
            case PaymentAccount.TYPE_ATM:
            case PaymentAccount.TYPE_LINKED_BANK: iconRes = R.drawable.ic_bank; break;
        }
        holder.ivIcon.setImageResource(iconRes);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, btnDelete;
        TextView tvProvider, tvIdentifier;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvProvider = itemView.findViewById(R.id.tvProvider);
            tvIdentifier = itemView.findViewById(R.id.tvIdentifier);
        }
    }
}
