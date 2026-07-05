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
            binding.tvPrice.setText(df.format(product.getPrice()));
            binding.tvRating.setText(String.valueOf(product.getRating()));
            
            if (product.getSoldCount() >= 1000) {
                binding.tvSold.setText("Đã bán " + (product.getSoldCount() / 1000.0) + "k");
            } else {
                binding.tvSold.setText("Đã bán " + product.getSoldCount());
            }

            // Sử dụng logic load ảnh thông minh từ assets/web giống ProductAdapter
            String imagePath = product.getImageUrl();
            Object loadTarget = R.drawable.ic_launcher_background;

            if (imagePath != null && !imagePath.isEmpty()) {
                if (imagePath.startsWith("http") || imagePath.startsWith("file://") || imagePath.startsWith("content://")) {
                    loadTarget = imagePath;
                } else {
                    String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                    if (cleanPath.startsWith("images/products/")) {
                        loadTarget = "file:///android_asset/" + cleanPath;
                    } else if (cleanPath.startsWith("images/")) {
                        loadTarget = "file:///android_asset/" + cleanPath;
                    } else {
                        loadTarget = "file:///android_asset/images/products/" + cleanPath;
                    }
                }
            }

            Glide.with(context)
                    .load(loadTarget)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(binding.imgProduct);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductClick(product);
                }
            });
        }
    }
}
