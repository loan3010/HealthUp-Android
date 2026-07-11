package com.example.healthup;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.databinding.ActivityChangeEmailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Add / change email via mock Firestore OTP (no Firebase Auth mail, no Cloud).
 * Auth email stays synthetic; only {@code displayEmail} + {@code emailVerified} update.
 */
public class ChangeEmailActivity extends AppCompatActivity {

    public static final String EXTRA_COMPLETE_PENDING = "extra_complete_pending";

    private ActivityChangeEmailBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore db;
    private OtpRepository otpRepository;
    private String userId;
    private String pendingEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        otpRepository = new OtpRepository();

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.change_email_session_expired, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        userId = user.getUid();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendVerify.setText(R.string.change_email_send_code);
        binding.btnSendVerify.setOnClickListener(v -> checkUniqueThenSend());
        binding.btnSave.setText(R.string.change_email_confirm);
        binding.btnSave.setOnClickListener(v -> confirmOtp());

        prefillCurrentEmail();
    }

    private void prefillCurrentEmail() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        return;
                    }
                    String display = doc.getString("displayEmail");
                    if (UserProfileBuilder.isRealEmail(display)) {
                        binding.etNewEmail.setHint(display);
                    }
                });
    }

    private void checkUniqueThenSend() {
        String email = binding.etNewEmail.getText() == null
                ? ""
                : binding.etNewEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                || UserProfileBuilder.isSyntheticAuthEmail(email)) {
            binding.tvErrorEmail.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvErrorEmail.setVisibility(View.GONE);
        setLoading(true);

        db.collection("users")
                .whereEqualTo("displayEmail", email)
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    if (!q.isEmpty() && !userId.equals(q.getDocuments().get(0).getId())) {
                        setLoading(false);
                        Toast.makeText(this, R.string.register_email_exists, Toast.LENGTH_LONG).show();
                        return;
                    }
                    sendMockOtp(email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void sendMockOtp(String email) {
        String otp = otpRepository.generateOtp();
        otpRepository.saveEmailVerificationOtp(userId, email, otp)
                .addOnSuccessListener(unused -> {
                    pendingEmail = email;
                    setLoading(false);
                    showOtpUi(otp);
                    Toast.makeText(this, getString(R.string.change_email_debug_otp, otp), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void showOtpUi(@Nullable String debugOtp) {
        binding.tvOtpLabel.setVisibility(View.VISIBLE);
        binding.etOtp.setVisibility(View.VISIBLE);
        binding.btnSave.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(debugOtp)) {
            binding.tvDebugOtp.setVisibility(View.VISIBLE);
            binding.tvDebugOtp.setText(getString(R.string.change_email_debug_otp, debugOtp));
        }
    }

    private void confirmOtp() {
        String code = binding.etOtp.getText() == null
                ? ""
                : binding.etOtp.getText().toString().trim();
        if (TextUtils.isEmpty(pendingEmail)) {
            Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        otpRepository.getEmailVerificationDoc(userId)
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || otpRepository.isOtpExpired(doc)
                            || !otpRepository.matchesOtp(doc, code)) {
                        setLoading(false);
                        binding.tvErrorOtp.setVisibility(View.VISIBLE);
                        binding.tvErrorOtp.setText(R.string.change_email_otp_wrong);
                        return;
                    }
                    binding.tvErrorOtp.setVisibility(View.GONE);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("displayEmail", pendingEmail);
                    updates.put("emailVerified", true);
                    db.collection("users").document(userId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                otpRepository.deleteEmailVerificationDoc(userId);
                                setLoading(false);
                                Toast.makeText(this, R.string.change_email_success, Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSendVerify.setEnabled(!loading);
        binding.btnSave.setEnabled(!loading);
        binding.etNewEmail.setEnabled(!loading);
        binding.etOtp.setEnabled(!loading);
    }
}
