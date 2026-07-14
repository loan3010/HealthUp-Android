package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.example.healthup.databinding.ActivityChangePhoneBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangePhoneActivity extends BaseAppCompatActivity {
    private ActivityChangePhoneBinding binding;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePhoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.tvGetOtp.setOnClickListener(v -> {
            String phone = binding.etNewPhone.getText().toString().trim();
            // Giả lập logic kiểm tra: Nếu chứa chữ @ (ví dụ nhập nhầm @abc123 như trong hình 1) hoặc trống
            if (phone.isEmpty() || phone.contains("@")) {
                binding.tvErrorPhone.setText("Số điện thoại không hợp lệ.");
                binding.tvErrorPhone.setVisibility(View.VISIBLE);
            } else if (phone.equals("0366649188")) { // Giả lập hình 3: SĐT đã liên kết với tài khoản khác
                binding.tvErrorPhone.setText("Số điện thoại đã được liên kết với tài khoản khác.");
                binding.tvErrorPhone.setVisibility(View.VISIBLE);
            } else {
                binding.tvErrorPhone.setVisibility(View.GONE);
                // Giả lập gửi OTP thành công
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            String phone = binding.etNewPhone.getText().toString().trim();
            String otp = binding.etOtp.getText().toString().trim();
            
            if (phone.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại", Toast.LENGTH_SHORT).show();
                return;
            }

            // Giả lập logic kiểm tra OTP: "123456" là mã đúng
            if ("123456".equals(otp)) {
                binding.tvErrorOtp.setVisibility(View.GONE);
                
                // Thực hiện update Firestore
                db.collection("users").document(userId)
                        .update("phone", phone)
                        .addOnSuccessListener(aVoid -> {
                            UIUtils.showSuccessDialog(this, this::finish);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                binding.tvErrorOtp.setVisibility(View.VISIBLE);
            }
        });
    }
}
