package com.group.healthup;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.group.healthup.databinding.ActivityAccountInfoBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AccountInfoActivity extends AppCompatActivity {
    private ActivityAccountInfoBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private boolean isShowPassword = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
            loadUserInfo();
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveUserInfo());
        
        // --- CÁC SỰ KIỆN CLICK MỞ MÀN HÌNH CHỈNH SỬA ---
        
        // Chỉnh sửa Tên người dùng
        View.OnClickListener changeUsernameListener = v -> {
            startActivity(new android.content.Intent(this, ChangeUsernameActivity.class));
        };
        binding.containerUsername.setOnClickListener(changeUsernameListener);
        binding.ivEditUsername.setOnClickListener(changeUsernameListener);

        // Chỉnh sửa Email
        View.OnClickListener changeEmailListener = v -> {
            startActivity(new android.content.Intent(this, ChangeEmailActivity.class));
        };
        binding.containerEmail.setOnClickListener(changeEmailListener);
        binding.ivEditEmail.setOnClickListener(changeEmailListener);

        // Chỉnh sửa Số điện thoại
        View.OnClickListener changePhoneListener = v -> {
            startActivity(new android.content.Intent(this, ChangePhoneActivity.class));
        };
        binding.containerPhone.setOnClickListener(changePhoneListener);
        binding.ivEditPhone.setOnClickListener(changePhoneListener);

        // Chỉnh sửa Mật khẩu
        View.OnClickListener changePasswordListener = v -> {
            startActivity(new android.content.Intent(this, ChangePasswordActivity.class));
        };
        binding.containerPassword.setOnClickListener(changePasswordListener);
        binding.ivEditPassword.setOnClickListener(changePasswordListener);

        // Toggle Password Visibility (Icon con mắt)
        binding.ivShowPasswordAccount.setOnClickListener(v -> {
            isShowPassword = !isShowPassword;
            if (isShowPassword) {
                binding.etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                binding.ivShowPasswordAccount.setImageResource(R.drawable.ic_eye_show);
            } else {
                binding.etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                binding.ivShowPasswordAccount.setImageResource(R.drawable.ic_eye_hide);
            }
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });
    }

    private void loadUserInfo() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        binding.etFullName.setText(documentSnapshot.getString("fullName"));
                        binding.etUsername.setText(documentSnapshot.getString("username"));
                        binding.etEmail.setText(documentSnapshot.getString("email"));
                        
                        String gender = documentSnapshot.getString("gender");
                        if ("Nam".equals(gender)) binding.rbMale.setChecked(true);
                        else if ("Nữ".equals(gender)) binding.rbFemale.setChecked(true);
                        else binding.rbOther.setChecked(true);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi tải thông tin", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserInfo() {
        String fullName = binding.etFullName.getText().toString().trim();
        String gender = binding.rbMale.isChecked() ? "Nam" : (binding.rbFemale.isChecked() ? "Nữ" : "Khác");

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập họ tên", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);

        db.collection("users").document(userId).update(user)
                .addOnSuccessListener(aVoid -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UIUtils.showSuccessDialog(this, null);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
