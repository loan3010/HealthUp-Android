package com.example.adapters;




import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;




import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;




import com.example.healthup.R;
import com.example.healthup.util.ImageLoadHelper;
import com.example.models.CartItem;




import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.TranslationManager;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;




public class CartAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {




    public interface Listener {
        void onSelectChanged(CartItem item, boolean selected);
        void onQuantityChanged(CartItem item, int newQuantity);
        void onRemove(CartItem item);
        void onEditVariant(CartItem item);
        void onItemClick(CartItem item);
        // FIX (bug #2): cho phép bấm trực tiếp icon trái tim trên từng sản phẩm trong giỏ hàng
        // để thêm/xóa khỏi Đã thích, mà không xóa sản phẩm khỏi giỏ hàng.
        void onToggleFavorite(CartItem item);
        void onClearOutOfStock();
    }




    private static final int TYPE_ITEM = 1;
    private static final int TYPE_HEADER = 2;


    private final List<CartItem> items;
    private final Listener listener;
    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("vi", "VN"));




    public CartAdapter(List<CartItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }




    @Override
    public int getItemViewType(int position) {
        if (items.get(position).isHeader()) return TYPE_HEADER;
        return TYPE_ITEM;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cart_header, parent, false);
            return new HeaderViewHolder(v);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new ItemViewHolder(v);
    }




    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CartItem item = items.get(position);


        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.btnClear.setOnClickListener(v -> listener.onClearOutOfStock());
            return;
        }


        ItemViewHolder h = (ItemViewHolder) holder;


        boolean isOutOfStock = item.getStock() <= 0;
        h.rootLayout.setAlpha(isOutOfStock ? 0.6f : 1.0f);
        h.tvOutOfStockOverlay.setVisibility(isOutOfStock ? View.VISIBLE : View.GONE);


        h.cbSelect.setOnCheckedChangeListener(null);
        h.cbSelect.setEnabled(!isOutOfStock);
        if (isOutOfStock) {
            item.setSelected(false);
            h.cbSelect.setChecked(false);
        } else {
            h.cbSelect.setChecked(item.isSelected());
        }


        h.itemView.setOnClickListener(v -> listener.onItemClick(item));




        if (h.cbSelect != null) {
            h.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setSelected(isChecked);
                listener.onSelectChanged(item, isChecked);
            });
        }




        if (h.tvName != null) {
            String name = item.getName();
            h.tvName.setText(name != null ? name : "");


            String currentLang = LocaleHelper.getLanguage(h.itemView.getContext());
            if ("en".equals(currentLang) && name != null) {
                TranslationManager.translate(name, "en", translated -> {
                    if (translated != null) h.tvName.setText(translated);
                });
            }
        }




        if (h.tvVariant != null) {
            String variantLabel = item.getVariantLabel();
            h.tvVariant.setText(variantLabel != null ? variantLabel : "");


            String currentLang = LocaleHelper.getLanguage(h.itemView.getContext());
            if ("en".equals(currentLang) && variantLabel != null) {
                TranslationManager.translate(variantLabel, "en", translated -> {
                    if (translated != null) h.tvVariant.setText(translated);
                });
            }
        }




        if (h.tvPrice != null) {
            double price = item.getPrice();
            h.tvPrice.setText("đ " + currencyFormat.format(price));
        }




        if (h.tvQuantity != null) {
            int quantity = Math.max(item.getQuantity(), 1);
            h.tvQuantity.setText(String.valueOf(quantity));
        }




        if (h.tvOriginalPrice != null) {
            if (item.getOriginalPrice() > item.getPrice()) {
                h.tvOriginalPrice.setVisibility(View.VISIBLE);
                h.tvOriginalPrice.setText("đ " + currencyFormat.format(item.getOriginalPrice()));
                h.tvOriginalPrice.setPaintFlags(
                        h.tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.tvOriginalPrice.setVisibility(View.GONE);
            }
        }




        if (h.tvStockWarning != null) {
            if (!isOutOfStock && item.getStock() <= 3) {
                h.tvStockWarning.setVisibility(View.VISIBLE);
                h.tvStockWarning.setText("Chỉ còn " + item.getStock() + " sản phẩm");
            } else {
                h.tvStockWarning.setVisibility(View.GONE);
            }
        }




        if (h.imgProduct != null) {
            String imagePath = item.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                ImageLoadHelper.loadInto(h.imgProduct, imagePath);
            } else {
                h.imgProduct.setImageResource(R.color.neutral_light_grey);
            }
            h.imgProduct.setOnClickListener(v -> listener.onItemClick(item));
        }




        if (h.tvName != null) {
            h.tvName.setOnClickListener(v -> listener.onItemClick(item));
        }




        if (h.tvVariant != null) {
            h.tvVariant.setEnabled(!isOutOfStock);
            h.tvVariant.setOnClickListener(v -> {
                if (!isOutOfStock) listener.onEditVariant(item);
            });
        }
        if (h.btnRemove != null) {
            h.btnRemove.setOnClickListener(v -> listener.onRemove(item));
        }




        // FIX (bug #2): icon trái tim phản ánh đúng trạng thái yêu thích của sản phẩm; bấm vào
        // để thêm/xóa khỏi Đã thích trực tiếp ngay tại Giỏ hàng, không làm mất sản phẩm khỏi giỏ.
        if (h.ivFavorite != null) {
            boolean fav = item.isFavorite();
            h.ivFavorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            int tintColor = ContextCompat.getColor(h.itemView.getContext(),
                    fav ? R.color.error : R.color.text_gray_light);
            h.ivFavorite.setColorFilter(tintColor);
            h.ivFavorite.setOnClickListener(v -> listener.onToggleFavorite(item));
        }




        if (h.btnIncrease != null) {
            h.btnIncrease.setEnabled(!isOutOfStock);
            h.btnIncrease.setOnClickListener(v -> {
                if (isOutOfStock) return;
                int newQty = item.getQuantity() + 1;
                if (item.getStock() > 0 && newQty > item.getStock()) return;
                item.setQuantity(newQty);
                if (h.tvQuantity != null) {
                    h.tvQuantity.setText(String.valueOf(newQty));
                }
                listener.onQuantityChanged(item, newQty);
            });
        }




        if (h.btnDecrease != null) {
            h.btnDecrease.setEnabled(!isOutOfStock);
            h.btnDecrease.setOnClickListener(v -> {
                if (isOutOfStock) return;
                int newQty = item.getQuantity() - 1;
                if (newQty < 1) return;
                item.setQuantity(newQty);
                if (h.tvQuantity != null) {
                    h.tvQuantity.setText(String.valueOf(newQty));
                }
                listener.onQuantityChanged(item, newQty);
            });
        }
    }




    @Override
    public int getItemCount() { return items.size(); }




    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView btnClear;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            btnClear = itemView.findViewById(R.id.btnClearOutOfStock);
        }
    }


    static class ItemViewHolder extends RecyclerView.ViewHolder {
        View rootLayout, tvOutOfStockOverlay;
        CheckBox cbSelect;
        ImageView imgProduct, btnRemove, ivFavorite;
        TextView tvName, tvVariant, tvStockWarning, tvPrice, tvOriginalPrice, tvQuantity, btnDecrease, btnIncrease;




        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.rootLayout);
            tvOutOfStockOverlay = itemView.findViewById(R.id.tvOutOfStockOverlay);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
            tvName = itemView.findViewById(R.id.tvName);
            tvVariant = itemView.findViewById(R.id.tvVariant);
            tvStockWarning = itemView.findViewById(R.id.tvStockWarning);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnDecrease = itemView.findViewById(R.id.btnDecrease);
            btnIncrease = itemView.findViewById(R.id.btnIncrease);
        }
    }
}
