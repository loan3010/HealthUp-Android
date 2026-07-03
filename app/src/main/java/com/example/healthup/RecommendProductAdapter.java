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

    public RecommendProductAdapter(Context context, List<Product> products) {
        this.context = context;
        this.products = products;
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

            Glide.with(context)
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.imgProduct);
        }
    }
}
