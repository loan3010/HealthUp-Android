package com.group.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ProductDiffCallback(this.productList, newList));
        this.productList = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class ProductDiffCallback extends DiffUtil.Callback {
        private final List<Product> oldList;
        private final List<Product> newList;

        public ProductDiffCallback(List<Product> oldList, List<Product> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList != null ? oldList.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return newList != null ? newList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Product oldProduct = oldList.get(oldItemPosition);
            Product newProduct = newList.get(newItemPosition);
            return oldProduct.equals(newProduct);
        }
    }

    public void setSelectionMode(boolean mode) {
        this.selectionMode = mode;
        if (!mode) {
            for (Product p : productList) p.setSelected(false);
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
        holder.bind(product, listener, selectionMode);
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct, btnFavorite, btnAdd;
        TextView tvName, tvPrice, tvRating, tvSoldCount;
        android.widget.CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.ivProduct);
            btnFavorite = itemView.findViewById(R.id.btnWishlist);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvSoldCount = itemView.findViewById(R.id.tvSoldCount);
            btnAdd = itemView.findViewById(R.id.btnAddToCart);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }

        public void bind(Product product, OnProductClickListener listener, boolean selectionMode) {
            tvName.setText(product.getName());
            
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
            tvPrice.setText(formatter.format(product.getPrice()) + "đ");
            
            tvRating.setText(String.valueOf(product.getRating()));
            tvSoldCount.setText("đã bán " + formatSoldCount(product.getSoldCount()));

            Glide.with(itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(imgProduct);

            if (selectionMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(product.isSelected());
                cbSelect.setClickable(false); // Để itemView nhận sự kiện click
                
                // Cập nhật background nếu được chọn
                if (product.isSelected()) {
                    itemView.setBackgroundResource(R.drawable.bg_product_selected);
                } else {
                    itemView.setBackgroundResource(R.drawable.bg_product_unselected);
                }
                
                btnFavorite.setVisibility(View.GONE);
                btnAdd.setVisibility(View.GONE);
            } else {
                cbSelect.setVisibility(View.GONE);
                itemView.setBackgroundResource(R.drawable.bg_product_unselected);
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
