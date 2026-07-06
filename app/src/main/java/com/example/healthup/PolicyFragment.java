package com.example.healthup;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.adapters.PolicyAdapter;
import com.example.models.PolicyItem;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PolicyFragment extends Fragment {

    private static final String DEFAULT_TITLE = "Chính sách và điều khoản";

    // Views
    private TextView tvHeaderTitle, tvUpdatedAt, tvContent, tvEmptyList;
    private NestedScrollView scrollList, scrollDetail;
    private RecyclerView rvPolicy;
    private ProgressBar progressList, progressDetail;

    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_policy, container, false);

        bindViews(view);

        rvPolicy.setLayoutManager(new LinearLayoutManager(getContext()));
        db = FirebaseFirestore.getInstance();

        view.findViewById(R.id.btnBack).setOnClickListener(v -> handleBack());
        setupBackPressedCallback();

        loadPolicyList();
        return view;
    }

    private void bindViews(View view) {
        tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle);
        tvUpdatedAt = view.findViewById(R.id.tvUpdatedAt);
        tvContent = view.findViewById(R.id.tvContent);
        tvEmptyList = view.findViewById(R.id.tvEmptyList);
        scrollList = view.findViewById(R.id.scrollList);
        scrollDetail = view.findViewById(R.id.scrollDetail);
        rvPolicy = view.findViewById(R.id.rvPolicy);
        progressList = view.findViewById(R.id.progressList);
        progressDetail = view.findViewById(R.id.progressDetail);
    }

    /** Bấm nút back trong header: nếu đang xem chi tiết -> quay về list; nếu đang ở list -> thoát fragment */
    private void handleBack() {
        if (scrollDetail.getVisibility() == View.VISIBLE) {
            showListView();
        } else {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }

    /** Nút back vật lý/gesture của hệ thống cũng áp dụng logic tương tự */
    private void setupBackPressedCallback() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (scrollDetail.getVisibility() == View.VISIBLE) {
                            showListView();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    private void showListView() {
        tvHeaderTitle.setText(DEFAULT_TITLE);
        scrollDetail.setVisibility(View.GONE);
        scrollList.setVisibility(View.VISIBLE);
    }

    private void showDetailView(String title) {
        tvHeaderTitle.setText(title);
        scrollList.setVisibility(View.GONE);
        scrollDetail.setVisibility(View.VISIBLE);
    }

    // ================== LOAD DANH SÁCH ==================
    private void loadPolicyList() {
        progressList.setVisibility(View.VISIBLE);
        tvEmptyList.setVisibility(View.GONE);

        db.collection("policies")
//                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(this::onPolicyListLoaded)
                .addOnFailureListener(e -> {
                    progressList.setVisibility(View.GONE);
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Không tải được danh sách: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void onPolicyListLoaded(QuerySnapshot snapshot) {
        progressList.setVisibility(View.GONE);

        List<PolicyItem> list = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            PolicyItem item = doc.toObject(PolicyItem.class);
            item.setId(doc.getId());
            list.add(item);
        }

        if (list.isEmpty()) {
            tvEmptyList.setVisibility(View.VISIBLE);
            return;
        }

        PolicyAdapter adapter = new PolicyAdapter(list, item ->
                loadPolicyDetail(item.getId(), item.getTitle()));
        rvPolicy.setAdapter(adapter);
    }

    // ================== LOAD CHI TIẾT ==================
    private void loadPolicyDetail(String policyId, String title) {
        showDetailView(title);
        tvContent.setText("");
        tvUpdatedAt.setText("Cập nhật lần cuối: --");
        progressDetail.setVisibility(View.VISIBLE);

        db.collection("policies").document(policyId).get()
                .addOnSuccessListener(this::onPolicyDetailLoaded)
                .addOnFailureListener(e -> {
                    progressDetail.setVisibility(View.GONE);
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Không tải được nội dung: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void onPolicyDetailLoaded(DocumentSnapshot doc) {
        progressDetail.setVisibility(View.GONE);

        if (!doc.exists()) {
            tvContent.setText("Không tìm thấy nội dung.");
            return;
        }

        String content = doc.getString("content");
        if (content != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tvContent.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT));
            } else {
                tvContent.setText(Html.fromHtml(content));
            }
        }

        Timestamp updatedAt = doc.getTimestamp("updatedAt");
        if (updatedAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            tvUpdatedAt.setText("Cập nhật lần cuối: " + sdf.format(updatedAt.toDate()));
        }
    }
}