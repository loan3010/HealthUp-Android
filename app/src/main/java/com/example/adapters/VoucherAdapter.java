package com.example.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Voucher;

import java.util.List;

public class VoucherAdapter extends RecyclerView.Adapter<VoucherAdapter.ViewHolder> {

    private Context context;
    private List<Voucher> voucherList;
    private OnVoucherClickListener listener;

    public interface OnVoucherClickListener {
        void onVoucherClick(Voucher voucher);
    }

    public VoucherAdapter(Context context, List<Voucher> voucherList, OnVoucherClickListener listener) {
        this.context = context;
        this.voucherList = voucherList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_voucher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Voucher voucher = voucherList.get(position);

        holder.tvTitle.setText(voucher.getTitle());
        holder.tvDescription.setText(voucher.getDescription());
        holder.tvExpiry.setText(voucher.getExpiryDate());
        holder.rbSelected.setChecked(voucher.isSelected());

        // Customize based on Type
        switch (voucher.getType()) {
            case SHIPPING:
                holder.layoutIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF5F5")));
                holder.imgType.setImageResource(R.drawable.ic_order_shipping);
                holder.imgType.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FF5252")));
                holder.tvTypeText.setText("FREESHIP");
                holder.tvTypeText.setTextColor(Color.parseColor("#FF5252"));
                break;
            case CASHBACK:
                holder.layoutIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F0F7FF")));
                holder.imgType.setImageResource(R.drawable.ic_loyalty);
                holder.imgType.setImageTintList(ColorStateList.valueOf(Color.parseColor("#2E7DFF")));
                holder.tvTypeText.setText("CASHBACK");
                holder.tvTypeText.setTextColor(Color.parseColor("#2E7DFF"));
                break;
            case DISCOUNT:
                holder.layoutIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5FDF9")));
                holder.imgType.setImageResource(R.drawable.ic_favorite);
                holder.imgType.setImageTintList(ColorStateList.valueOf(Color.parseColor("#36873A")));
                holder.tvTypeText.setText("DISCOUNT");
                holder.tvTypeText.setTextColor(Color.parseColor("#36873A"));
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            // Logic: Toggle selection
            voucher.setSelected(!voucher.isSelected());
            notifyItemChanged(position);
            if (listener != null) listener.onVoucherClick(voucher);
        });
    }

    @Override
    public int getItemCount() {
        return voucherList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View layoutIcon;
        ImageView imgType;
        TextView tvTypeText, tvTitle, tvDescription, tvExpiry;
        RadioButton rbSelected;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutIcon = itemView.findViewById(R.id.layoutIcon);
            imgType = itemView.findViewById(R.id.imgType);
            tvTypeText = itemView.findViewById(R.id.tvTypeText);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvExpiry = itemView.findViewById(R.id.tvExpiry);
            rbSelected = itemView.findViewById(R.id.rbSelected);
        }
    }
}
