package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.healthup.databinding.FragmentBlogBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Blog;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class BlogFragment extends Fragment {
    private FragmentBlogBinding binding;
    private BlogAdapter adapter;
    private final List<Blog> blogList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBlogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        loadBlogs();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void setupRecyclerView() {
        adapter = new BlogAdapter(blogList);
        binding.rvBlogs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBlogs.setAdapter(adapter);
    }

    private void loadBlogs() {
        binding.progressBar.setVisibility(View.VISIBLE);
        FirestoreManager.getInstance().getBlogs(task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                blogList.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        Blog blog = doc.toObject(Blog.class);
                        blog.setId(doc.getId());
                        blogList.add(blog);
                    } catch (Exception e) {
                        android.util.Log.e("BlogFragment", "Lỗi nạp blog: " + doc.getId(), e);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
