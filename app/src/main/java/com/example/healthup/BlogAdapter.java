package com.example.healthup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.models.Blog;
import java.util.List;

public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.BlogViewHolder> {
    private List<Blog> blogs;

    public BlogAdapter(List<Blog> blogs) {
        this.blogs = blogs;
    }

    @NonNull
    @Override
    public BlogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blog, parent, false);
        return new BlogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BlogViewHolder holder, int position) {
        Blog blog = blogs.get(position);
        holder.tvTitle.setText(blog.getTitle());
        holder.tvSummary.setText(blog.getContent());

        // Xử lý hiển thị ảnh Blog từ assets hoặc web
        String imagePath = blog.getImageUrl();
        
        if (imagePath != null && !imagePath.isEmpty()) {
            String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
            
            if (cleanPath.startsWith("images/")) {
                Glide.with(holder.itemView.getContext())
                        .load("file:///android_asset/" + cleanPath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivBlog);
            } else if (imagePath.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivBlog);
            } else {
                // Thử tìm trong thư mục blogs nếu chỉ có tên file
                Glide.with(holder.itemView.getContext())
                        .load("file:///android_asset/images/blogs/" + cleanPath)
                        .placeholder(R.color.neutral_light_grey)
                        .error(R.color.neutral_light_grey)
                        .into(holder.ivBlog);
            }
        } else {
            // NẾU IMAGEURL TRỐNG: Tự động lấy một tấm ảnh mặc định trong kho của bạn
            Glide.with(holder.itemView.getContext())
                    .load("file:///android_asset/images/blogs/hat-dinh-duong-suc-khoe.jpg")
                    .placeholder(R.color.neutral_light_grey)
                    .into(holder.ivBlog);
        }
    }

    @Override
    public int getItemCount() {
        return blogs.size();
    }

    static class BlogViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBlog;
        TextView tvTitle, tvSummary;

        public BlogViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBlog = itemView.findViewById(R.id.ivBlog);
            tvTitle = itemView.findViewById(R.id.tvBlogTitle);
            tvSummary = itemView.findViewById(R.id.tvBlogSummary);
        }
    }
}
