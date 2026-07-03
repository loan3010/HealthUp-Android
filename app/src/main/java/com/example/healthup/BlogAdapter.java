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

        Glide.with(holder.itemView.getContext())
                .load(blog.getImageUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivBlog);
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
