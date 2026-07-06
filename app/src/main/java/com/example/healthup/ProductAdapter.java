package com.example.healthup;


import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
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
        // FIX: luôn tạo bản sao riêng, KHÔNG dùng chung reference với list bên ngoài (Fragment/Activity).
        // Nguyên nhân gốc của bug "danh sách trống lần đầu, đổi tab mới hiện":
        // nếu adapter dùng chung reference, khi bên ngoài mutate (clear + addAll) list đó TRƯỚC khi
        // gọi updateData(), DiffUtil sẽ so sánh 1 list với chính nó (đã bị đổi) => tưởng không có gì
        // thay đổi => không gọi notify* => RecyclerView không vẽ item dù dữ liệu đã có.
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
            // Làm nhỏ sản phẩm Flash Sale lại (tầm 35% màn hình)
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

            DecimalFormat df = new DecimalFormat("#,###đ");
            String formattedPrice = df.format(product.getPrice());
            tvPrice.setText(formattedPrice);


            // Nếu là hàng Flash Sale (ngang), làm nhỏ chữ giá và ép 1 dòng để không bị xuống dòng
            if (isHorizontal) {
                // Ép cứng cỡ chữ cực nhỏ cho Flash Sale để không bị nhảy dòng
                tvPrice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
                tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);

                // Thu nhỏ luôn 2 nút bấm để nhường chỗ cho giá tiền
                btnAdd.getLayoutParams().width = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnAdd.getLayoutParams().height = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().width = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
                btnWishlist.getLayoutParams().height = (int) (24 * itemView.getContext().getResources().getDisplayMetrics().density);
            } else {
                // Cỡ chữ bình thường cho danh sách dọc
                tvPrice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
                tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);

                // Kích thước nút bấm bình thường
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


            // Badges
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


            // Xử lý hiển thị ảnh
            String imagePath = product.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Object loadTarget;

                if (cleanPath.startsWith("images/")) {
                    loadTarget = "file:///android_asset/" + cleanPath;
                } else if (imagePath.startsWith("http")) {
                    loadTarget = imagePath;
                } else {
                    loadTarget = "file:///android_asset/images/products/" + cleanPath;
                }


                Glide.with(itemView.getContext())
                        .load(loadTarget)
                        .placeholder(R.color.neutral_light_grey)
                        .into(ivProduct);
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
                btnWishlist.setImageResource(product.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            }


            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onProductClick(product));
                btnAdd.setOnClickListener(v -> listener.onAddToCart(product));
                btnWishlist.setOnClickListener(v -> listener.onFavoriteClick(product));
            } else {
                btnWishlist.setOnClickListener(v -> {
                    product.setFavorite(!product.isFavorite());
                    btnWishlist.setImageResource(product.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                    Toast.makeText(itemView.getContext(), "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
}