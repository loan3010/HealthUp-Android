package com.example.healthup;




import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.example.healthup.util.LocaleHelper;
import com.example.healthup.util.TranslationManager;
import com.bumptech.glide.Glide;
import com.example.models.Product;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;




public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
    private List<Product> products;
    private boolean isHorizontal;
    private OnProductClickListener listener;
    private boolean selectionMode = false;




    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onAddToCart(Product product);
        void onFavoriteClick(Product product);
    }




    public ProductAdapter(List<Product> products, OnProductClickListener listener) {
        this(products, listener, false);
    }




    public ProductAdapter(List<Product> products, OnProductClickListener listener, boolean isHorizontal) {
        this.products = (products != null) ? new ArrayList<>(products) : new ArrayList<>();
        this.listener = listener;
        this.isHorizontal = isHorizontal;
    }




    public void updateData(List<Product> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ProductDiffCallback(this.products, newList));
        this.products = newList;
        diffResult.dispatchUpdatesTo(this);
    }




    public void setSelectionMode(boolean mode) {
        this.selectionMode = mode;
        if (!mode) {
            for (Product p : products) p.setSelected(false);
        }
        notifyDataSetChanged();
    }




    private static class ProductDiffCallback extends DiffUtil.Callback {
        private final List<Product> oldList;
        private final List<Product> newList;




        public ProductDiffCallback(List<Product> oldList, List<Product> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }




        @Override
        public int getOldListSize() { return oldList != null ? oldList.size() : 0; }
        @Override
        public int getNewListSize() { return newList != null ? newList.size() : 0; }
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
    }




    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        if (isHorizontal) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = (int) (parent.getContext().getResources().getDisplayMetrics().widthPixels * 0.35);
            view.setLayoutParams(lp);
        }
        return new ProductViewHolder(view);
    }




    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.bind(product, listener, selectionMode, isHorizontal);
    }




    @Override
    public int getItemCount() {
        return products != null ? products.size() : 0;
    }




    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProduct, btnAdd, btnWishlist;
        TextView tvName, tvPrice, tvOriginalPrice, tvRating, tvSoldCount;
        TextView tvBadgeNew, tvBadgeHot;
        android.widget.CheckBox cbSelect;




        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.ivProduct);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvSoldCount = itemView.findViewById(R.id.tvSoldCount);
            tvBadgeNew = itemView.findViewById(R.id.tvBadgeNew);
            tvBadgeHot = itemView.findViewById(R.id.tvBadgeHot);
            btnAdd = itemView.findViewById(R.id.btnAddToCart);
            btnWishlist = itemView.findViewById(R.id.btnWishlist);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }




        public void bind(Product product, OnProductClickListener listener, boolean selectionMode, boolean isHorizontal) {
            tvName.setText(product.getName());
            
            // Tự động dịch tên sản phẩm nếu đang ở chế độ Tiếng Anh
            String currentLang = LocaleHelper.getLanguage(itemView.getContext());
            if ("en".equals(currentLang)) {
                TranslationManager.translate(product.getName(), "en", translatedText -> {
                    if (translatedText != null && !translatedText.isEmpty()) {
                        tvName.setText(translatedText);
                    }
                });
            }


            DecimalFormat df = new DecimalFormat("#,###đ");
            String formattedPrice = df.format(product.getPrice());
            tvPrice.setText(formattedPrice);




            if (isHorizontal) {
                tvPrice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
                tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);


                btnAdd.getLayoutParams().width = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnAdd.getLayoutParams().height = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().width = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().height = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
            } else {
                tvPrice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
                tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);


                btnAdd.getLayoutParams().width = (int) (32 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnAdd.getLayoutParams().height = (int) (32 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().width = (int) (32 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().height = (int) (32 * itemView.getContext().getResources().getDisplayMetrics().density);
            }




            if (product.getOriginalPrice() > 0 && product.getOriginalPrice() > product.getPrice()) {
                tvOriginalPrice.setVisibility(View.VISIBLE);
                tvOriginalPrice.setText(df.format(product.getOriginalPrice()));
                tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvOriginalPrice.setVisibility(View.GONE);
            }




            if (tvRating != null) tvRating.setText(String.valueOf(product.getRating()));
            if (tvSoldCount != null) tvSoldCount.setText("đã bán " + product.getSoldCount());




            if (product.isNew()) {
                tvBadgeNew.setVisibility(View.VISIBLE);
                tvBadgeHot.setVisibility(View.GONE);
            } else if (product.isHot()) {
                tvBadgeHot.setVisibility(View.VISIBLE);
                tvBadgeNew.setVisibility(View.GONE);
            } else {
                tvBadgeNew.setVisibility(View.GONE);
                tvBadgeHot.setVisibility(View.GONE);
            }




            String imagePath = product.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                com.example.healthup.util.ImageLoadHelper.loadInto(ivProduct, imagePath);
            } else {
                ivProduct.setImageResource(R.color.neutral_light_grey);
            }




            if (selectionMode && cbSelect != null) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(product.isSelected());
                btnWishlist.setVisibility(View.GONE);
                btnAdd.setVisibility(View.GONE);
            } else if (cbSelect != null) {
                cbSelect.setVisibility(View.GONE);
                btnWishlist.setVisibility(View.VISIBLE);
                btnAdd.setVisibility(View.VISIBLE);
                updateWishlistIcon(btnWishlist, product);
            }




            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onProductClick(product));
                btnAdd.setOnClickListener(v -> listener.onAddToCart(product));
                // FIX: WishlistManager.toggle() đổi product.isFavorite() NGAY LẬP TỨC (đồng bộ)
                // trước khi gửi request Firestore (bất đồng bộ). Gọi listener.onFavoriteClick()
                // trước (nó gọi toggle() bên trong), rồi cập nhật icon NGAY TẠI ĐÂY — không phụ
                // thuộc vào network hay callback bất đồng bộ.
                btnWishlist.setOnClickListener(v -> {
                    listener.onFavoriteClick(product);
                    updateWishlistIcon(btnWishlist, product);
                });
            } else {
                btnWishlist.setOnClickListener(v -> {
                    product.setFavorite(!product.isFavorite());
                    updateWishlistIcon(btnWishlist, product);
                    Toast.makeText(itemView.getContext(),
                            product.isFavorite() ? "Đã thêm vào yêu thích" : "Đã xóa khỏi yêu thích",
                            Toast.LENGTH_SHORT).show();
                });
            }
        }


        private void updateWishlistIcon(ImageView btnWishlist, Product product) {
            boolean isFavorite = product.isFavorite();
            btnWishlist.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            int tintColor = ContextCompat.getColor(itemView.getContext(),
                    isFavorite ? R.color.error : R.color.primary_default);
            btnWishlist.setImageTintList(ColorStateList.valueOf(tintColor));
        }
    }
}