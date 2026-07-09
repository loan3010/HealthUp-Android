package com.example.healthup;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.BlogAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class BlogFragment extends Fragment {


    private RecyclerView rvBlogs;
    private BlogAdapter blogAdapter;
    private List<Blog> blogList = new ArrayList<>();
    private List<Blog> filteredList = new ArrayList<>();
    private ChipGroup chipGroup;
    private String currentCategory;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        currentCategory = getString(R.string.all_categories);
        View view = inflater.inflate(R.layout.fragment_blog, container, false);
        initViews(view);
        setupRecyclerView();
        fetchBlogs();
        return view;
    }


    private void initViews(View view) {
        rvBlogs = view.findViewById(R.id.rv_blogs);
        chipGroup = view.findViewById(R.id.chip_group_blog);


        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });


        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = group.findViewById(checkedId);
            if (chip != null) {
                currentCategory = chip.getText().toString();
                filterBlogs();
            }
        });
    }


    private void setupRecyclerView() {
        blogAdapter = new BlogAdapter(filteredList, blog -> {
            android.content.Intent intent = new android.content.Intent(getContext(), BlogDetailActivity.class);
            intent.putExtra("blog", blog);
            startActivity(intent);
        });
        rvBlogs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvBlogs.setAdapter(blogAdapter);
    }


    // FIX ROOT CAUSE: orderBy("timestamp", DESC) phía Firestore sẽ loại bỏ mọi document
    // không có field "timestamp" (hoặc kiểu dữ liệu không nhất quán) ra khỏi kết quả trả về.
    // Nếu dữ liệu blog thực tế đang bị thiếu/không đồng nhất field này thì kết quả trả về
    // trống hoàn toàn -> trang chỉ còn tiêu đề + tab, không có bài viết nào cả.
    // Sửa: lấy toàn bộ document không orderBy, tự sắp xếp bằng Java (không loại bỏ ai),
    // đồng thời thêm addOnFailureListener để không còn "im lặng" khi query lỗi.
    private void fetchBlogs() {
        FirestoreManager.getInstance().getFirestore().collection("blogs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    blogList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Blog blog = doc.toObject(Blog.class);
                        if (blog != null) {
                            blog.setId(doc.getId());
                            blogList.add(blog);
                        }
                    }
                    Collections.sort(blogList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                    filterBlogs();
                })
                .addOnFailureListener(e -> {
                    Log.e("BlogFragment", "Lỗi tải danh sách blog: " + e.getMessage());
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Không thể tải bài viết. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void filterBlogs() {
        filteredList.clear();
        String allCats = normalize(getString(R.string.all_categories));
        String normalizedCurrent = normalize(currentCategory);
        for (Blog blog : blogList) {
            String category = normalize(blog.getCategory());
            if (normalizedCurrent.equals(allCats) || (category != null && category.equals(normalizedCurrent))) {
                filteredList.add(blog);
            }
        }
        blogAdapter.notifyDataSetChanged();
    }

    private String normalize(String value) {
        if (value == null) return null;
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFC);
    }
}