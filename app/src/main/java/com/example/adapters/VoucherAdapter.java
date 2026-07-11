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
import android.widget.Toast;

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

    private double orderTotal;
    private double shippingFee;
    private String userTier;

    public VoucherAdapter(Context context, List<Voucher> voucherList, double orderTotal, double shippingFee, String userTier, OnVoucherClickListener listener) {
        this.context = context;
        this.voucherList = voucherList;
        this.orderTotal = orderTotal;
        this.shippingFee = shippingFee;
        this.userTier = userTier;
        this.listener = listener;
    }

    public void setUserTier(String userTier) {
        this.userTier = userTier;
        notifyDataSetChanged();
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
        
        // Hiển thị giá trị giảm giá nếu có
        String desc = voucher.getDescription();
        if (voucher.getDiscountAmount() > 0) {
            String valStr = voucher.getDiscountAmount() < 100 ? 
                String.format(java.util.Locale.getDefault(), "%d%%", (int)voucher.getDiscountAmount()) : 
                String.format(java.util.Locale.getDefault(), "%,.0fđ", voucher.getDiscountAmount()).replace(",", ".");
            holder.tvTitle.setText(String.format(java.util.Locale.getDefault(), "%s - Giảm %s", voucher.getTitle(), valStr));
        }
        
        holder.tvDescription.setText(desc);
        holder.tvExpiry.setText(voucher.getExpiryDate());
        holder.rbSelected.setChecked(voucher.isSelected());

        // Kiểm tra điều kiện sử dụng (Shopee style)
        double minAmount = voucher.getMinOrderAmount();
        double currentTotal = orderTotal; // ✅ LUÔN dùng tổng tiền hàng để check điều kiện voucher
        
        String reqTier = voucher.getRequiredTier();
        boolean isTierMatch = (reqTier == null || reqTier.isEmpty() || reqTier.equalsIgnoreCase("Member") 
                || (userTier != null && userTier.equalsIgnoreCase(reqTier)));
        
        boolean isAmountEligible = (currentTotal >= minAmount);
        boolean isEligible = isAmountEligible && isTierMatch;

        if (!isEligible) {
            holder.itemView.setAlpha(0.6f);
            holder.rbSelected.setVisibility(View.GONE);
            holder.tvIneligibleHint.setVisibility(View.VISIBLE);
            
            if (!isTierMatch) {
                if ("VIP".equalsIgnoreCase(reqTier)) {
                    holder.tvIneligibleHint.setText("Mua thêm để lên hạng VIP và nhận ưu đãi này!");
                    holder.tvIneligibleHint.setTextColor(Color.parseColor("#D89216")); // Gold color
                } else {
                    holder.tvIneligibleHint.setText("Dành riêng cho hạng " + (reqTier != null ? reqTier : "cao hơn"));
                    holder.tvIneligibleHint.setTextColor(Color.parseColor("#FF5252"));
                }
            } else {
                double diff = minAmount - currentTotal;
                String hint = String.format(java.util.Locale.getDefault(), 
                    "Mua thêm %,.0fđ để sử dụng voucher này", diff).replace(",", ".");
                holder.tvIneligibleHint.setText(hint);
                holder.tvIneligibleHint.setTextColor(Color.parseColor("#FF5252"));
            }
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.rbSelected.setVisibility(View.VISIBLE);
            holder.rbSelected.setEnabled(true);
            holder.tvIneligibleHint.setVisibility(View.GONE);
        }

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
        
        // Hiển thị nhãn VIP nếu là voucher dành riêng cho VIP
        if ("VIP".equalsIgnoreCase(voucher.getRequiredTier())) {
            holder.tvTypeText.setText("VIP ONLY");
            holder.layoutIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF8E1")));
            holder.imgType.setImageTintList(ColorStateList.valueOf(Color.parseColor("#D89216")));
            holder.tvTypeText.setTextColor(Color.parseColor("#D89216"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (!isEligible) {
                String hint;
                if (!isTierMatch) {
                    hint = "Voucher này dành riêng cho hạng " + (reqTier != null ? reqTier : "VIP");
                } else {
                    hint = String.format(java.util.Locale.getDefault(), 
                        "Bạn cần mua thêm %,.0fđ để sử dụng mã này", minAmount - currentTotal).replace(",", ".");
                }
                Toast.makeText(context, hint, Toast.LENGTH_SHORT).show();
                return;
            }
            // Không tự tích chọn nữa, báo cho Fragment xử lý xác nhận
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
        TextView tvTypeText, tvTitle, tvDescription, tvExpiry, tvIneligibleHint;
        RadioButton rbSelected;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutIcon = itemView.findViewById(R.id.layoutIcon);
            imgType = itemView.findViewById(R.id.imgType);
            tvTypeText = itemView.findViewById(R.id.tvTypeText);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvExpiry = itemView.findViewById(R.id.tvExpiry);
            tvIneligibleHint = itemView.findViewById(R.id.tvIneligibleHint);
            rbSelected = itemView.findViewById(R.id.rbSelected);
        }
    }
}
