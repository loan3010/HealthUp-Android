package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.CartItem;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface Listener {
        void onSelectChanged(CartItem item, boolean selected);
        void onQuantityChanged(CartItem item, int newQuantity);
        void onRemove(CartItem item);
        void onEditVariant(CartItem item);
        void onItemClick(CartItem item);
    }

    private final List<CartItem> items;
    private final Listener listener;
    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    public CartAdapter(List<CartItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = items.get(position);

        if (holder.cbSelect != null) {
            holder.cbSelect.setOnCheckedChangeListener(null);
            holder.cbSelect.setChecked(item.isSelected());
            holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setSelected(isChecked);
                listener.onSelectChanged(item, isChecked);
            });
        }

        if (holder.tvName != null) {
            String name = item.getName();
            holder.tvName.setText(name != null ? name : "");
        }

        if (holder.tvVariant != null) {
            String variantLabel = item.getVariantLabel();
            holder.tvVariant.setText(variantLabel != null ? variantLabel : "");
        }

        if (holder.tvPrice != null) {
            double price = item.getPrice();
            holder.tvPrice.setText("đ " + currencyFormat.format(price));
        }

        if (holder.tvQuantity != null) {
            int quantity = Math.max(item.getQuantity(), 1);
            holder.tvQuantity.setText(String.valueOf(quantity));
        }

        if (holder.tvOriginalPrice != null) {
            if (item.getOriginalPrice() > item.getPrice()) {
                holder.tvOriginalPrice.setVisibility(View.VISIBLE);
                holder.tvOriginalPrice.setText("đ " + currencyFormat.format(item.getOriginalPrice()));
                holder.tvOriginalPrice.setPaintFlags(
                        holder.tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                holder.tvOriginalPrice.setVisibility(View.GONE);
            }
        }

        if (holder.tvStockWarning != null) {
            if (item.getStock() > 0 && item.getStock() <= 3) {
                holder.tvStockWarning.setVisibility(View.VISIBLE);
                holder.tvStockWarning.setText("Chỉ còn " + item.getStock() + " sản phẩm");
            } else {
                holder.tvStockWarning.setVisibility(View.GONE);
            }
        }

        if (holder.imgProduct != null) {
            String imagePath = item.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                ImageLoadHelper.loadInto(holder.imgProduct, imagePath);
            } else {
                holder.imgProduct.setImageResource(R.color.neutral_light_grey);
            }
            holder.imgProduct.setOnClickListener(v -> listener.onItemClick(item));
        }

        if (holder.tvName != null) {
            holder.tvName.setOnClickListener(v -> listener.onItemClick(item));
        }

        if (holder.tvVariant != null) {
            holder.tvVariant.setOnClickListener(v -> listener.onEditVariant(item));
        }
        if (holder.btnRemove != null) {
            holder.btnRemove.setOnClickListener(v -> listener.onRemove(item));
        }

        if (holder.btnIncrease != null) {
            holder.btnIncrease.setOnClickListener(v -> {
                int newQty = item.getQuantity() + 1;
                if (item.getStock() > 0 && newQty > item.getStock()) return;
                item.setQuantity(newQty);
                if (holder.tvQuantity != null) {
                    holder.tvQuantity.setText(String.valueOf(newQty));
                }
                listener.onQuantityChanged(item, newQty);
            });
        }

        if (holder.btnDecrease != null) {
            holder.btnDecrease.setOnClickListener(v -> {
                int newQty = item.getQuantity() - 1;
                if (newQty < 1) return;
                item.setQuantity(newQty);
                if (holder.tvQuantity != null) {
                    holder.tvQuantity.setText(String.valueOf(newQty));
                }
                listener.onQuantityChanged(item, newQty);
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        ImageView imgProduct, btnRemove;
        TextView tvName, tvVariant, tvStockWarning, tvPrice, tvOriginalPrice, tvQuantity, btnDecrease, btnIncrease;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            tvName = itemView.findViewById(R.id.tvName);
            tvVariant = itemView.findViewById(R.id.tvVariant);
            tvStockWarning = itemView.findViewById(R.id.tvStockWarning);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnDecrease = itemView.findViewById(R.id.btnDecrease);
            btnIncrease = itemView.findViewById(R.id.btnIncrease);
        }
    }
}
