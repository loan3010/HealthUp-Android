package com.example.healthup;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.bumptech.glide.Glide;
import com.example.models.Blog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BlogDetailActivity extends AppCompatActivity {

    private ImageView ivImage;
    private TextView tvCategory, tvTitle, tvAuthor, tvDate, tvContent;
    private Toolbar toolbar;
    private Blog blog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_detail);

        blog = (Blog) getIntent().getSerializableExtra("blog");

        initViews();
        displayBlogDetails();
    }

    private void initViews() {
        ivImage = findViewById(R.id.iv_detail_image);
        tvCategory = findViewById(R.id.tv_detail_category);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvAuthor = findViewById(R.id.tv_detail_author);
        tvDate = findViewById(R.id.tv_detail_date);
        tvContent = findViewById(R.id.tv_detail_content);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void displayBlogDetails() {
        if (blog != null) {
            tvTitle.setText(blog.getTitle());
            tvCategory.setText(blog.getCategory());
            tvAuthor.setText(getString(R.string.author_prefix, blog.getAuthor()));
            tvContent.setText(blog.getContent());

            SimpleDateFormat sdf = new SimpleDateFormat("dd 'tháng' MM, yyyy", new Locale("vi", "VN"));
            tvDate.setText(sdf.format(new Date(blog.getTimestamp())));

            String imagePath = blog.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                if (imagePath.startsWith("http")) {
                    Glide.with(this)
                            .load(imagePath)
                            .placeholder(R.drawable.ic_launcher_background)
                            .into(ivImage);
                } else {
                    String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                    if (!cleanPath.startsWith("images/")) {
                        cleanPath = "images/blogs/" + cleanPath;
                    }
                    Glide.with(this)
                            .load("file:///android_asset/" + cleanPath)
                            .placeholder(R.drawable.ic_launcher_background)
                            .into(ivImage);
                }
            }
        }
    }
}
