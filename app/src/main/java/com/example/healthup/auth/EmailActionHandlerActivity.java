package com.example.healthup.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.ChangeEmailActivity;
import com.example.healthup.LoginActivity;
import com.example.healthup.MainActivity;
import com.example.healthup.R;
import com.example.healthup.data.repository.PasswordResetRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Receives Firebase email-action deep links (verifyAndChangeEmail / verifyEmail)
 * and applies the oobCode in-process so the Auth session can stay alive.
 */
public class EmailActionHandlerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null || intent.getData() == null) {
            finishQuiet();
            return;
        }
        Uri data = intent.getData();
        String oobCode = data.getQueryParameter("oobCode");
        String mode = data.getQueryParameter("mode");
        if (TextUtils.isEmpty(oobCode)) {
            // Some Hosting / App Link wrappers nest the real link.
            String link = data.getQueryParameter("link");
            if (!TextUtils.isEmpty(link)) {
                Uri nested = Uri.parse(link);
                oobCode = nested.getQueryParameter("oobCode");
                mode = nested.getQueryParameter("mode");
            }
        }
        if (TextUtils.isEmpty(oobCode)) {
            Toast.makeText(this, R.string.email_action_invalid_link, Toast.LENGTH_LONG).show();
            openChangeEmailOrMain();
            return;
        }

        final String code = oobCode;
        final String actionMode = mode == null ? "" : mode.toLowerCase();

        if (actionMode.contains("reset") || "recoverEmail".equals(actionMode)) {
            handlePasswordResetCode(code);
            return;
        }

        EmailVerificationHelper.applyOobCode(code, new EmailVerificationHelper.ApplyCallback() {
            @Override
            public void onApplied(@NonNull String emailFromCode) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String pending = resolveExpectedEmail(emailFromCode);

                if (user != null) {
                    user.reload().addOnCompleteListener(task -> {
                        FirebaseUser refreshed = FirebaseAuth.getInstance().getCurrentUser();
                        if (refreshed == null) {
                            rememberAndAskPassword(pending);
                            return;
                        }
                        String toSync = !TextUtils.isEmpty(pending)
                                ? pending
                                : refreshed.getEmail();
                        if (TextUtils.isEmpty(toSync)) {
                            Toast.makeText(
                                    EmailActionHandlerActivity.this,
                                    R.string.email_action_applied_open_app,
                                    Toast.LENGTH_LONG
                            ).show();
                            openChangeEmailOrMain();
                            return;
                        }
                        EmailProfileSync.syncFromAuthUser(
                                refreshed,
                                toSync,
                                new EmailProfileSync.Callback() {
                                    @Override
                                    public void onSuccess() {
                                        PendingEmailChange.clear(EmailActionHandlerActivity.this);
                                        Toast.makeText(
                                                EmailActionHandlerActivity.this,
                                                R.string.email_verify_success,
                                                Toast.LENGTH_LONG
                                        ).show();
                                        goMain();
                                    }

                                    @Override
                                    public void onError(@NonNull String message) {
                                        if ("not_matched".equals(message)) {
                                            // Browser may have applied; Auth not reloaded yet — open Change Email.
                                            Toast.makeText(
                                                    EmailActionHandlerActivity.this,
                                                    R.string.email_action_applied_tap_verified,
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        } else {
                                            Toast.makeText(
                                                    EmailActionHandlerActivity.this,
                                                    getString(R.string.change_email_sync_failed, message),
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        }
                                        openChangeEmailOrMain();
                                    }
                                }
                        );
                    });
                } else {
                    rememberAndAskPassword(pending);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                // Link already used by browser is common — still guide user to finish in-app.
                Toast.makeText(EmailActionHandlerActivity.this, message, Toast.LENGTH_LONG).show();
                openChangeEmailOrMain();
            }
        });
    }

    private void handlePasswordResetCode(@NonNull String oobCode) {
        PendingPasswordReset.Data pending = PendingPasswordReset.load(this);
        if (pending == null) {
            // User may finish on Firebase web page with their own password — just guide to login.
            Toast.makeText(this, R.string.reset_password_link_open_login, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        new PasswordResetRepository().confirmPendingResetWithOobCode(
                this,
                oobCode,
                (result, errorMessage) -> {
                    if (result == PasswordResetRepository.ResetPasswordResult.SUCCESS) {
                        Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.reset_password_link_open_login, Toast.LENGTH_LONG).show();
                    }
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
        );
    }

    @Nullable
    private String resolveExpectedEmail(@Nullable String fromCode) {
        if (!TextUtils.isEmpty(fromCode) && UserProfileBuilder.isRealEmail(fromCode)) {
            PendingEmailChange.Data existing = PendingEmailChange.load(this);
            if (existing != null) {
                // Keep uid binding; refresh email from code if present.
                PendingEmailChange.save(this, existing.uid, fromCode);
            }
            return fromCode.trim().toLowerCase();
        }
        PendingEmailChange.Data data = PendingEmailChange.load(this);
        return data != null ? data.email : null;
    }

    private void rememberAndAskPassword(@Nullable String email) {
        PendingEmailChange.Data data = PendingEmailChange.load(this);
        if (data == null && !TextUtils.isEmpty(email)) {
            // Without uid we still open Change Email; user must sign in via password dialog.
            Toast.makeText(this, R.string.change_email_need_password_to_finish, Toast.LENGTH_LONG).show();
        } else if (data != null && !TextUtils.isEmpty(email)) {
            PendingEmailChange.save(this, data.uid, email);
            Toast.makeText(this, R.string.change_email_need_password_to_finish, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.email_action_applied_tap_verified, Toast.LENGTH_LONG).show();
        }
        openChangeEmailOrMain();
    }

    private void openChangeEmailOrMain() {
        Intent intent = new Intent(this, ChangeEmailActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(ChangeEmailActivity.EXTRA_COMPLETE_PENDING, true);
        startActivity(intent);
        finish();
    }

    private void goMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void finishQuiet() {
        finish();
    }
}
