package com.example.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Voucher;

import java.util.List;

public class LoyaltyVoucherAdapter extends RecyclerView.Adapter<LoyaltyVoucherAdapter.ViewHolder> {

    private final Context context;
    private final List<Voucher> voucherList;
    private final OnUseNowClickListener listener;
    private String userTier = "Member";

    public interface OnUseNowClickListener {
        void onUseNow(Voucher voucher);
    }

    public LoyaltyVoucherAdapter(Context context, List<Voucher> voucherList, OnUseNowClickListener listener) {
        this.context = context;
        this.voucherList = voucherList;
        this.listener = listener;
    }

    public void setUserTier(String userTier) {
        this.userTier = userTier;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_loyalty_voucher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Voucher voucher = voucherList.get(position);

        holder.tvVoucherTitle.setText(voucher.getTitle());
        holder.tvVoucherDesc.setText(voucher.getDescription());
        holder.tvVoucherExpiry.setText(String.format(java.util.Locale.getDefault(), "HSD: %s", voucher.getExpiryDate()));

        // Logic check eligibility (VIP check)
        String reqTier = voucher.getRequiredTier();
        boolean isTierMatch = (reqTier == null || reqTier.isEmpty() || reqTier.equalsIgnoreCase("Member") 
                || (userTier != null && userTier.equalsIgnoreCase(reqTier)));
        
        if (!isTierMatch) {
            holder.itemView.setAlpha(0.6f);
            holder.btnUseNow.setVisibility(View.GONE);
            holder.tvIneligibleHint.setVisibility(View.VISIBLE);
            if ("VIP".equalsIgnoreCase(reqTier)) {
                holder.tvIneligibleHint.setText("Mua thêm để lên hạng VIP và nhận ưu đãi này!");
                holder.tvIneligibleHint.setTextColor(Color.parseColor("#D89216")); // Gold
            } else {
                holder.tvIneligibleHint.setText("Dành riêng cho hạng " + reqTier);
                holder.tvIneligibleHint.setTextColor(Color.parseColor("#FF5252"));
            }
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.btnUseNow.setVisibility(View.VISIBLE);
            holder.tvIneligibleHint.setVisibility(View.GONE);
        }

        // Change icon based on type (Shipping or Discount)
        if (voucher.getType() == Voucher.Type.SHIPPING) {
            holder.ivVoucherType.setImageResource(R.drawable.ic_order_shipping);
            holder.ivVoucherType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFF5F5")));
            holder.ivVoucherType.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")));
        } else {
            holder.ivVoucherType.setImageResource(R.drawable.ic_gift);
            holder.ivVoucherType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F5FDF9")));
            holder.ivVoucherType.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#36873A")));
        }

        holder.btnUseNow.setOnClickListener(v -> {
            if (listener != null) listener.onUseNow(voucher);
        });
    }

    @Override
    public int getItemCount() {
        return voucherList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivVoucherType;
        TextView tvVoucherTitle, tvVoucherDesc, tvVoucherExpiry, tvIneligibleHint;
        Button btnUseNow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVoucherType = itemView.findViewById(R.id.ivVoucherType);
            tvVoucherTitle = itemView.findViewById(R.id.tvVoucherTitle);
            tvVoucherDesc = itemView.findViewById(R.id.tvVoucherDesc);
            tvVoucherExpiry = itemView.findViewById(R.id.tvVoucherExpiry);
            tvIneligibleHint = itemView.findViewById(R.id.tvIneligibleHint);
            btnUseNow = itemView.findViewById(R.id.btnUseNow);
        }
    }
}
