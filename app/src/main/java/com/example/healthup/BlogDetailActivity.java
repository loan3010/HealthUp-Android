package com.example.healthup;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class BlogDetailActivity extends BaseAppCompatActivity {


    private ImageView ivImage;
    private TextView tvCategory, tvTitle, tvAuthor, tvDate, tvContent;
    private Toolbar toolbar;
    private Blog blog;


    // FIX: thêm phần "Bài viết liên quan" theo yêu cầu.
    private RecyclerView rvRelated;
    private BlogAdapter relatedAdapter;
    private final List<Blog> relatedList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog_detail);


        blog = (Blog) getIntent().getSerializableExtra("blog");


        initViews();
        displayBlogDetails();
        setupRelatedBlogs();
        fetchRelatedBlogs();
    }


    private void initViews() {
        ivImage = findViewById(R.id.iv_detail_image);
        tvCategory = findViewById(R.id.tv_detail_category);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvAuthor = findViewById(R.id.tv_detail_author);
        tvDate = findViewById(R.id.tv_detail_date);
        tvContent = findViewById(R.id.tv_detail_content);
        toolbar = findViewById(R.id.toolbar);
        rvRelated = findViewById(R.id.rv_related_blogs);


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


    // FIX: RecyclerView nằm trong NestedScrollView nên tắt cuộn riêng để không xung đột
    // cuộn với trang; tái sử dụng đúng BlogAdapter + item_blog.xml để đồng bộ giao diện
    // với trang danh sách Blog. Khi bấm vào 1 bài liên quan, mở lại chính màn hình này
    // với dữ liệu bài mới (giữ back stack để quay lại bài đang đọc trước đó).
    private void setupRelatedBlogs() {
        if (rvRelated == null) return;
        relatedAdapter = new BlogAdapter(relatedList, clickedBlog -> {
            Intent intent = new Intent(BlogDetailActivity.this, BlogDetailActivity.class);
            intent.putExtra("blog", clickedBlog);
            startActivity(intent);
        });
        rvRelated.setLayoutManager(new LinearLayoutManager(this));
        rvRelated.setAdapter(relatedAdapter);
        rvRelated.setNestedScrollingEnabled(false);
    }


    // FIX: lấy các bài viết khác (loại trừ bài đang xem), ưu tiên bài cùng danh mục lên trước,
    // giới hạn 6 bài để tránh trang quá dài.
    private void fetchRelatedBlogs() {
        if (blog == null) return;
        FirestoreManager.getInstance().getFirestore().collection("blogs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Blog> sameCategory = new ArrayList<>();
                    List<Blog> others = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Blog b = doc.toObject(Blog.class);
                        if (b == null) continue;
                        b.setId(doc.getId());
                        if (b.getId() != null && b.getId().equals(blog.getId())) continue;
                        if (blog.getCategory() != null && blog.getCategory().equals(b.getCategory())) {
                            sameCategory.add(b);
                        } else {
                            others.add(b);
                        }
                    }
                    Collections.sort(sameCategory, (a, c) -> Long.compare(c.getTimestamp(), a.getTimestamp()));
                    Collections.sort(others, (a, c) -> Long.compare(c.getTimestamp(), a.getTimestamp()));


                    relatedList.clear();
                    relatedList.addAll(sameCategory);
                    relatedList.addAll(others);
                    while (relatedList.size() > 6) {
                        relatedList.remove(relatedList.size() - 1);
                    }
                    if (relatedAdapter != null) relatedAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("BlogDetailActivity", "Lỗi tải bài viết liên quan: " + e.getMessage()));
    }
}