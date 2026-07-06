package com.example.healthup;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    // Tên collection và các field trong Firestore - chỉnh lại nếu project bạn đặt tên khác
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_SPENT = "spentAmount";
    private static final String FIELD_AVATAR_URL = "avatarUrl"; // link ảnh trên Firebase Storage

    private static final long MUC_VIP = 5_000_000; // mốc chi tiêu lên VIP, chỉnh lại theo logic thật

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ImageView imgAvatar;
    private TextView tvName, tvUsername, tvTier, tvSpent, tvProgressHint;
    private ProgressBar progressTichLuy;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        imgAvatar = view.findViewById(R.id.img_avatar);
        tvName = view.findViewById(R.id.tv_name);
        tvUsername = view.findViewById(R.id.tv_username);
        tvTier = view.findViewById(R.id.tv_tier);
        tvSpent = view.findViewById(R.id.tv_spent);
        tvProgressHint = view.findViewById(R.id.tv_progress_hint);
        progressTichLuy = view.findViewById(R.id.progress_tich_luy);
        // tv_phone / tv_email KHÔNG bind Firebase nữa - đây là thông tin liên hệ
        // cố định của HealthUp, giữ nguyên giá trị tĩnh đã ghi sẵn trong fragment_profile.xml

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserData();

        view.findViewById(R.id.row_address_book).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, new AddressManagementFragment())
                    .addToBackStack(null)
                    .commit();
        });

        view.findViewById(R.id.row_policy).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, new PolicyFragment())
                    .addToBackStack(null)
                    .commit();
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            // TODO: điều hướng về màn Login sau khi đăng xuất
        });

        return view;
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        View view = getView();
        if (view == null) return;

        if (currentUser == null) {
            view.findViewById(R.id.group_logged_out).setVisibility(View.VISIBLE);
            view.findViewById(R.id.group_logged_in).setVisibility(View.GONE);
            view.findViewById(R.id.card_tich_luy).setVisibility(View.GONE);
            view.findViewById(R.id.btn_logout).setVisibility(View.GONE);
            return;
        }

        view.findViewById(R.id.group_logged_out).setVisibility(View.GONE);
        view.findViewById(R.id.group_logged_in).setVisibility(View.VISIBLE);
        view.findViewById(R.id.card_tich_luy).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btn_logout).setVisibility(View.VISIBLE);

        String uid = currentUser.getUid();

        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(this::bindUserToUi)
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Lỗi tải dữ liệu: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindUserToUi(DocumentSnapshot document) {
        if (!document.exists()) {
            Toast.makeText(requireContext(), "Không tìm thấy dữ liệu người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = document.getString(FIELD_NAME);
        String username = document.getString(FIELD_USERNAME);
        String tier = document.getString(FIELD_TIER);
        String avatarUrl = document.getString(FIELD_AVATAR_URL);
        Long spentLong = document.getLong(FIELD_SPENT);
        long spent = spentLong != null ? spentLong : 0;

        tvName.setText(name != null ? name : "");
        tvUsername.setText(username != null ? username : "");
        tvTier.setText(tier != null ? tier : "Thành viên");

        NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvSpent.setText("Đã chi: " + vnFormat.format(spent) + " VND");

        long conLai = MUC_VIP - spent;
        if (conLai > 0) {
            tvProgressHint.setText("Mua thêm " + vnFormat.format(conLai) + " VND nhận ưu đãi VIP!");
            int progress = (int) ((spent * 100) / MUC_VIP);
            progressTichLuy.setProgress(progress);
        } else {
            tvProgressHint.setText("Bạn đã đạt hạng VIP!");
            progressTichLuy.setProgress(100);
        }

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .circleCrop()
                    .into(imgAvatar);
        }
    }
}