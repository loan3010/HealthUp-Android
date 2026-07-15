package com.example.healthup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ItemProductRecommendBinding;
import com.example.models.Product;
import java.text.DecimalFormat;
import java.util.List;

public class RecommendProductAdapter extends RecyclerView.Adapter<RecommendProductAdapter.ProductViewHolder> {
    private Context context;
    private List<Product> products;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public RecommendProductAdapter(Context context, List<Product> products) {
        this.context = context;
        this.products = products;
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductRecommendBinding binding = ItemProductRecommendBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ProductViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {
        private ItemProductRecommendBinding binding;

        public ProductViewHolder(ItemProductRecommendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Product product) {
            binding.tvName.setText(product.getName());
            binding.tvPrice.setText(df.format(product.getDisplayPrice()));
            binding.tvRating.setText(String.valueOf(product.getRating()));
            
            binding.tvSold.setText("Đã bán " + product.getSoldCount());

            com.example.healthup.util.ImageLoadHelper.loadInto(binding.imgProduct, product.getImageUrl());

            boolean inStock = product.isInStock();
            if (binding.tvBadgeOutOfStock != null) {
                binding.tvBadgeOutOfStock.setVisibility(inStock ? android.view.View.GONE : android.view.View.VISIBLE);
            }
            binding.btnAdd.setEnabled(inStock);
            binding.btnAdd.setAlpha(inStock ? 1f : 0.45f);
            binding.btnAdd.setText(inStock ? "Thêm" : context.getString(R.string.out_of_stock));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(product);
                }
            });
        }
    }
}
