package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Address;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {

    public interface Listener {
        void onSelect(Address address);
        void onEdit(Address address);
    }

    private final List<Address> items;
    private final Listener listener;
    private final boolean isSelectionMode;
    private int selectedPosition = -1;

    public AddressAdapter(List<Address> items, boolean isSelectionMode, Listener listener) {
        this.items = items;
        this.isSelectionMode = isSelectionMode;
        this.listener = listener;
        if (isSelectionMode) {
            // Khởi tạo vị trí được chọn dựa trên địa chỉ mặc định
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).isDefault()) {
                    selectedPosition = i;
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Address address = items.get(position);

        holder.tvName.setText(address.getRecipientName());
        holder.tvPhone.setText("(+84) " + address.getPhone());
        holder.tvDetail.setText(address.getFullAddress());
        
        holder.tvDefaultBadge.setVisibility(address.isDefault() ? View.VISIBLE : View.GONE);
        
        if (isSelectionMode) {
            holder.rbSelect.setVisibility(View.VISIBLE);
            boolean isSelected = position == selectedPosition;
            holder.rbSelect.setChecked(isSelected);
            
            // Cập nhật viền cho CardView nếu được chọn
            if (isSelected) {
                holder.cardContainer.setBackgroundResource(R.drawable.bg_address_card_selected);
            } else {
                holder.cardContainer.setBackgroundResource(android.R.color.white);
            }

            View.OnClickListener clickSelect = v -> {
                int oldPos = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPosition);
                listener.onSelect(address);
            };

            holder.itemView.setOnClickListener(clickSelect);
        } else {
            holder.rbSelect.setVisibility(View.GONE);
            holder.cardContainer.setBackgroundResource(android.R.color.white);
            holder.itemView.setOnClickListener(null);
        }
        
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(address));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        RadioButton rbSelect;
        TextView tvName, tvPhone, tvDetail, tvDefaultBadge, btnEdit;
        CardView cardContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rbSelect = itemView.findViewById(R.id.rbSelect);
            tvName = itemView.findViewById(R.id.tvName);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvDetail = itemView.findViewById(R.id.tvDetail);
            tvDefaultBadge = itemView.findViewById(R.id.tvDefaultBadge);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            cardContainer = itemView.findViewById(R.id.cardContainer);
        }
    }
}
