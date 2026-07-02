package com.group.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.group.healthup.R;
import com.group.models.Blog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.ViewHolder> {

    private List<Blog> blogList;
    private OnBlogClickListener listener;

    public interface OnBlogClickListener {
        void onBlogClick(Blog blog);
    }

    public BlogAdapter(List<Blog> blogList, OnBlogClickListener listener) {
        this.blogList = blogList;
        this.listener = listener;
    }

    public void updateList(List<Blog> newList) {
        this.blogList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blog, parent, false);
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

        Glide.with(holder.itemView.getContext())
                .load(blog.getImageUrl())
                .placeholder(R.drawable.ic_launcher_background)
                .into(holder.ivImage);

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
