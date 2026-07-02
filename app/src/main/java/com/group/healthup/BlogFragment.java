package com.group.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.group.adapters.BlogAdapter;
import com.group.healthup.firebase.FirestoreManager;
import com.group.models.Blog;
import java.util.ArrayList;
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

    private void fetchBlogs() {
        FirestoreManager.getInstance().getFirestore().collection("blogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
                    filterBlogs();
                });
    }

    private void filterBlogs() {
        filteredList.clear();
        String allCats = getString(R.string.all_categories);
        for (Blog blog : blogList) {
            if (currentCategory.equals(allCats) || blog.getCategory().equals(currentCategory)) {
                filteredList.add(blog);
            }
        }
        blogAdapter.notifyDataSetChanged();
    }
}
