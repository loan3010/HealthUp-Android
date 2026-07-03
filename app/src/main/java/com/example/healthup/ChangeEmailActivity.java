package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangeEmailBinding;

public class ChangeEmailActivity extends AppCompatActivity {
    private ActivityChangeEmailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.tvGetOtp.setOnClickListener(v -> {
            String email = binding.etNewEmail.getText().toString().trim();
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tvErrorEmail.setVisibility(View.VISIBLE);
            } else {
                binding.tvErrorEmail.setVisibility(View.GONE);
                // Giả lập gửi OTP thành công
            }
        });

        binding.btnSave.setOnClickListener(v -> {
            String otp = binding.etOtp.getText().toString().trim();
            
            // Giả lập logic kiểm tra: Nếu nhập "123456" thì thành công
            if ("123456".equals(otp)) {
                binding.tvErrorOtp.setVisibility(View.GONE);
                UIUtils.showSuccessDialog(this, this::finish);
            } else if (!otp.isEmpty()) {
                binding.tvErrorOtp.setVisibility(View.VISIBLE);
            }
        });
    }
}
