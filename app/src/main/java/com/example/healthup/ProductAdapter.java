package com.example.healthup;

import android.content.Intent;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.models.Product;
import java.text.DecimalFormat;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
    private List<Product> products;
    private boolean isHorizontal;

    public ProductAdapter(List<Product> products, boolean isHorizontal) {
        this.products = products;
        this.isHorizontal = isHorizontal;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        if (isHorizontal) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = (int) (parent.getContext().getResources().getDisplayMetrics().widthPixels * 0.42);
            view.setLayoutParams(lp);
        }
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.tvName.setText(product.getName());
        
        // Rating and Sold Count
        holder.tvRating.setText(product.getStars() != null ? product.getStars() : "0.0");
        holder.tvSoldCount.setText("Đã bán " + (product.getSoldCount() > 0 ? product.getSoldCount() : product.getSold()));

        DecimalFormat df = new DecimalFormat("#,###đ");
        holder.tvPrice.setText(df.format(product.getPrice()));

        if (product.getOriginalPrice() > 0 && product.getOriginalPrice() > product.getPrice()) {
            holder.tvOriginalPrice.setVisibility(View.VISIBLE);
            holder.tvOriginalPrice.setText(df.format(product.getOriginalPrice()));
            holder.tvOriginalPrice.setPaintFlags(holder.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.tvOriginalPrice.setVisibility(View.GONE);
        }

        // Badges
        if (product.isNew()) {
            holder.tvBadgeNew.setVisibility(View.VISIBLE);
            holder.tvBadgeHot.setVisibility(View.GONE);
        } else if (product.isHot()) {
            holder.tvBadgeHot.setVisibility(View.VISIBLE);
            holder.tvBadgeNew.setVisibility(View.GONE);
        } else {
            holder.tvBadgeNew.setVisibility(View.GONE);
            holder.tvBadgeHot.setVisibility(View.GONE);
        }

        // Image loading
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            String imagePath = product.getImages().get(0);
            String fullPath;
            if (imagePath.startsWith("http")) {
                fullPath = imagePath;
            } else {
                String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                fullPath = "file:///android_asset/" + cleanPath;
            }
            
            Glide.with(holder.itemView.getContext())
                    .load(fullPath)
                    .placeholder(R.color.neutral_light_grey)
                    .into(holder.ivProduct);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ProductDetailActivity.class);
            intent.putExtra("product_id", product.getId());
            v.getContext().startActivity(intent);
        });

        holder.btnWishlist.setOnClickListener(v -> {
            // Wishlist logic will be handled here or via callback
            Toast.makeText(holder.itemView.getContext(), "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
        });

        holder.btnAdd.setOnClickListener(v -> {
            Toast.makeText(holder.itemView.getContext(), "Đã thêm vào giỏ hàng", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    public void updateList(List<Product> newList) {
        this.products = newList;
        notifyDataSetChanged();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProduct;
        TextView tvName, tvPrice, tvOriginalPrice;
        TextView tvBadgeNew, tvBadgeHot;
        TextView tvRating, tvSoldCount;
        ImageView btnAdd, btnWishlist;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.ivProduct);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvBadgeNew = itemView.findViewById(R.id.tvBadgeNew);
            tvBadgeHot = itemView.findViewById(R.id.tvBadgeHot);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvSoldCount = itemView.findViewById(R.id.tvSoldCount);
            btnAdd = itemView.findViewById(R.id.btnAddToCart);
            btnWishlist = itemView.findViewById(R.id.btnWishlist);
        }
    }
}
