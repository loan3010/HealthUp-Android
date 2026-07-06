package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;

public class PhoneVerificationFragment extends Fragment {

    private EditText etPhone;
    private TextView tvErrorPhone, tvStatusTitle, tvStatusDesc, tvTitle, tvDescription, tvPrefix;
    private View layoutStatus, layoutBottomHint, tvChangePhone, layoutInputPhone;
    private Button btnContinue;

    private boolean isChecking = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone_verification, container, false);
        bindViews(view);
        setupListeners();
        return view;
    }

    private void bindViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        
        tvTitle = view.findViewById(R.id.tvTitle);
        tvDescription = view.findViewById(R.id.tvDescription);
        etPhone = view.findViewById(R.id.etPhone);
        tvErrorPhone = view.findViewById(R.id.tvErrorPhone);
        tvPrefix = view.findViewById(R.id.tvPrefix);
        
        layoutInputPhone = view.findViewById(R.id.layoutInputPhone);
        layoutStatus = view.findViewById(R.id.layoutStatus);
        tvStatusTitle = view.findViewById(R.id.tvStatusTitle);
        tvStatusDesc = view.findViewById(R.id.tvStatusDesc);
        
        btnContinue = view.findViewById(R.id.btnContinue);
        tvChangePhone = view.findViewById(R.id.tvChangePhone);
        layoutBottomHint = view.findViewById(R.id.layoutBottomHint);
    }

    private void setupListeners() {
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetUI();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnContinue.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            if (phone.length() < 9) {
                showError();
                return;
            }

            if (!isChecking) {
                checkPhoneNumber(phone);
            } else {
                // Logic tiếp theo (Đăng nhập / Đăng ký)
                if (btnContinue.getText().toString().equals("Đăng nhập ngay")) {
                    Toast.makeText(getContext(), "Chuyển sang màn hình Đăng nhập", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Chuyển sang màn hình Đăng ký", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void resetUI() {
        layoutInputPhone.setBackgroundResource(R.drawable.bg_input_normal);
        tvErrorPhone.setVisibility(View.GONE);
        layoutStatus.setVisibility(View.GONE);
        tvChangePhone.setVisibility(View.GONE);
        layoutBottomHint.setVisibility(View.VISIBLE);
        btnContinue.setText("Tiếp tục");
        isChecking = false;
        tvPrefix.setVisibility(View.GONE);
    }

    private void showError() {
        layoutInputPhone.setBackgroundResource(R.drawable.bg_input_error);
        tvErrorPhone.setVisibility(View.VISIBLE);
    }

    private void checkPhoneNumber(String phone) {
        // Giả lập check Firestore
        btnContinue.setEnabled(false);
        btnContinue.setText("Đang kiểm tra...");

        // Chuẩn hóa số điện thoại để hiển thị
        String displayPhone = phone.startsWith("0") ? phone.substring(1) : phone;
        displayPhone = "+84  " + displayPhone.replaceAll("(\\d{3})(\\d{3})(\\d{3})", "$1 $2 $3");

        final String finalDisplayPhone = displayPhone;

        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("phone", phone)
                .get()
                .addOnSuccessListener(snapshot -> {
                    btnContinue.setEnabled(true);
                    isChecking = true;
                    tvTitle.setText("Xác minh");
                    tvDescription.setVisibility(View.GONE);
                    tvPrefix.setVisibility(View.VISIBLE);
                    tvPrefix.setText("+84");
                    etPhone.setText(phone.startsWith("0") ? phone.substring(1) : phone);
                    etPhone.setEnabled(false);
                    
                    layoutStatus.setVisibility(View.VISIBLE);
                    layoutBottomHint.setVisibility(View.GONE);
                    tvChangePhone.setVisibility(View.VISIBLE);

                    if (!snapshot.isEmpty()) {
                        // Tài khoản đã tồn tại
                        tvStatusTitle.setText("Tài khoản đã tồn tại.");
                        tvStatusTitle.setTextColor(getResources().getColor(R.color.green_primary));
                        tvStatusDesc.setText("Số điện thoại đã được đăng ký. Vui lòng đăng nhập để tiếp tục mua hàng với các ưu đãi dành riêng cho thành viên.");
                        btnContinue.setText("Đăng nhập ngay");
                    } else {
                        // Chưa đăng ký
                        tvStatusTitle.setText("Số điện thoại này chưa được đăng ký.");
                        tvStatusTitle.setTextColor(getResources().getColor(R.color.green_primary));
                        tvStatusDesc.setText("Vui lòng đăng ký tài khoản để tiếp tục mua hàng và nhận ưu đãi dành riêng cho bạn.");
                        btnContinue.setText("Đăng ký ngay");
                    }
                })
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    btnContinue.setText("Tiếp tục");
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
