package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.healthup.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class AdminResetPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_ADMIN_UID = "extra_admin_uid";

    private AdminResetPasswordViewModel viewModel;
    private FrameLayout passwordInputContainer;
    private FrameLayout confirmPasswordInputContainer;
    private LinearLayout passwordErrorLayout;
    private LinearLayout confirmPasswordErrorLayout;
    private TextView passwordErrorText;
    private TextView confirmPasswordErrorText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private MaterialButton resetPasswordButton;
    private FrameLayout loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        String adminUid = getIntent().getStringExtra(EXTRA_ADMIN_UID);
        if (adminUid == null || adminUid.isEmpty()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(AdminResetPasswordViewModel.class);
        viewModel.setAdminUid(adminUid);

        passwordInputContainer = findViewById(R.id.passwordInputContainer);
        confirmPasswordInputContainer = findViewById(R.id.confirmPasswordInputContainer);
        passwordErrorLayout = findViewById(R.id.passwordErrorLayout);
        confirmPasswordErrorLayout = findViewById(R.id.confirmPasswordErrorLayout);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        confirmPasswordErrorText = findViewById(R.id.confirmPasswordErrorText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        resetPasswordButton.setOnClickListener(v -> attemptReset());

        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                clearFieldErrors();
                updateResetButtonState();
            }
        };
        passwordEditText.addTextChangedListener(watcher);
        confirmPasswordEditText.addTextChangedListener(watcher);

        observeViewModel();
        updateResetButtonState();
    }

    private void attemptReset() {
        clearFieldErrors();
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString() : "";
        String confirm = confirmPasswordEditText.getText() != null ? confirmPasswordEditText.getText().toString() : "";
        viewModel.resetPassword(password, confirm);
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, state -> {
            if (state == null) return;
            switch (state) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    Toast.makeText(this, R.string.admin_reset_password_success, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, AdminLoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    viewModel.resetState();
                    break;
                case ERROR:
                    setLoading(false);
                    viewModel.resetState();
                    break;
                case IDLE:
                default:
                    setLoading(false);
                    break;
            }
        });

        viewModel.getPasswordError().observe(this, errorCode -> {
            if (errorCode == null) return;
            showPasswordError(resolvePasswordError(errorCode));
        });

        viewModel.getConfirmPasswordError().observe(this, errorCode -> {
            if (errorCode == null) return;
            showConfirmPasswordError(resolveConfirmPasswordError(errorCode));
        });

        viewModel.getGeneralError().observe(this, error -> {
            if (error == null) return;
            if ("session_invalid".equals(error)) {
                showPasswordError(getString(R.string.reset_password_session_invalid));
            } else if ("user_not_found".equals(error)) {
                showPasswordError(getString(R.string.admin_forgot_email_not_registered));
            } else if ("auth_sync_failed".equals(error)) {
                showPasswordError(getString(R.string.admin_reset_auth_sync_failed));
            } else {
                showPasswordError(getString(R.string.reset_password_error_generic));
            }
        });
    }

    private String resolvePasswordError(String errorCode) {
        switch (errorCode) {
            case "too_short":
                return getString(R.string.register_password_short);
            case "missing_lowercase":
                return getString(R.string.register_password_lowercase);
            case "missing_uppercase":
                return getString(R.string.register_password_uppercase);
            case "missing_number":
                return getString(R.string.register_password_number);
            default:
                return getString(R.string.register_password_required);
        }
    }

    private String resolveConfirmPasswordError(String errorCode) {
        if ("mismatch".equals(errorCode)) {
            return getString(R.string.register_confirm_password_mismatch);
        }
        return getString(R.string.register_confirm_password_required);
    }

    private void showPasswordError(String message) {
        passwordErrorText.setText(message);
        passwordErrorLayout.setVisibility(View.VISIBLE);
        passwordInputContainer.setBackgroundResource(R.drawable.bg_input_error);
    }

    private void showConfirmPasswordError(String message) {
        confirmPasswordErrorText.setText(message);
        confirmPasswordErrorLayout.setVisibility(View.VISIBLE);
        confirmPasswordInputContainer.setBackgroundResource(R.drawable.bg_input_error);
    }

    private void clearFieldErrors() {
        passwordErrorLayout.setVisibility(View.GONE);
        confirmPasswordErrorLayout.setVisibility(View.GONE);
        passwordInputContainer.setBackgroundResource(R.drawable.bg_input_default);
        confirmPasswordInputContainer.setBackgroundResource(R.drawable.bg_input_default);
    }

    private void updateResetButtonState() {
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString() : "";
        String confirm = confirmPasswordEditText.getText() != null ? confirmPasswordEditText.getText().toString() : "";
        boolean enabled = viewModel.isFormValid(password, confirm) && !isLoading();
        resetPasswordButton.setEnabled(enabled);
        resetPasswordButton.setBackgroundResource(enabled
                ? R.drawable.bg_login_button_enabled
                : R.drawable.bg_button_disabled);
        resetPasswordButton.setTextColor(ContextCompat.getColor(this, enabled
                ? R.color.button_text_on_primary
                : R.color.button_text_disabled));
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        passwordEditText.setEnabled(!loading);
        confirmPasswordEditText.setEnabled(!loading);
        updateResetButtonState();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
