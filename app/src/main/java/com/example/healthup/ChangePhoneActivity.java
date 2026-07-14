package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangePhoneBinding;
import com.example.healthup.data.repository.OtpRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangePhoneActivity extends AppCompatActivity {
    private ActivityChangePhoneBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private OtpRepository otpRepository;
    private String generatedOtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePhoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        otpRepository = new OtpRepository(db);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.tvGetOtp.setOnClickListener(v -> {
            String phone = binding.etNewPhone.getText().toString().trim();
            // Giả lập logic kiểm tra: Nếu chứa chữ @ (ví dụ nhập nhầm @abc123 như trong hình 1) hoặc trống
            if (phone.isEmpty() || phone.contains("@") || phone.length() < 10) {
                binding.tvErrorPhone.setText("Số điện thoại không hợp lệ.");
                binding.tvErrorPhone.setVisibility(View.VISIBLE);
            } else if (phone.equals("0366649188")) { // Giả lập hình 3: SĐT đã liên kết với tài khoản khác
                binding.tvErrorPhone.setText("Số điện thoại đã được liên kết với tài khoản khác.");
                binding.tvErrorPhone.setVisibility(View.VISIBLE);
            } else {
                binding.tvErrorPhone.setVisibility(View.GONE);
                
                // Tạo OTP ngẫu nhiên 6 số
                generatedOtp = otpRepository.generateOtp();
                
                // Hiển thị OTP lên màn hình (giả lập nhận tin nhắn)
                binding.tvDebugOtp.setText("Mã xác thực của bạn là: " + generatedOtp);
                binding.tvDebugOtp.setVisibility(View.VISIBLE);
                
                Toast.makeText(this, "Mã OTP đã được gửi", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            String phone = binding.etNewPhone.getText().toString().trim();
            String otp = binding.etOtp.getText().toString().trim();
            
            if (phone.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại", Toast.LENGTH_SHORT).show();
                return;
            }

            if (generatedOtp == null) {
                Toast.makeText(this, "Vui lòng nhận mã OTP trước", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra OTP người dùng nhập với mã đã tạo
            if (generatedOtp.equals(otp)) {
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
