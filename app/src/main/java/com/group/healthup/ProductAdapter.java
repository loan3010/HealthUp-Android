package com.group.healthup;

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
import com.group.models.Product;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
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

        // Xử lý hiển thị ảnh
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            String imagePath = product.getImages().get(0);
            
            // Xử lý đường dẫn
            String cleanPath = imagePath;
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            
            // Nếu đường dẫn bắt đầu bằng images/ (đúng với cấu trúc trong assets)
            if (cleanPath.startsWith("images/")) {
                Glide.with(holder.itemView.getContext())
                        .load("file:///android_asset/" + cleanPath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivProduct);
            } else if (imagePath.startsWith("http")) {
                // Nếu là URL web
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivProduct);
            } else {
                // Thử tìm trực tiếp trong assets/images/products nếu chỉ có tên file
                Glide.with(holder.itemView.getContext())
                        .load("file:///android_asset/images/products/" + cleanPath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivProduct);
            }
        } else {
            holder.ivProduct.setImageResource(R.color.neutral_light_grey);
        }

        // Xử lý thêm vào wishlist
        holder.btnWishlist.setOnClickListener(v -> {
            // Sau này bạn có thể thêm logic lưu vào Firebase wishlist ở đây
            Toast.makeText(holder.itemView.getContext(), 
                "Đã thêm " + product.getName() + " vào yêu thích", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProduct;
        TextView tvName, tvPrice, tvOriginalPrice;
        TextView tvBadgeNew, tvBadgeHot;
        ImageView btnAdd, btnWishlist;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.ivProduct);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvBadgeNew = itemView.findViewById(R.id.tvBadgeNew);
            tvBadgeHot = itemView.findViewById(R.id.tvBadgeHot);
            btnAdd = itemView.findViewById(R.id.btnAddToCart);
            btnWishlist = itemView.findViewById(R.id.btnWishlist);
        }
    }
}
