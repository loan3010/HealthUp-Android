package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * After registration when user provided a real email.
 * Mock email OTP in Firestore (no Firebase Auth mail / no Cloud).
 */
public class EmailVerificationPendingActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_verify_email";
    public static final String EXTRA_MAIL_ALREADY_SENT = "extra_mail_already_sent";

    private String email;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private OtpRepository otpRepository;
    private FrameLayout loadingOverlay;
    private MaterialButton resendButton;
    private MaterialButton verifiedButton;
    private MaterialButton openMailButton;
    private TextView subtitle;
    private TextView debugOtpText;
    private String lastDebugOtp = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification_pending);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        otpRepository = new OtpRepository();

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (TextUtils.isEmpty(email)) {
            email = user.getEmail();
        }

        subtitle = findViewById(R.id.subtitleText);
        subtitle.setText(getString(R.string.email_verify_subtitle_mock, email == null ? "" : email));

        loadingOverlay = findViewById(R.id.loadingOverlay);
        openMailButton = findViewById(R.id.openMailButton);
        resendButton = findViewById(R.id.resendButton);
        verifiedButton = findViewById(R.id.verifiedButton);
        TextView skipText = findViewById(R.id.skipText);

        // Reuse open-mail button as "show OTP" for mock flow.
        openMailButton.setText(R.string.email_verify_show_otp);
        openMailButton.setOnClickListener(v -> {
            if (TextUtils.isEmpty(lastDebugOtp)) {
                sendMockOtp(false);
            } else {
                Toast.makeText(this, getString(R.string.change_email_debug_otp, lastDebugOtp), Toast.LENGTH_LONG).show();
            }
        });
        resendButton.setOnClickListener(v -> sendMockOtp(true));
        verifiedButton.setOnClickListener(v -> promptOtpAndVerify());
        skipText.setOnClickListener(v -> continueToApp());

        sendMockOtp(false);
    }

    private void sendMockOtp(boolean fromResend) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        setLoading(true);
        String otp = otpRepository.generateOtp();
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        otpRepository.saveEmailVerificationOtp(user.getUid(), normalized, otp)
                .addOnSuccessListener(unused -> {
                    lastDebugOtp = otp;
                    setLoading(false);
                    Toast.makeText(
                            this,
                            getString(R.string.change_email_debug_otp, otp),
                            Toast.LENGTH_LONG
                    ).show();
                    if (fromResend) {
                        Toast.makeText(this, R.string.email_verify_resend_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void promptOtpAndVerify() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.change_email_otp_hint);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_email_otp_label)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.change_email_check_verified, (d, w) -> {
                    String code = input.getText() == null ? "" : input.getText().toString().trim();
                    verifyMockOtp(code);
                })
                .show();
    }

    private void verifyMockOtp(@NonNull String code) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        setLoading(true);
        otpRepository.getEmailVerificationDoc(user.getUid())
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || otpRepository.isOtpExpired(doc)
                            || !otpRepository.matchesOtp(doc, code)) {
                        setLoading(false);
                        Toast.makeText(this, R.string.change_email_otp_wrong, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("emailVerified", true);
                    if (!TextUtils.isEmpty(email)) {
                        String normalized = RegisterValidator.normalizeEmail(email);
                        updates.put("displayEmail", normalized);
                    }
                    firestore.collection("users").document(user.getUid())
                            .update(updates)
                            .addOnCompleteListener(updateTask -> {
                                otpRepository.deleteEmailVerificationDoc(user.getUid());
                                setLoading(false);
                                Toast.makeText(this, R.string.email_verify_success, Toast.LENGTH_SHORT).show();
                                continueToApp();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                });
    }

    private void continueToApp() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        GuestCartManager.getInstance(this).mergeToFirestore(user.getUid(), () -> runOnUiThread(() -> {
            Intent intent = CheckoutIntentHelper.buildPostAuthMainIntent(this);
            startActivity(intent);
            finish();
        }));
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        openMailButton.setEnabled(!loading);
        resendButton.setEnabled(!loading);
        verifiedButton.setEnabled(!loading);
    }

    @Override
    public void onBackPressed() {
        continueToApp();
    }
}
