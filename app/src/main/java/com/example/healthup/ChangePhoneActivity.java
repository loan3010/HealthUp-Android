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

import java.util.Map;

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
        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            binding.tvErrorPhone.setText(R.string.login_phone_length_error);
            binding.tvErrorPhone.setVisibility(View.VISIBLE);
            return;
        }
        
        String normalizedPhone = PhoneNormalizer.normalize(phone);
        binding.tvErrorPhone.setVisibility(View.GONE);
        setLoading(true);

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
                    sendMockOtp(normalizedPhone);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi kiểm tra số điện thoại", Toast.LENGTH_SHORT).show();
                });
    }

    private void sendMockOtp(String phone) {
        String otp = otpRepository.generateOtp();
        otpRepository.savePhoneVerificationOtp(userId, phone, otp)
                .addOnSuccessListener(unused -> {
                    pendingPhone = phone;
                    setLoading(false);
                    showOtpUi(otp);
                    Toast.makeText(this, "Mã OTP đã được gửi", Toast.LENGTH_SHORT).show();
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
        if (TextUtils.isEmpty(code)) {
            binding.tvErrorOtp.setVisibility(View.VISIBLE);
            return;
        }

        setLoading(true);
        otpRepository.getPhoneVerificationDoc(userId)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || otpRepository.isOtpExpired(doc)
                            || !otpRepository.matchesOtp(doc, code)) {
                        setLoading(false);
                        binding.tvErrorOtp.setVisibility(View.VISIBLE);
                        return;
                    }
                    
                    binding.tvErrorOtp.setVisibility(View.GONE);
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
