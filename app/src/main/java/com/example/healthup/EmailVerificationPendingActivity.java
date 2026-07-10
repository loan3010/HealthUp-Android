package com.example.healthup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.auth.EmailVerificationHelper;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * After registration when user provided a real email.
 * Uses Firebase Auth {@code sendEmailVerification} (worked for registration before).
 */
public class EmailVerificationPendingActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_verify_email";
    public static final String EXTRA_MAIL_ALREADY_SENT = "extra_mail_already_sent";

    private String email;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FrameLayout loadingOverlay;
    private MaterialButton resendButton;
    private MaterialButton verifiedButton;
    private MaterialButton openMailButton;
    private TextView subtitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification_pending);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

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
        subtitle.setText(getString(R.string.email_verify_subtitle, email == null ? "" : email));

        loadingOverlay = findViewById(R.id.loadingOverlay);
        openMailButton = findViewById(R.id.openMailButton);
        resendButton = findViewById(R.id.resendButton);
        verifiedButton = findViewById(R.id.verifiedButton);
        TextView skipText = findViewById(R.id.skipText);

        openMailButton.setOnClickListener(v -> openMailApp());
        resendButton.setOnClickListener(v -> sendVerification(true));
        verifiedButton.setOnClickListener(v -> checkVerified());
        skipText.setOnClickListener(v -> continueToApp(false));

        boolean alreadySent = getIntent().getBooleanExtra(EXTRA_MAIL_ALREADY_SENT, false);
        if (!alreadySent) {
            sendVerification(false);
        }
    }

    private void sendVerification(boolean fromResend) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        setLoading(true);
        EmailVerificationHelper.sendToCurrentEmail(user, new EmailVerificationHelper.Callback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                if (fromResend) {
                    Toast.makeText(
                            EmailVerificationPendingActivity.this,
                            R.string.email_verify_resend_success,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                Toast.makeText(EmailVerificationPendingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkVerified() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        setLoading(true);
        user.reload().addOnCompleteListener(task -> {
            FirebaseUser refreshed = firebaseAuth.getCurrentUser();
            if (refreshed == null) {
                setLoading(false);
                goToLogin();
                return;
            }
            if (!refreshed.isEmailVerified()) {
                setLoading(false);
                Toast.makeText(this, R.string.change_email_not_verified_yet, Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("emailVerified", true);
            String verifiedEmail = refreshed.getEmail();
            if (!TextUtils.isEmpty(verifiedEmail)) {
                String normalized = RegisterValidator.normalizeEmail(verifiedEmail);
                updates.put("email", normalized);
                updates.put("displayEmail", normalized);
            }
            firestore.collection("users").document(refreshed.getUid())
                    .update(updates)
                    .addOnCompleteListener(updateTask -> {
                        setLoading(false);
                        Toast.makeText(this, R.string.email_verify_success, Toast.LENGTH_SHORT).show();
                        continueToApp(true);
                    });
        });
    }

    private void continueToApp(boolean ignored) {
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

    private void openMailApp() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.email_verify_open_mail_failed, Toast.LENGTH_SHORT).show();
            }
        }
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
        continueToApp(false);
    }
}
