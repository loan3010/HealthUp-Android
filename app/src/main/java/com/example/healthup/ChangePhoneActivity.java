package com.example.healthup;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.databinding.ActivityChangePhoneBinding;
import com.example.healthup.data.repository.OtpRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangePhoneActivity extends AppCompatActivity {
    private ActivityChangePhoneBinding binding;
    private FirebaseFirestore db;
    private OtpRepository otpRepository;
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
