package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.healthup.R;
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

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.isSelected());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            listener.onSelectChanged(item, isChecked);
        });

        holder.tvName.setText(item.getName());
        holder.tvVariant.setText(item.getVariantLabel());
        holder.tvPrice.setText("đ " + currencyFormat.format(item.getPrice()));
        holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

        if (item.getOriginalPrice() > item.getPrice()) {
            holder.tvOriginalPrice.setVisibility(View.VISIBLE);
            holder.tvOriginalPrice.setText("đ " + currencyFormat.format(item.getOriginalPrice()));
            holder.tvOriginalPrice.setPaintFlags(
                    holder.tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.tvOriginalPrice.setVisibility(View.GONE);
        }

        if (item.getStock() > 0 && item.getStock() <= 3) {
            holder.tvStockWarning.setVisibility(View.VISIBLE);
            holder.tvStockWarning.setText("Chỉ còn " + item.getStock() + " sản phẩm");
        } else {
            holder.tvStockWarning.setVisibility(View.GONE);
        }

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext()).load(item.getImageUrl()).into(holder.imgProduct);
        }

        holder.tvVariant.setOnClickListener(v -> listener.onEditVariant(item));
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(item));

        holder.btnIncrease.setOnClickListener(v -> {
            int newQty = item.getQuantity() + 1;
            if (item.getStock() > 0 && newQty > item.getStock()) return;
            item.setQuantity(newQty);
            holder.tvQuantity.setText(String.valueOf(newQty));
            listener.onQuantityChanged(item, newQty);
        });

        holder.btnDecrease.setOnClickListener(v -> {
            int newQty = item.getQuantity() - 1;
            if (newQty < 1) return;
            item.setQuantity(newQty);
            holder.tvQuantity.setText(String.valueOf(newQty));
            listener.onQuantityChanged(item, newQty);
        });
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
