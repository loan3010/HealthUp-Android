package com.example.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.TranslationManager;
import com.bumptech.glide.Glide;
import com.example.healthup.R;
import com.example.models.CartItem;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CheckoutProductAdapter extends RecyclerView.Adapter<CheckoutProductAdapter.ViewHolder> {

    private final List<CartItem> items;
    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));

    public CheckoutProductAdapter(List<CartItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checkout_product, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.tvName.setText(item.getName());

        String currentLang = LocaleHelper.getLanguage(holder.itemView.getContext());
        if ("en".equals(currentLang)) {
            if (item.getName() != null) {
                TranslationManager.translate(item.getName(), "en", translated -> {
                    if (translated != null) holder.tvName.setText(translated);
                });
            }
        }

        // ✅ đổi từ getVariant() sang getVariantLabel()
        String variantLabel = item.getVariantLabel();
        if (variantLabel != null && !variantLabel.isEmpty()) {
            holder.tvVariant.setVisibility(View.VISIBLE);
            holder.tvVariant.setText(variantLabel);
            if ("en".equals(currentLang)) {
                TranslationManager.translate(variantLabel, "en", translated -> {
                    if (translated != null) holder.tvVariant.setText(translated);
                });
            }
        } else {
            holder.tvVariant.setVisibility(View.GONE);
        }

        holder.tvPrice.setText(currencyFormat.format(item.getPrice()) + "đ");
        holder.tvQuantity.setText("x" + item.getQuantity());

        if (item.getOriginalPrice() > item.getPrice()) {
            holder.tvOriginalPrice.setVisibility(View.VISIBLE);
            holder.tvOriginalPrice.setText(currencyFormat.format(item.getOriginalPrice()) + "đ");
            holder.tvOriginalPrice.setPaintFlags(
                    holder.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.tvOriginalPrice.setVisibility(View.GONE);
        }

        if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
            com.example.healthup.util.ImageLoadHelper.loadInto(holder.imgProduct, item.getImageUrl());
        } else {
            holder.imgProduct.setImageResource(R.color.neutral_light_grey);
        }

        // Link to Product Detail
        View.OnClickListener toProductDetail = v -> {
            if (item.getProductId() != null) {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.healthup.ProductDetailActivity.class);
                intent.putExtra("productId", item.getProductId());
                v.getContext().startActivity(intent);
            }
        };
        holder.imgProduct.setOnClickListener(toProductDetail);
        holder.tvName.setOnClickListener(toProductDetail);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvVariant, tvPrice, tvOriginalPrice, tvQuantity;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvName);
            tvVariant = itemView.findViewById(R.id.tvVariant);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
        }
    }
}
