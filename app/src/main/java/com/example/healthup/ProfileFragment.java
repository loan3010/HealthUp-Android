package com.example.healthup;


import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.healthup.util.StaffRoleHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;


import java.text.NumberFormat;
import java.util.Locale;


public class ProfileFragment extends Fragment {


    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_SPENT = "spentAmount";
    private static final String FIELD_AVATAR_URL = "avatarUrl";
    private static final long MUC_VIP = 5_000_000;


    private FirebaseAuth mAuth;
    private FirebaseFirestore db;


    private ImageView imgAvatar;
    private View groupLoggedOut, groupLoggedIn, cardTichLuy;
    private View rowSellerInbox;
    private View cardStaffInbox;
    private TextView tvName, tvTier, tvSpent, tvProgressHint;
    private ProgressBar progressTichLuy;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);


        imgAvatar = view.findViewById(R.id.img_avatar);
        groupLoggedOut = view.findViewById(R.id.group_logged_out);
        groupLoggedIn = view.findViewById(R.id.group_logged_in);
        cardTichLuy = view.findViewById(R.id.card_tich_luy);
        rowSellerInbox = view.findViewById(R.id.row_seller_inbox);
        cardStaffInbox = view.findViewById(R.id.card_staff_inbox);
        tvName = view.findViewById(R.id.tv_name);
        tvTier = view.findViewById(R.id.tv_tier);
        tvSpent = view.findViewById(R.id.tv_spent);
        tvProgressHint = view.findViewById(R.id.tv_progress_hint);
        progressTichLuy = view.findViewById(R.id.progress_tich_luy);


        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();


        setupNavigation(view);
        updateAuthUi(view);
        loadUserData();


        View rowAddressBook = view.findViewById(R.id.row_address_book);
        if (rowAddressBook != null) {
            rowAddressBook.setOnClickListener(v -> loadFragment(new AddressBookFragment()));
        }


        View rowPolicy = view.findViewById(R.id.row_policy);
        if (rowPolicy != null) {
            rowPolicy.setOnClickListener(v -> {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new PolicyFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }


        return view;
    }


    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            updateAuthUi(view);
            loadUserData(true);
        }
    }


    private void setupNavigation(View view) {
        view.findViewById(R.id.btn_settings).setOnClickListener(v ->
                loadFragment(new SettingsFragment()));


        view.findViewById(R.id.btn_chat).setOnClickListener(v ->
                startActivity(ChatActivity.buyerIntent(requireContext())));


        view.findViewById(R.id.btn_dang_ky).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RegisterActivity.class)));


        view.findViewById(R.id.btn_dang_nhap).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));


        view.findViewById(R.id.btn_view_all_orders).setOnClickListener(v ->
                openOrderHistoryWithTab(0));


        view.findViewById(R.id.order_pending).setOnClickListener(v ->
                openOrderHistoryWithTab(1));


        view.findViewById(R.id.order_pickup).setOnClickListener(v ->
                openOrderHistoryWithTab(2));


        view.findViewById(R.id.order_shipping).setOnClickListener(v ->
                openOrderHistoryWithTab(3));


        view.findViewById(R.id.order_delivered).setOnClickListener(v ->
                openOrderHistoryWithTab(4));


        view.findViewById(R.id.order_returned).setOnClickListener(v ->
                openOrderHistoryWithTab(5));


        View btnLogout = view.findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                updateAuthUi(view);
                loadUserData();
            });
        }


        View wishlistCard = findRowByText(view, "Sản phẩm\nyêu thích");
        if (wishlistCard != null) {
            wishlistCard.setOnClickListener(v -> loadFragment(new WishlistFragment()));
        }


        View addressBookCard = findRowByText(view, "Sổ địa chỉ");
        if (addressBookCard != null) {
            addressBookCard.setOnClickListener(v -> loadFragment(new AddressBookFragment()));
        }


        View aboutRow = findRowByText(view, "Về HealthUp");
        if (aboutRow != null) {
            aboutRow.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AboutActivity.class)));
        }


        View faqRow = findRowByText(view, "Trung tâm trợ giúp - FAQs");
        if (faqRow != null) {
            faqRow.setOnClickListener(v -> loadFragment(new FAQFragment()));
        }


        View policyRow = findRowByText(view, "Chính sách & Điều khoản");
        if (policyRow != null) {
            policyRow.setOnClickListener(v -> loadFragment(new PolicyFragment()));
        }


        View chatRow = findRowByText(view, "Trò chuyện cùng HealthUp");
        if (chatRow != null) {
            chatRow.setOnClickListener(v ->
                    startActivity(ChatActivity.buyerIntent(requireContext())));
        }


        // FIX: nút "Blog HealthUp" trước đây không có tác dụng gì vì chưa được gắn
        // OnClickListener. Giờ mở BlogFragment (đã có giao diện danh sách blog đầy đủ),
        // đồng bộ với cách các dòng menu khác (FAQ, Chính sách, Về HealthUp...) đang điều hướng.
        View blogRow = findRowByText(view, "Blog HealthUp");
        if (blogRow != null) {
            blogRow.setOnClickListener(v -> loadFragment(new BlogFragment()));
        }


        if (rowSellerInbox != null) {
            rowSellerInbox.setOnClickListener(v -> openSellerInbox());
        }
        if (cardStaffInbox != null) {
            cardStaffInbox.setOnClickListener(v -> openSellerInbox());
        }
    }


    private void openSellerInbox() {
        startActivity(new Intent(requireContext(), SellerChatListActivity.class));
    }


    private View findRowByText(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) {
            ViewParent parent = root.getParent();
            while (parent instanceof View && !(parent instanceof LinearLayout)) {
                parent = ((View) parent).getParent();
            }
            return parent instanceof View ? (View) parent : root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findRowByText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }


    private void updateAuthUi(View view) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean loggedIn = currentUser != null;
        groupLoggedOut.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        groupLoggedIn.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        cardTichLuy.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        View btnLogout = view.findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        }
    }


    private void loadUserData() {
        loadUserData(false);
    }


    private void loadUserData(boolean preferServer) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        View view = getView();
        if (view == null) return;


        groupLoggedOut.setVisibility(currentUser == null ? View.VISIBLE : View.GONE);
        groupLoggedIn.setVisibility(currentUser == null ? View.GONE : View.VISIBLE);
        cardTichLuy.setVisibility(currentUser == null ? View.GONE : View.VISIBLE);


        View btnLogout = view.findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setVisibility(currentUser == null ? View.GONE : View.VISIBLE);
        }


        if (currentUser == null) {
            updateStaffInboxVisibility(false);
            return;
        }


        db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .get(preferServer ? Source.SERVER : Source.DEFAULT)
                .addOnSuccessListener(this::bindUserToUi)
                .addOnFailureListener(e -> {
                    updateStaffInboxVisibility(false);
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Lỗi tải dữ liệu: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void bindUserToUi(DocumentSnapshot document) {
        if (!document.exists()) {
            updateStaffInboxVisibility(false);
            return;
        }

        String name = document.getString("fullName");
        if (name == null || name.isEmpty()) {
            name = document.getString(FIELD_NAME);
        }
        if (name == null || name.isEmpty()) {
            name = document.getString("displayName");
        }
        if (name == null || name.isEmpty()) {
            name = "Người dùng";
        }


        String tier = document.getString(FIELD_TIER);
        String avatarUrl = document.getString(FIELD_AVATAR_URL);
        Long spentLong = document.getLong(FIELD_SPENT);
        long spent = spentLong != null ? spentLong : 0;


        tvName.setText(name);
        tvTier.setText(tier != null ? tier : "Thành viên");


        NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvSpent.setText("Đã chi: " + vnFormat.format(spent) + " VND");


        long conLai = MUC_VIP - spent;
        if (conLai > 0) {
            tvProgressHint.setText("Mua thêm " + vnFormat.format(conLai) + " VND nhận ưu đãi VIP!");
            progressTichLuy.setProgress((int) ((spent * 100) / MUC_VIP));
        } else {
            tvProgressHint.setText("Bạn đã đạt hạng VIP!");
            progressTichLuy.setProgress(100);
        }


        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_account_default)
                    .circleCrop()
                    .into(imgAvatar);
        }


        updateStaffInboxVisibility(StaffRoleHelper.isStaff(document));
    }


    private void updateStaffInboxVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (rowSellerInbox != null) {
            rowSellerInbox.setVisibility(visibility);
        }
        if (cardStaffInbox != null) {
            cardStaffInbox.setVisibility(visibility);
        }
    }


    private void openOrderHistoryWithTab(int tabIndex) {
        OrderHistoryFragment fragment = new OrderHistoryFragment();
        Bundle args = new Bundle();
        args.putInt("initial_tab", tabIndex);
        fragment.setArguments(args);
        loadFragment(fragment);
    }


    private void loadFragment(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}