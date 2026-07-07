package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangeEmailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeEmailActivity extends AppCompatActivity {
    private ActivityChangeEmailBinding binding;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

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
            String email = binding.etNewEmail.getText().toString().trim();
            String otp = binding.etOtp.getText().toString().trim();
            
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email mới", Toast.LENGTH_SHORT).show();
                return;
            }

            // Giả lập logic kiểm tra: Nếu nhập "123456" thì thành công
            if ("123456".equals(otp)) {
                binding.tvErrorOtp.setVisibility(View.GONE);
                
                // Thực hiện update Firestore
                db.collection("users").document(userId)
                        .update("email", email)
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
