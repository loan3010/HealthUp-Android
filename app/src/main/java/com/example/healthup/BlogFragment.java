package com.example.healthup;




import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;




public class BlogFragment extends Fragment {




    private RecyclerView rvBlogs;
    private BlogAdapter blogAdapter;
    private List<Blog> blogList = new ArrayList<>();
    private List<Blog> filteredList = new ArrayList<>();
    private ChipGroup chipGroup;
    private String currentCategory;
    private String searchQuery = "";
    private EditText etSearch;
    private ImageView ivClearSearch;


    // FIX (yêu cầu #1): danh sách danh mục Blog GIỮ NGUYÊN nội dung cũ (không copy danh mục
    // của trang Danh mục sản phẩm), chỉ đổi CÁCH RENDER chip để đồng bộ layout/màu sắc với
    // trang Danh mục (dùng chung item_category_chip.xml + logic updateChipStyle bên dưới).
    private final List<String> blogCategories = new ArrayList<>();




    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        currentCategory = getString(R.string.all_categories);
        View view = inflater.inflate(R.layout.fragment_blog, container, false);
        initViews(view);
        setupBlogCategories();
        setupRecyclerView();
        fetchBlogs();
        return view;
    }




    private void initViews(View view) {
        rvBlogs = view.findViewById(R.id.rv_blogs);
        chipGroup = view.findViewById(R.id.chip_group_blog);
        etSearch = view.findViewById(R.id.et_search_blog);
        ivClearSearch = view.findViewById(R.id.iv_clear_search);


        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });


        ivClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            filterBlogs();
        });


        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                ivClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                filterBlogs();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }




    // FIX (bug #1 - layout tab danh mục khác trang Danh mục): trước đây chip được khai báo
    // tĩnh sẵn trong XML với style Choice mặc định của Material, khác hoàn toàn cách hiển thị
    // (nền xanh khi chọn / viền xám khi chưa chọn, bo tròn, cùng kích thước) của trang Danh
    // mục. Giờ sinh chip động bằng chính layout item_category_chip.xml mà trang Danh mục dùng,
    // và áp dụng cùng hàm updateChipStyle() -> giao diện tab hai trang giống hệt nhau, trong
    // khi nội dung 4 danh mục của Blog vẫn giữ nguyên như cũ (không đụng tới danh mục sản phẩm).
    private void setupBlogCategories() {
        blogCategories.clear();
        blogCategories.addAll(Arrays.asList(
                getString(R.string.all_categories),
                "Dinh dưỡng",
                "Sức khỏe",
                "Công thức",
                "Kiến thức"
        ));
        setupCategoryChips();
    }




    private void setupCategoryChips() {
        chipGroup.removeAllViews();
        for (String name : blogCategories) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroup, false);
            chip.setText(name);
            chip.setCheckable(true);


            boolean isSelected = name.equals(currentCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);


            chip.setOnClickListener(v -> {
                currentCategory = name;
                refreshChipGroupUI();
                filterBlogs();
            });
            chipGroup.addView(chip);
        }
    }




    private void refreshChipGroupUI() {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            boolean isSelected = chip.getText().toString().equals(currentCategory);
            chip.setChecked(isSelected);
            updateChipStyle(chip, isSelected);
        }
    }




    // Giống hệt logic màu sắc trong ProductListFragment.updateChipStyle() để 2 trang đồng bộ.
    private void updateChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.primary_green);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(getResources().getColor(R.color.text_dark));
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(R.color.border_color);
        }
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
            boolean matchesCategory = normalizedCurrent.equals(allCats) || (category != null && category.equals(normalizedCurrent));


            boolean matchesSearch = searchQuery.isEmpty() ||
                    (blog.getTitle() != null && blog.getTitle().toLowerCase().contains(searchQuery));


            if (matchesCategory && matchesSearch) {
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