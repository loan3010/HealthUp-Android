package com.example.healthup;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.healthup.R;
import com.example.models.Blog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.ViewHolder> {


    private List<Blog> blogList;
    private OnBlogClickListener listener;
    private final boolean isHorizontal;


    public interface OnBlogClickListener {
        void onBlogClick(Blog blog);
    }


    public BlogAdapter(List<Blog> blogList, OnBlogClickListener listener) {
        this(blogList, listener, false);
    }


    // FIX (bug "card bài viết ở trang chủ bị dính nhau"): item_blog.xml dùng CHUNG cho 2 nơi:
    // danh sách dọc đầy đủ ở trang "Cẩm nang sức khỏe" (cần width="match_parent") VÀ carousel
    // ngang ở trang chủ (cần chiều rộng cố định + margin giữa các card). Trước đây item_blog.xml
    // có width="match_parent" cứng trong XML -> áp dụng luôn cho carousel ngang ở trang chủ,
    // khiến mỗi card chiếm trọn chiều rộng RecyclerView, không có khoảng cách giữa các card khi
    // vuốt ngang.
    // Sửa: thêm tham số isHorizontal (theo đúng pattern đã dùng ở ProductAdapter) để override
    // LayoutParams của card TẠI RUNTIME chỉ khi hiển thị dạng carousel ngang, không đụng gì đến
    // XML dùng chung với danh sách dọc.
    public BlogAdapter(List<Blog> blogList, OnBlogClickListener listener, boolean isHorizontal) {
        this.blogList = blogList;
        this.listener = listener;
        this.isHorizontal = isHorizontal;
    }


    public void updateList(List<Blog> newList) {
        this.blogList = newList;
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blog, parent, false);
        if (isHorizontal) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            lp.width = (int) (parent.getContext().getResources().getDisplayMetrics().widthPixels * 0.78);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, (int) (12 * density), 0);
            }
            view.setLayoutParams(lp);
        }
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Blog blog = blogList.get(position);
        holder.tvTitle.setText(blog.getTitle());
        holder.tvCategory.setText(blog.getCategory());

        // Extract excerpt from content
        String content = blog.getContent();
        if (content != null && content.length() > 100) {
            holder.tvExcerpt.setText(content.substring(0, 100) + "...");
        } else {
            holder.tvExcerpt.setText(content);
        }


        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'tháng' MM, yyyy", new Locale("vi", "VN"));
        holder.tvDate.setText(sdf.format(new Date(blog.getTimestamp())));


        // Xử lý hiển thị ảnh Blog từ assets hoặc web
        String imagePath = blog.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(holder.ivImage);
            } else {
                String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                if (!cleanPath.startsWith("images/")) {
                    cleanPath = "images/blogs/" + cleanPath;
                }
                Glide.with(holder.itemView.getContext())
                        .load("file:///android_asset/" + cleanPath)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(holder.ivImage);
            }
        }


        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBlogClick(blog);
        });
    }


    @Override
    public int getItemCount() {
        return blogList != null ? blogList.size() : 0;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvCategory, tvTitle, tvExcerpt, tvDate;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_blog_image);
            tvCategory = itemView.findViewById(R.id.tv_blog_category);
            tvTitle = itemView.findViewById(R.id.tv_blog_title);
            tvExcerpt = itemView.findViewById(R.id.tv_blog_excerpt);
            tvDate = itemView.findViewById(R.id.tv_blog_date);
        }
    }
}