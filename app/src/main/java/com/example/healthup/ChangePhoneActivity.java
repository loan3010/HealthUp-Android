package com.example.healthup;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.databinding.ActivityChangePhoneBinding;
import com.example.healthup.util.PhoneNormalizer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangePhoneActivity extends AppCompatActivity {
    private ActivityChangePhoneBinding binding;
    private FirebaseFirestore db;
    private OtpRepository otpRepository;
    private String userId;
    private String pendingPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePhoneBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        otpRepository = new OtpRepository();
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        } else {
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvGetOtp.setOnClickListener(v -> checkUniqueThenSend());
        binding.btnSave.setOnClickListener(v -> confirmOtp());
    }

    private void checkUniqueThenSend() {
        String phone = binding.etNewPhone.getText().toString().trim();
        if (!PhoneNormalizer.isValidLocalPhone(phone)) {
            binding.tvErrorPhone.setText(R.string.login_phone_invalid_error);
            binding.tvErrorPhone.setVisibility(View.VISIBLE);
            return;
        }
        
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        binding.tvErrorPhone.setVisibility(View.GONE);
        setLoading(true);

        // 1. Kiểm tra SĐT đã tồn tại chưa
        db.collection("users")
                .whereEqualTo("phone", normalizedPhone)
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    if (!q.isEmpty() && !userId.equals(q.getDocuments().get(0).getId())) {
                        setLoading(false);
                        binding.tvErrorPhone.setText(R.string.register_phone_exists);
                        binding.tvErrorPhone.setVisibility(View.VISIBLE);
                        return;
                    }
                    // 2. Gửi OTP thật qua registration_otp collection
                    sendMockOtp(normalizedPhone);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi kiểm tra số điện thoại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendMockOtp(String phone) {
        String otp = otpRepository.generateOtp();
        // Dùng COLLECTION_PHONE_VERIFICATION (giờ là registration_otp)
        otpRepository.savePhoneVerificationOtp(userId, phone, otp)
                .addOnSuccessListener(unused -> {
                    pendingPhone = phone;
                    setLoading(false);
                    showOtpUi(otp);
                    Toast.makeText(this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi gửi mã OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showOtpUi(@Nullable String debugOtp) {
        if (!TextUtils.isEmpty(debugOtp)) {
            binding.tvDebugOtp.setVisibility(View.VISIBLE);
            binding.tvDebugOtp.setText(getString(R.string.otp_debug_label, debugOtp));
        }
    }

    private void confirmOtp() {
        String code = binding.etOtp.getText().toString().trim();
        if (TextUtils.isEmpty(pendingPhone)) {
            Toast.makeText(this, "Vui lòng nhận mã OTP trước", Toast.LENGTH_SHORT).show();
            return;
        }
        if (code.length() < 6) {
            binding.tvErrorOtp.setVisibility(View.VISIBLE);
            binding.tvErrorOtp.setText(R.string.otp_invalid_length);
            return;
        }

        setLoading(true);
        otpRepository.getPhoneVerificationDoc(userId)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || otpRepository.isOtpExpired(doc)
                            || !otpRepository.matchesOtp(doc, code)) {
                        setLoading(false);
                        binding.tvErrorOtp.setVisibility(View.VISIBLE);
                        binding.tvErrorOtp.setText(R.string.otp_invalid_code);
                        return;
                    }
                    
                    binding.tvErrorOtp.setVisibility(View.GONE);
                    // Cập nhật SĐT vào hồ sơ người dùng
                    db.collection("users").document(userId)
                            .update("phone", pendingPhone)
                            .addOnSuccessListener(unused -> {
                                otpRepository.deletePhoneVerificationDoc(userId);
                                setLoading(false);
                                UIUtils.showSuccessDialog(this, this::finish);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Lỗi cập nhật số điện thoại", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi xác thực OTP", Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.tvGetOtp.setEnabled(!loading);
        binding.btnSave.setEnabled(!loading);
        binding.etNewPhone.setEnabled(!loading);
        binding.etOtp.setEnabled(!loading);
    }
}
