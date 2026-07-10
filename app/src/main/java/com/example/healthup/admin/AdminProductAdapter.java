package com.example.healthup.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.healthup.R;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class AdminProductAdapter extends RecyclerView.Adapter<AdminProductAdapter.ViewHolder> {

    private static final int LOW_STOCK_THRESHOLD = 10;

    public interface Listener {
        void onProductClick(Product product);
        void onDeleteClick(Product product);
    }

    private final List<Product> products;
    private final Listener listener;
    private final NumberFormat priceFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    public AdminProductAdapter(List<Product> products, Listener listener) {
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = products.get(position);
        holder.tvName.setText(product.getName());
        holder.tvCategory.setText(product.getCategory());
        holder.tvPrice.setText(priceFormat.format(product.getPrice()) + " đ");

        int stock = product.getStock();
        holder.tvStock.setText("Tồn kho: " + stock);
        if (stock <= 0) {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.error));
        } else if (stock < LOW_STOCK_THRESHOLD) {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary));
        } else {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        if (product.isDraft()) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(R.string.admin_filter_draft);
        } else if (product.isHidden()) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(R.string.admin_hidden_badge);
        } else if (stock <= 0) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(R.string.admin_out_of_stock_badge);
        } else if (stock < LOW_STOCK_THRESHOLD) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(R.string.admin_low_stock_badge);
        } else {
            holder.tvBadge.setVisibility(View.GONE);
        }

        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            ImageLoadHelper.loadInto(holder.img, imageUrl);
        } else {
            Glide.with(holder.img.getContext()).clear(holder.img);
            holder.img.setImageResource(R.color.neutral_light_grey);
        }

        holder.itemView.setOnClickListener(v -> listener.onProductClick(product));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(product));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvName, tvCategory, tvPrice, tvStock, tvBadge;
        ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgAdminProduct);
            tvName = itemView.findViewById(R.id.tvAdminProductName);
            tvCategory = itemView.findViewById(R.id.tvAdminProductCategory);
            tvPrice = itemView.findViewById(R.id.tvAdminProductPrice);
            tvStock = itemView.findViewById(R.id.tvAdminProductStock);
            tvBadge = itemView.findViewById(R.id.tvAdminProductBadge);
            btnDelete = itemView.findViewById(R.id.btnDeleteProduct);
        }
    }
}
