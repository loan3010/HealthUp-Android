package com.group.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.group.healthup.R;
import com.group.models.Product;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
    private List<Product> productList;
    private OnProductClickListener listener;
    private boolean selectionMode = false;
    private List<String> selectedIds = new java.util.ArrayList<>();

    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onAddToCart(Product product);
        void onFavoriteClick(Product product);
    }

    public ProductAdapter(List<Product> productList, OnProductClickListener listener) {
        this.productList = productList;
        this.listener = listener;
    }

    public void updateData(List<Product> newList) {
        this.productList = newList;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean mode) {
        this.selectionMode = mode;
        if (!mode) selectedIds.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(String productId) {
        if (selectedIds.contains(productId)) {
            selectedIds.remove(productId);
        } else {
            selectedIds.add(productId);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.bind(product, listener, selectionMode, selectedIds.contains(product.getId()));
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct, btnFavorite;
        TextView tvName, tvPrice, tvRating, tvSoldCount;
        MaterialButton btnAdd;
        android.widget.CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.img_product);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            tvName = itemView.findViewById(R.id.tv_product_name);
            tvPrice = itemView.findViewById(R.id.tv_product_price);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvSoldCount = itemView.findViewById(R.id.tv_sold_count);
            btnAdd = itemView.findViewById(R.id.btn_add_to_cart);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }

        public void bind(Product product, OnProductClickListener listener, boolean selectionMode, boolean isSelected) {
            tvName.setText(product.getName());
            
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
            tvPrice.setText(formatter.format(product.getPrice()) + "đ");
            
            tvRating.setText(String.valueOf(product.getRating()));
            tvSoldCount.setText("đã bán " + formatSoldCount(product.getSoldCount()));

            Glide.with(itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(imgProduct);

            if (selectionMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(isSelected);
                btnFavorite.setVisibility(View.GONE);
                btnAdd.setVisibility(View.GONE);
            } else {
                cbSelect.setVisibility(View.GONE);
                btnFavorite.setVisibility(View.VISIBLE);
                btnAdd.setVisibility(View.VISIBLE);
                btnFavorite.setImageResource(product.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            }

            itemView.setOnClickListener(v -> listener.onProductClick(product));
            btnAdd.setOnClickListener(v -> listener.onAddToCart(product));
            btnFavorite.setOnClickListener(v -> listener.onFavoriteClick(product));
        }

        private String formatSoldCount(int count) {
            if (count >= 1000) {
                return String.format("%.1fk", count / 1000.0);
            }
            return String.valueOf(count);
        }
    }
}
