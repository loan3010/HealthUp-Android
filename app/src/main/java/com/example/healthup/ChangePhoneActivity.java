package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangePhoneBinding;

public class ChangePhoneActivity extends AppCompatActivity {
    private ActivityChangePhoneBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePhoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            String otp = binding.etOtp.getText().toString().trim();
            
            // Giả lập logic kiểm tra OTP: Nếu nhập "000000" thì báo lỗi (như hình 2)
            if ("000000".equals(otp)) {
                binding.tvErrorOtp.setVisibility(View.VISIBLE);
            } else if ("123456".equals(otp)) { // Giả lập mã đúng
                binding.tvErrorOtp.setVisibility(View.GONE);
                UIUtils.showSuccessDialog(this, this::finish);
            } else if (!otp.isEmpty()) {
                binding.tvErrorOtp.setVisibility(View.VISIBLE);
            }
        });
    }
}
