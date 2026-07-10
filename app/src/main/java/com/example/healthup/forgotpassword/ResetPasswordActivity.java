package com.example.healthup.forgotpassword;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.example.healthup.RegisterValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

public class ResetPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";

    private static final int BORDER_ANIMATION_MS = 200;

    private enum InputState {
        DEFAULT,
        FOCUSED,
        ERROR
    }

    private TextView passwordLabel;
    private TextView confirmPasswordLabel;
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

    private ResetPasswordViewModel viewModel;
    private int passwordBorderRes = R.drawable.bg_input_default;
    private int confirmPasswordBorderRes = R.drawable.bg_input_default;

    private boolean passwordTouched;
    private boolean confirmPasswordTouched;
    private boolean passwordHasError;
    private boolean confirmPasswordHasError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        String phone = getIntent().getStringExtra(EXTRA_PHONE);
        if (phone == null || phone.isEmpty()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ResetPasswordViewModel.class);
        viewModel.setPhone(phone);

        bindViews();
        setupInputBehavior();
        setupActions();
        observeViewModel();
        updateResetButtonState();
    }

    private void bindViews() {
        passwordLabel = findViewById(R.id.passwordLabel);
        confirmPasswordLabel = findViewById(R.id.confirmPasswordLabel);
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
    }

    private void setupActions() {
        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        resetPasswordButton.setOnClickListener(v -> attemptReset());
    }

    private void setupInputBehavior() {
        setupField(passwordEditText, passwordErrorLayout, true);
        setupField(confirmPasswordEditText, confirmPasswordErrorLayout, false);

        passwordEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (passwordHasError) {
                    clearFieldError(true, passwordEditText.hasFocus());
                    passwordHasError = false;
                }
                updateResetButtonState();
            }
        });

        confirmPasswordEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (confirmPasswordHasError) {
                    clearFieldError(false, confirmPasswordEditText.hasFocus());
                    confirmPasswordHasError = false;
                }
                updateResetButtonState();
            }
        });
    }

    private void setupField(EditText editText, LinearLayout errorLayout, boolean isPassword) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (isPassword) {
                    passwordHasError = false;
                } else {
                    confirmPasswordHasError = false;
                }
                errorLayout.setVisibility(View.GONE);
                applyInputState(isPassword, InputState.FOCUSED);
                return;
            }

            if (isPassword) {
                passwordTouched = true;
                validatePasswordField(true);
            } else {
                confirmPasswordTouched = true;
                validateConfirmPasswordField(true);
            }
        });
    }

    private void attemptReset() {
        passwordTouched = true;
        confirmPasswordTouched = true;

        boolean passwordValid = validatePasswordField(true);
        boolean confirmValid = validateConfirmPasswordField(true);
        if (!passwordValid || !confirmValid) {
            return;
        }

        viewModel.resetPassword(getPasswordValue(), getConfirmPasswordValue(), this);
    }

    private boolean validatePasswordField(boolean showError) {
        String errorCode = RegisterValidator.validatePassword(getPasswordValue());
        if (errorCode == null) {
            if (showError || passwordTouched) {
                clearFieldError(true, passwordEditText.hasFocus());
                passwordHasError = false;
            }
            return true;
        }
        if (!showError && !passwordTouched) {
            return false;
        }
        showFieldError(true, resolvePasswordError(errorCode));
        return false;
    }

    private boolean validateConfirmPasswordField(boolean showError) {
        String errorCode = RegisterValidator.validateConfirmPassword(
                getPasswordValue(),
                getConfirmPasswordValue()
        );
        if (errorCode == null) {
            if (showError || confirmPasswordTouched) {
                clearFieldError(false, confirmPasswordEditText.hasFocus());
                confirmPasswordHasError = false;
            }
            return true;
        }
        if (!showError && !confirmPasswordTouched) {
            return false;
        }
        showFieldError(false, resolveConfirmPasswordError(errorCode));
        return false;
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, state -> {
            if (state == null) {
                return;
            }
            switch (state) {
                case LOADING:
                    setLoading(true);
                    break;
                case WAITING_SMS:
                    setLoading(false);
                    break;
                case SUCCESS:
                    setLoading(false);
                    Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, LoginActivity.class);
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
            if (errorCode != null) {
                showFieldError(true, resolvePasswordError(errorCode));
                viewModel.clearPasswordError();
            }
        });

        viewModel.getConfirmPasswordError().observe(this, errorCode -> {
            if (errorCode != null) {
                showFieldError(false, resolveConfirmPasswordError(errorCode));
                viewModel.clearConfirmPasswordError();
            }
        });

        viewModel.getSmsCodeRequiredEvent().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled() != null) {
                showSmsCodeDialog();
            }
        });

        viewModel.getGeneralError().observe(this, error -> {
            if (error == null) {
                return;
            }
            showSnackbar(resolveGeneralError(error));
            viewModel.clearGeneralError();
        });
    }

    private String resolveGeneralError(String errorCode) {
        switch (errorCode) {
            case "session_invalid":
                return getString(R.string.reset_password_session_invalid);
            case "user_not_found":
                return getString(R.string.forgot_password_phone_not_registered);
            case "provider_disabled":
                return getString(R.string.reset_password_provider_disabled);
            case "phone_not_linked":
                return getString(R.string.reset_password_phone_not_linked);
            case "invalid_sms_code":
                return getString(R.string.reset_password_invalid_sms_code);
            case "recent_auth_required":
                return getString(R.string.reset_password_recent_auth_required);
            case "network":
                return getString(R.string.reset_password_error_network);
            case "weak_password":
                return getString(R.string.register_password_short);
            case "invalid_request":
            case "generic":
                return getString(R.string.reset_password_error_generic);
            default:
                return getString(R.string.reset_password_error_generic);
        }
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

    private void showFieldError(boolean isPassword, String message) {
        if (isPassword) {
            passwordHasError = true;
            passwordErrorText.setText(message);
            passwordErrorLayout.setVisibility(View.VISIBLE);
            applyInputState(true, InputState.ERROR);
            shakeView(passwordInputContainer);
        } else {
            confirmPasswordHasError = true;
            confirmPasswordErrorText.setText(message);
            confirmPasswordErrorLayout.setVisibility(View.VISIBLE);
            applyInputState(false, InputState.ERROR);
            shakeView(confirmPasswordInputContainer);
        }
    }

    private void clearFieldError(boolean isPassword, boolean isFocused) {
        if (isPassword) {
            passwordErrorLayout.setVisibility(View.GONE);
            applyInputState(true, isFocused ? InputState.FOCUSED : InputState.DEFAULT);
        } else {
            confirmPasswordErrorLayout.setVisibility(View.GONE);
            applyInputState(false, isFocused ? InputState.FOCUSED : InputState.DEFAULT);
        }
    }

    private void applyInputState(boolean isPassword, InputState state) {
        int borderRes;
        int labelColor;
        switch (state) {
            case FOCUSED:
                borderRes = R.drawable.bg_input_focused;
                labelColor = R.color.brand_primary;
                break;
            case ERROR:
                borderRes = R.drawable.bg_input_error;
                labelColor = R.color.error;
                break;
            case DEFAULT:
            default:
                borderRes = R.drawable.bg_input_default;
                labelColor = R.color.text_primary;
                break;
        }

        if (isPassword) {
            if (passwordBorderRes != borderRes) {
                animateBorder(passwordInputContainer, passwordBorderRes, borderRes);
                passwordBorderRes = borderRes;
            }
            passwordLabel.setTextColor(ContextCompat.getColor(this, labelColor));
        } else {
            if (confirmPasswordBorderRes != borderRes) {
                animateBorder(confirmPasswordInputContainer, confirmPasswordBorderRes, borderRes);
                confirmPasswordBorderRes = borderRes;
            }
            confirmPasswordLabel.setTextColor(ContextCompat.getColor(this, labelColor));
        }
    }

    private void updateResetButtonState() {
        boolean enabled = viewModel.isFormValid(getPasswordValue(), getConfirmPasswordValue()) && !isLoading();
        resetPasswordButton.setEnabled(enabled);
        if (enabled) {
            resetPasswordButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
            resetPasswordButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        } else {
            resetPasswordButton.setBackgroundResource(R.drawable.bg_button_disabled);
            resetPasswordButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_disabled));
        }
    }

    private String getPasswordValue() {
        return passwordEditText.getText() == null ? "" : passwordEditText.getText().toString();
    }

    private String getConfirmPasswordValue() {
        return confirmPasswordEditText.getText() == null ? "" : confirmPasswordEditText.getText().toString();
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

    private void animateBorder(View container, int fromRes, int toRes) {
        Drawable from = ContextCompat.getDrawable(this, fromRes);
        Drawable to = ContextCompat.getDrawable(this, toRes);
        if (from == null || to == null) {
            container.setBackgroundResource(toRes);
            return;
        }
        TransitionDrawable transition = new TransitionDrawable(new Drawable[]{from, to});
        container.setBackground(transition);
        transition.startTransition(BORDER_ANIMATION_MS);
    }

    private void shakeView(View view) {
        TranslateAnimation shake = new TranslateAnimation(0, 8, 0, 0);
        shake.setDuration(300);
        shake.setInterpolator(new CycleInterpolator(3));
        view.startAnimation(shake);
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.resetPasswordScrollView), message, Snackbar.LENGTH_LONG).show();
    }

    private void showSmsCodeDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.reset_password_sms_hint);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_password_sms_title)
                .setMessage(R.string.reset_password_sms_message)
                .setView(input)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> viewModel.resetState())
                .setPositiveButton(R.string.reset_password_sms_confirm, (dialog, which) -> {
                    String code = input.getText() == null ? "" : input.getText().toString();
                    viewModel.submitSmsCode(code);
                })
                .show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
