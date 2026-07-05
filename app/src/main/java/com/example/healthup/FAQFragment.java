package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.healthup.databinding.FragmentFaqsBinding;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.FAQ;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class FAQFragment extends Fragment {
    private FragmentFaqsBinding binding;
    private FAQAdapter adapter;
    private final List<FAQ> fullList = new ArrayList<>();
    private final List<FAQ> filteredList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFaqsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        loadFAQs();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        binding.etSearchFaq.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new FAQAdapter(filteredList);
        binding.rvFaqs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFaqs.setAdapter(adapter);
    }

    private void loadFAQs() {
        binding.progressBar.setVisibility(View.VISIBLE);
        FirestoreManager.getInstance().getFAQs(task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                fullList.clear();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    try {
                        FAQ faq = doc.toObject(FAQ.class);
                        faq.setId(doc.getId());
                        fullList.add(faq);
                    } catch (Exception e) {
                        android.util.Log.e("FAQFragment", "Lỗi nạp FAQ: " + doc.getId(), e);
                    }
                }
                filteredList.clear();
                filteredList.addAll(fullList);
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            for (FAQ item : fullList) {
                if (item.getQuestion().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
