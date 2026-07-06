package com.example.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.PolicyItem;

import java.util.List;

public class PolicyAdapter extends RecyclerView.Adapter<PolicyAdapter.ViewHolder> {

    public interface OnPolicyClickListener {
        void onPolicyClick(PolicyItem item);
    }

    private final List<PolicyItem> items;
    private final OnPolicyClickListener listener;

    public PolicyAdapter(List<PolicyItem> items, OnPolicyClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        // ===== Container cha (row + divider) =====
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));

        // ===== Row chính (icon - title - chevron) — đây mới là view nhận click =====
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding16 = dp(context, 16);
        row.setPadding(padding16, padding16, padding16, padding16);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 52)));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        row.setClickable(true);
        row.setFocusable(true);

        // Icon trái
        ImageView imgIcon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20));
        imgIcon.setLayoutParams(iconParams);
        imgIcon.setColorFilter(ContextCompat.getColor(context, R.color.green_button));
        row.addView(imgIcon);

        // Title
        TextView tvTitle = new TextView(context);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(context, 16));
        tvTitle.setLayoutParams(titleParams);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_dark));
        row.addView(tvTitle);

        // Chevron phải
        ImageView imgChevron = new ImageView(context);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(context, 7), dp(context, 11));
        imgChevron.setLayoutParams(chevronParams);
        imgChevron.setImageResource(R.drawable.ic_chevron_right);
        imgChevron.setColorFilter(ContextCompat.getColor(context, R.color.text_gray_muted));
        row.addView(imgChevron);

        wrapper.addView(row);

        // ===== Divider =====
        View divider = new View(context);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1));
        dividerParams.setMarginStart(dp(context, 16));
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.divider));
        wrapper.addView(divider);

        return new ViewHolder(wrapper, imgIcon, tvTitle, divider, row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PolicyItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.imgIcon.setImageResource(getIconForPolicy(item));
        holder.divider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);

        // ⚠️ QUAN TRỌNG: gắn listener vào "row", KHÔNG gắn vào holder.itemView
        holder.row.setOnClickListener(v -> listener.onPolicyClick(item));
    }

    private int getIconForPolicy(PolicyItem item) {
        String id = item.getId();
        String title = item.getTitle() != null ? item.getTitle().toLowerCase() : "";

        // Ưu tiên khớp theo ID (nếu được đặt ID document cụ thể)
        if (id != null) {
            switch (id) {
                case "terms_of_use": return R.drawable.ic_policy_check;
                case "privacy_policy": return R.drawable.ic_policy_shield;
                case "data_protection": return R.drawable.ic_policy_lock;
                case "payment_policy": return R.drawable.ic_policy_card;
                case "shipping_policy": return R.drawable.ic_policy_truck;
                case "return_refund": return R.drawable.ic_policy_return;
            }
        }

        // Nếu ID không khớp, thử khớp theo Tiêu đề (dành cho ID auto-generated)
        if (title.contains("bảo mật")) return R.drawable.ic_policy_shield;
        if (title.contains("thanh toán")) return R.drawable.ic_policy_card;
        if (title.contains("đổi trả")) return R.drawable.ic_policy_return;
        if (title.contains("vận chuyển")) return R.drawable.ic_policy_truck;
        if (title.contains("sử dụng")) return R.drawable.ic_policy_check;
        if (title.contains("dữ liệu")) return R.drawable.ic_policy_lock;

        return R.drawable.ic_policy_default;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvTitle;
        View divider;
        View row;

        ViewHolder(@NonNull View itemView, ImageView imgIcon, TextView tvTitle, View divider, View row) {
            super(itemView);
            this.imgIcon = imgIcon;
            this.tvTitle = tvTitle;
            this.divider = divider;
            this.row = row;
        }
    }
}
