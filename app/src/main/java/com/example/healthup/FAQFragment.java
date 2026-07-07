package com.example.healthup;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.healthup.R;
import com.example.adapters.FAQAdapter;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.FAQ;
import java.util.ArrayList;
import java.util.List;


public class FAQFragment extends Fragment {


    private RecyclerView rvFaqs;
    private FAQAdapter faqAdapter;
    private List<FAQ> fullFaqList = new ArrayList<>();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_faqs, container, false);
        initViews(view);
        setupFaqList();
        fetchFaqs();
        return view;
    }


    private void initViews(View view) {
        rvFaqs = view.findViewById(R.id.rv_faqs);


        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });


        view.findViewById(R.id.btn_contact_support).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:19001234"));
            startActivity(intent);
        });


        setupSocialButtons(view);
    }


    // FIX: các icon mạng xã hội trước đây đều dùng chung ic_notifications (icon mặc định,
    // không đúng ý nghĩa). Đổi sang tái sử dụng đúng bộ icon mạng xã hội thật (Facebook,
    // Instagram, Threads, TikTok) giống hệt đang dùng ở trang Tài khoản.
    private void setupSocialButtons(View view) {
        View btnFb = view.findViewById(R.id.btn_fb);
        View btnIg = view.findViewById(R.id.btn_ig);
        View btnThreads = view.findViewById(R.id.btn_threads);
        View btnTiktok = view.findViewById(R.id.btn_tiktok);


        if (btnFb != null) btnFb.setOnClickListener(v -> openUrl("https://facebook.com/healthup"));
        if (btnIg != null) btnIg.setOnClickListener(v -> openUrl("https://instagram.com/healthup"));
        if (btnThreads != null) btnThreads.setOnClickListener(v -> openUrl("https://www.threads.net/@healthup"));
        if (btnTiktok != null) btnTiktok.setOnClickListener(v -> openUrl("https://tiktok.com/@healthup"));
    }


    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
        }
    }


    private void setupFaqList() {
        faqAdapter = new FAQAdapter(new ArrayList<>());
        rvFaqs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFaqs.setAdapter(faqAdapter);
        rvFaqs.setNestedScrollingEnabled(false);
    }


    // FIX: trước đây lọc theo Danh mục trợ giúp + từ khóa tìm kiếm, nhưng câu hỏi chưa được
    // gắn đúng danh mục trong Firestore -> bấm vào danh mục nào cũng ra danh sách trống.
    // Theo yêu cầu: bỏ luôn Danh mục trợ giúp và thanh tìm kiếm, hiển thị toàn bộ
    // Câu hỏi thường gặp ngay khi vào trang.
    private void fetchFaqs() {
        FirestoreManager.getInstance().getFirestore().collection("faqs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;
                    fullFaqList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        FAQ faq = doc.toObject(FAQ.class);
                        if (faq != null) {
                            faq.setId(doc.getId());
                            fullFaqList.add(faq);
                        }
                    }
                    faqAdapter.updateList(new ArrayList<>(fullFaqList));
                });
    }
}