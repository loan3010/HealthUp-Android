package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.healthup.auth.EmailProfileSync;
import com.example.healthup.auth.EmailVerificationHelper;
import com.example.healthup.auth.PendingEmailChange;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.databinding.ActivityChangeEmailBinding;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.PhoneNormalizer;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/**
 * Add / change email via Firebase {@code verifyBeforeUpdateEmail}.
 * <p>
 * Completion paths (no forced logout to Login):
 * <ol>
 *   <li>Deep link applies oobCode while session alive → sync Firestore</li>
 *   <li>"Tôi đã xác thực" while still signed in → reload + sync</li>
 *   <li>Session invalidated after browser verify → password dialog here
 *       (with Quên mật khẩu), then sync — stay on this screen</li>
 * </ol>
 */
public class ChangeEmailActivity extends AppCompatActivity {

    public static final String EXTRA_COMPLETE_PENDING = "extra_complete_pending";

    private ActivityChangeEmailBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore db;
    private String userId;
    private String pendingEmail;
    /** True when opened only to finish a pending change (may have no Auth session). */
    private boolean completeOnlyMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.tvOtpLabel.setVisibility(View.GONE);
        binding.etOtp.setVisibility(View.GONE);
        binding.tvErrorOtp.setVisibility(View.GONE);
        binding.tvDebugOtp.setVisibility(View.GONE);
        binding.btnSave.setVisibility(View.GONE);

        binding.btnSendVerify.setText(R.string.change_email_send_verify);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSendVerify.setOnClickListener(v -> checkUniqueThenSend());
        binding.btnSave.setText(R.string.change_email_check_verified);
        binding.btnSave.setOnClickListener(v -> checkVerified());

        FirebaseUser user = firebaseAuth.getCurrentUser();
        PendingEmailChange.Data pending = PendingEmailChange.load(this);

        if (user != null) {
            userId = user.getUid();
            restorePendingEmail();
            prefillCurrentEmail();
            if (getIntent().getBooleanExtra(EXTRA_COMPLETE_PENDING, false) && pendingEmail != null) {
                // Returned from deep link — try sync, else password dialog.
                checkVerified();
            }
            return;
        }

        // No Auth session: still allow finishing if we have a pending email change.
        if (pending != null) {
            completeOnlyMode = true;
            userId = pending.uid;
            pendingEmail = pending.email;
            binding.etNewEmail.setText(pendingEmail);
            binding.etNewEmail.setEnabled(false);
            binding.btnSendVerify.setVisibility(View.GONE);
            showSentUi(pendingEmail);
            binding.btnSave.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.change_email_need_password_to_finish, Toast.LENGTH_LONG).show();
            promptPasswordToFinish(false);
            return;
        }

        Toast.makeText(this, R.string.change_email_session_expired, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_COMPLETE_PENDING, false)) {
            restorePendingEmail();
            checkVerified();
        }
    }

    private void restorePendingEmail() {
        if (userId == null) {
            return;
        }
        String email = PendingEmailChange.emailForUid(this, userId);
        if (!TextUtils.isEmpty(email)) {
            pendingEmail = email;
            binding.etNewEmail.setText(email);
            showSentUi(email);
        }
    }

    private void savePendingEmail(@NonNull String email) {
        pendingEmail = email;
        if (userId != null) {
            PendingEmailChange.save(this, userId, email);
        }
    }

    private void clearPendingEmail() {
        PendingEmailChange.clear(this);
    }

    private void prefillCurrentEmail() {
        if (userId == null || !TextUtils.isEmpty(pendingEmail)) {
            return;
        }
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !TextUtils.isEmpty(pendingEmail)) {
                        return;
                    }
                    String display = doc.getString("displayEmail");
                    String email = doc.getString("email");
                    String show = !TextUtils.isEmpty(display) ? display : email;
                    if (UserProfileBuilder.isRealEmail(show)) {
                        binding.etNewEmail.setText(show);
                    }
                });
    }

    private void checkUniqueThenSend() {
        if (completeOnlyMode) {
            promptPasswordToFinish(true);
            return;
        }
        String rawEmail = binding.etNewEmail.getText().toString().trim();
        String email = RegisterValidator.normalizeEmail(rawEmail);
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tvErrorEmail.setText(R.string.register_email_invalid_error);
            binding.tvErrorEmail.setVisibility(View.VISIBLE);
            return;
        }
        if (UserProfileBuilder.isSyntheticAuthEmail(email)) {
            binding.tvErrorEmail.setText(R.string.register_email_invalid_error);
            binding.tvErrorEmail.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvErrorEmail.setVisibility(View.GONE);
        binding.etNewEmail.setText(email);

        setBusy(true);
        db.collection("users")
                .whereEqualTo("displayEmail", email)
                .limit(1)
                .get()
                .addOnSuccessListener(byDisplay -> {
                    if (!byDisplay.isEmpty() && !userId.equals(byDisplay.getDocuments().get(0).getId())) {
                        setBusy(false);
                        binding.tvErrorEmail.setText(R.string.register_email_exists);
                        binding.tvErrorEmail.setVisibility(View.VISIBLE);
                        return;
                    }
                    db.collection("users")
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(byEmail -> {
                                if (!byEmail.isEmpty() && !userId.equals(byEmail.getDocuments().get(0).getId())) {
                                    setBusy(false);
                                    binding.tvErrorEmail.setText(R.string.register_email_exists);
                                    binding.tvErrorEmail.setVisibility(View.VISIBLE);
                                    return;
                                }
                                sendFirebaseVerifyMail(email, null);
                            })
                            .addOnFailureListener(e -> {
                                setBusy(false);
                                Toast.makeText(this, R.string.register_error_generic, Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.register_error_generic, Toast.LENGTH_SHORT).show();
                });
    }

    private void sendFirebaseVerifyMail(@NonNull String email, @Nullable String passwordForReauth) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            setBusy(false);
            promptPasswordToFinish(true);
            return;
        }

        Runnable doSend = () -> {
            FirebaseUser current = firebaseAuth.getCurrentUser();
            if (current == null) {
                setBusy(false);
                promptPasswordToFinish(true);
                return;
            }
            EmailVerificationHelper.sendVerifyBeforeUpdateEmail(
                    current,
                    email,
                    new EmailVerificationHelper.Callback() {
                        @Override
                        public void onSuccess() {
                            setBusy(false);
                            savePendingEmail(email);
                            showSentUi(email);
                            Toast.makeText(
                                    ChangeEmailActivity.this,
                                    R.string.change_email_verify_sent,
                                    Toast.LENGTH_LONG
                            ).show();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            setBusy(false);
                            if (message.toLowerCase(Locale.ROOT).contains("mật khẩu")) {
                                promptPasswordThenRetrySend(email);
                                return;
                            }
                            Toast.makeText(ChangeEmailActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        };

        if (TextUtils.isEmpty(passwordForReauth)) {
            doSend.run();
            return;
        }

        String authEmail = user.getEmail();
        if (TextUtils.isEmpty(authEmail)) {
            setBusy(false);
            Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_LONG).show();
            return;
        }
        user.reauthenticate(EmailAuthProvider.getCredential(authEmail, passwordForReauth))
                .addOnSuccessListener(unused -> doSend.run())
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.login_credentials_wrong, Toast.LENGTH_LONG).show();
                });
    }

    private void promptPasswordThenRetrySend(@NonNull String email) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.login_password_required);
        FrameLayout wrap = paddedWrap(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_email_confirm_password_title)
                .setMessage(R.string.change_email_confirm_password_message)
                .setView(wrap)
                .setPositiveButton(R.string.change_email_send_verify, (d, w) -> {
                    String pwd = textOf(input);
                    if (TextUtils.isEmpty(pwd)) {
                        Toast.makeText(this, R.string.change_email_send_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setBusy(true);
                    sendFirebaseVerifyMail(email, pwd);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSentUi(@NonNull String email) {
        binding.tvDebugOtp.setVisibility(View.VISIBLE);
        binding.tvDebugOtp.setText(getString(R.string.change_email_sent_hint, email));
        binding.btnSendVerify.setText(R.string.email_verify_resend);
        binding.btnSave.setVisibility(View.VISIBLE);
    }

    private void checkVerified() {
        if (TextUtils.isEmpty(pendingEmail)) {
            pendingEmail = RegisterValidator.normalizeEmail(binding.etNewEmail.getText().toString());
        }
        if (TextUtils.isEmpty(pendingEmail)) {
            Toast.makeText(this, R.string.change_email_send_first, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            promptPasswordToFinish(true);
            return;
        }

        setBusy(true);
        user.reload().addOnCompleteListener(reloadTask -> {
            FirebaseUser refreshed = firebaseAuth.getCurrentUser();
            if (refreshed == null) {
                setBusy(false);
                promptPasswordToFinish(true);
                return;
            }
            // Prefer soft token refresh; if it fails we still try sync / password path.
            refreshed.getIdToken(true)
                    .addOnCompleteListener(tokenTask -> trySyncOrPassword(refreshed));
        });
    }

    private void trySyncOrPassword(@NonNull FirebaseUser refreshed) {
        String authEmail = refreshed.getEmail();
        boolean matched = authEmail != null
                && authEmail.equalsIgnoreCase(pendingEmail)
                && refreshed.isEmailVerified();

        if (!matched) {
            setBusy(false);
            // Auth may already be new email under a dead session illusion — offer password.
            // Or link not clicked yet.
            if (authEmail != null
                    && UserProfileBuilder.isSyntheticAuthEmail(authEmail)) {
                Toast.makeText(this, R.string.change_email_not_verified_yet, Toast.LENGTH_LONG).show();
            } else {
                promptPasswordToFinish(true);
            }
            return;
        }

        EmailProfileSync.syncFromAuthUser(refreshed, pendingEmail, new EmailProfileSync.Callback() {
            @Override
            public void onSuccess() {
                setBusy(false);
                clearPendingEmail();
                maybePromptGoogleLink(pendingEmail);
            }

            @Override
            public void onError(@NonNull String message) {
                setBusy(false);
                if (message.toLowerCase(Locale.ROOT).contains("permission")) {
                    // Token stale after email change — reauth in place.
                    promptPasswordToFinish(true);
                } else if ("not_matched".equals(message)) {
                    Toast.makeText(
                            ChangeEmailActivity.this,
                            R.string.change_email_not_verified_yet,
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    Toast.makeText(
                            ChangeEmailActivity.this,
                            getString(R.string.change_email_sync_failed, message),
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });
    }

    /**
     * Re-sign-in with the verified email + password on this screen (not LoginActivity).
     */
    private void promptPasswordToFinish(boolean fromUserAction) {
        if (TextUtils.isEmpty(pendingEmail)) {
            if (fromUserAction) {
                Toast.makeText(this, R.string.change_email_send_first, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.login_password_required);

        TextView forgot = new TextView(this);
        forgot.setText(R.string.change_email_forgot_password);
        forgot.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        forgot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        forgot.setPadding(0, pad, 0, 0);
        forgot.setOnClickListener(v -> openForgotPassword());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(input);
        column.addView(forgot);
        FrameLayout wrap = paddedWrap(column);

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_email_finish_title)
                .setMessage(getString(R.string.change_email_finish_message, pendingEmail))
                .setView(wrap)
                .setCancelable(true)
                .setPositiveButton(R.string.change_email_finish_confirm, (d, w) -> {
                    String pwd = textOf(input);
                    if (TextUtils.isEmpty(pwd)) {
                        Toast.makeText(this, R.string.login_password_required, Toast.LENGTH_SHORT).show();
                        promptPasswordToFinish(true);
                        return;
                    }
                    signInAndSync(pendingEmail, pwd);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void signInAndSync(@NonNull String email, @NonNull String password) {
        setBusy(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setBusy(false);
                        Toast.makeText(this, R.string.change_email_session_expired, Toast.LENGTH_LONG).show();
                        return;
                    }
                    userId = user.getUid();
                    completeOnlyMode = false;
                    EmailProfileSync.syncFromAuthUser(user, email, new EmailProfileSync.Callback() {
                        @Override
                        public void onSuccess() {
                            setBusy(false);
                            clearPendingEmail();
                            maybePromptGoogleLink(email);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            setBusy(false);
                            if ("not_matched".equals(message)) {
                                // Signed in but Auth not verified yet — rare.
                                Toast.makeText(
                                        ChangeEmailActivity.this,
                                        R.string.change_email_not_verified_yet,
                                        Toast.LENGTH_LONG
                                ).show();
                            } else {
                                Toast.makeText(
                                        ChangeEmailActivity.this,
                                        getString(R.string.change_email_sync_failed, message),
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        Toast.makeText(this, R.string.social_link_password_wrong, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.login_failed_generic, Toast.LENGTH_LONG).show();
                    }
                    promptPasswordToFinish(true);
                });
    }

    private void openForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        if (!TextUtils.isEmpty(userId)) {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        String phone = doc.getString("phone");
                        if (!TextUtils.isEmpty(phone)) {
                            intent.putExtra(
                                    CheckoutIntentHelper.EXTRA_PREFILL_PHONE,
                                    PhoneNormalizer.normalize(phone)
                            );
                        }
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> startActivity(intent));
        } else {
            startActivity(intent);
        }
    }

    private void maybePromptGoogleLink(String email) {
        boolean looksLikeGmail = email != null && email.toLowerCase(Locale.ROOT).endsWith("@gmail.com");
        Runnable done = () -> UIUtils.showSuccessDialog(this, this::finish);
        if (!looksLikeGmail) {
            done.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.change_email_link_google_title)
                .setMessage(R.string.email_verify_google_link_prompt)
                .setPositiveButton(R.string.change_email_link_google_later, (d, w) -> done.run())
                .show();
    }

    private FrameLayout paddedWrap(@NonNull View child) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(child, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        return wrap;
    }

    @NonNull
    private static String textOf(@NonNull EditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }

    private void setBusy(boolean busy) {
        binding.loadingOverlay.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.btnSendVerify.setEnabled(!busy && !completeOnlyMode);
        binding.btnSave.setEnabled(!busy);
        binding.etNewEmail.setEnabled(!busy && !completeOnlyMode);
    }
}
