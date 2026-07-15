package com.example.healthup;


import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.healthup.databinding.FragmentSettingsBinding;
import com.example.healthup.databinding.ItemSettingRowBinding;


public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        setupUI();
        updateAuthVisibility();

        binding.btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAuthVisibility();
    }


    private void setupUI() {
        // Tài khoản
        binding.itemInfo.getRoot().setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AccountInfoActivity.class));
        });


        binding.itemAddress.getRoot().setOnClickListener(v -> {
            loadFragment(new AddressBookFragment());
        });


        binding.itemPayment.getRoot().setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), PaymentInfoActivity.class));
        });


        // FIX: Mở trang Sản phẩm yêu thích
        binding.itemWishlist.getRoot().setOnClickListener(v -> {
            loadFragment(new WishlistFragment());
        });


        setupRow(binding.itemInfo.getRoot(), "Thông tin tài khoản", "");
        setupRow(binding.itemAddress.getRoot(), "Số địa chỉ", "");
        setupRow(binding.itemPayment.getRoot(), "Thông tin thanh toán", "");
        setupRow(binding.itemWishlist.getRoot(), "Sản phẩm yêu thích", "");


        // Cài đặt
        binding.itemNotification.getRoot().setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), NotificationSettingsActivity.class));
        });


        binding.itemLanguage.getRoot().setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), LanguageSettingsActivity.class));
        });


        binding.itemAccess.getRoot().setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AccessSettingsActivity.class));
        });


        binding.itemDeleteAccount.getRoot().setOnClickListener(v -> showDeleteAccountDialog());


        setupRow(binding.itemNotification.getRoot(), "Thông báo", "");
        String currentLang = com.example.healthup.util.LocaleHelper.getLanguage(requireContext());
        String langDisplay = currentLang.equals("vi") ? "Tiếng Việt" : "English";
        setupRow(binding.itemLanguage.getRoot(), "Ngôn ngữ", langDisplay);
        setupRow(binding.itemAccess.getRoot(), "Quyền truy cập", "");

        ItemSettingRowBinding deleteBinding = ItemSettingRowBinding.bind(binding.itemDeleteAccount.getRoot());
        deleteBinding.tvTitle.setText("Xóa tài khoản");
        deleteBinding.tvTitle.setTextColor(getResources().getColor(R.color.action_error));

        // FIX: Đăng xuất hoạt động
        binding.btnLogout.setOnClickListener(v -> {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            Toast.makeText(getActivity(), "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            
            // Quay về màn hình Home hoặc Profile (đã logout)
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        // Liên hệ - Đồng bộ với OrderDetail
        binding.itemPhone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(android.net.Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        binding.itemEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:healthup@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Hỗ trợ khách hàng - HealthUp");
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(getActivity(), "Không tìm thấy ứng dụng email", Toast.LENGTH_SHORT).show();
            }
        });
    }


    // FIX: helper điều hướng sang Fragment khác từ Settings (do Settings nằm lồng trong ProfileFragment container)
    private void loadFragment(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }


    private void showDeleteAccountDialog() {
        android.app.Dialog dialog = new android.app.Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_delete_account);


        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.6f);
        }


        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmDelete);


        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(getActivity(), "Yêu cầu xóa tài khoản đã được gửi", Toast.LENGTH_SHORT).show();
        });


        dialog.show();
    }


    private void setupRow(View root, String title, String value) {
        ItemSettingRowBinding rowBinding = ItemSettingRowBinding.bind(root);
        rowBinding.tvTitle.setText(title);
        if (!value.isEmpty()) {
            rowBinding.tvValue.setVisibility(View.VISIBLE);
            rowBinding.tvValue.setText(value);
        }
    }


    private void updateAuthVisibility() {
        boolean loggedIn = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null;
        int visibility = loggedIn ? View.VISIBLE : View.GONE;

        binding.itemDeleteAccount.getRoot().setVisibility(visibility);
        binding.btnLogout.setVisibility(visibility);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
