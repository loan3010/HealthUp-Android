package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
    private List<CartItem> cartItems;
    private OnCartItemChangeListener listener;

    public interface OnCartItemChangeListener {
        void onQuantityChange(CartItem item, int newQuantity);
        void onDeleteItem(CartItem item);
        void onItemClick(CartItem item);
    }

    public CartAdapter(List<CartItem> cartItems, OnCartItemChangeListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    public void updateData(List<CartItem> newList) {
        this.cartItems = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = cartItems.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvVariant, tvPrice, tvQuantity;
        ImageButton btnMinus, btnPlus, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.iv_cart_item_image);
            tvName = itemView.findViewById(R.id.tv_cart_item_name);
            tvVariant = itemView.findViewById(R.id.tv_cart_item_variant);
            tvPrice = itemView.findViewById(R.id.tv_cart_item_price);
            tvQuantity = itemView.findViewById(R.id.tv_cart_item_quantity);
            btnMinus = itemView.findViewById(R.id.btn_minus);
            btnPlus = itemView.findViewById(R.id.btn_plus);
            btnDelete = itemView.findViewById(R.id.btn_delete_cart_item);
        }

        public void bind(CartItem item, OnCartItemChangeListener listener) {
            if (item.getProduct() != null) {
                tvName.setText(item.getProduct().getName());
                
                if (item.getVariantName() != null && !item.getVariantName().isEmpty()) {
                    tvVariant.setText(item.getVariantName());
                    tvVariant.setVisibility(View.VISIBLE);
                } else {
                    tvVariant.setVisibility(View.GONE);
                }

                NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
                double displayPrice = item.getPrice() > 0 ? item.getPrice() : item.getProduct().getPrice();
                tvPrice.setText(formatter.format(displayPrice) + "đ");
                
                Glide.with(itemView.getContext())
                        .load(item.getProduct().getImageUrl())
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(imgProduct);
            }

            tvQuantity.setText(String.valueOf(item.getQuantity()));

            btnMinus.setOnClickListener(v -> {
                if (item.getQuantity() > 1) {
                    listener.onQuantityChange(item, item.getQuantity() - 1);
                }
            });

            btnPlus.setOnClickListener(v -> {
                listener.onQuantityChange(item, item.getQuantity() + 1);
            });

            btnDelete.setOnClickListener(v -> listener.onDeleteItem(item));
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
