package com.group.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.group.healthup.databinding.ItemProductSelectBinding;
import com.group.models.OrderItem;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductSelectAdapter extends RecyclerView.Adapter<ProductSelectAdapter.ViewHolder> {
    private List<OrderItem> items;
    private Map<OrderItem, Integer> selectedItems;
    private DecimalFormat df = new DecimalFormat("#,###đ/sp");

    public ProductSelectAdapter(List<OrderItem> items, Map<OrderItem, Integer> initialSelected) {
        this.items = items;
        this.selectedItems = new HashMap<>(initialSelected);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductSelectBinding binding = ItemProductSelectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public Map<OrderItem, Integer> getSelectedItems() {
        return selectedItems;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ItemProductSelectBinding binding;

        public ViewHolder(ItemProductSelectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(OrderItem item) {
            binding.tvProductName.setText(item.getName());
            binding.tvOrderInfo.setText("Đã đặt: " + item.getQuantity() + " · " + df.format(item.getPrice()));
            
            Glide.with(itemView.getContext())
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.imgProduct);

            boolean isSelected = selectedItems.containsKey(item);
            binding.cbSelect.setChecked(isSelected);
            
            if (isSelected) {
                binding.cvProduct.setStrokeColor(0xFF36873A);
                binding.divider.setVisibility(View.VISIBLE);
                binding.rlQtySection.setVisibility(View.VISIBLE);
                int qty = selectedItems.get(item);
                binding.tvQty.setText(String.valueOf(qty));
            } else {
                binding.cvProduct.setStrokeColor(0xFFE0D9D5);
                binding.divider.setVisibility(View.GONE);
                binding.rlQtySection.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> toggleSelection(item));
            binding.cbSelect.setOnClickListener(v -> toggleSelection(item));

            binding.btnPlus.setOnClickListener(v -> {
                if (selectedItems.containsKey(item)) {
                    int currentQty = selectedItems.get(item);
                    if (currentQty < item.getQuantity()) {
                        selectedItems.put(item, currentQty + 1);
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            });

            binding.btnMinus.setOnClickListener(v -> {
                if (selectedItems.containsKey(item)) {
                    int currentQty = selectedItems.get(item);
                    if (currentQty > 1) {
                        selectedItems.put(item, currentQty - 1);
                        notifyItemChanged(getAdapterPosition());
                    } else {
                        selectedItems.remove(item);
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            });
        }

        private void toggleSelection(OrderItem item) {
            if (selectedItems.containsKey(item)) {
                selectedItems.remove(item);
            } else {
                selectedItems.put(item, 1);
            }
            notifyItemChanged(getAdapterPosition());
        }
    }
}
