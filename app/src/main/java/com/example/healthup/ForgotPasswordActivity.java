package com.example.healthup;

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

import com.example.healthup.forgotpassword.ForgotPasswordOtpActivity;
import com.example.healthup.forgotpassword.ForgotPasswordViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final int BORDER_ANIMATION_MS = 200;

    private enum InputState {
        DEFAULT,
        FOCUSED,
        ERROR
    }

    private TextView phoneLabel;
    private FrameLayout phoneInputContainer;
    private LinearLayout phoneErrorLayout;
    private TextView phoneErrorText;
    private EditText phoneEditText;
    private MaterialButton sendOtpButton;
    private FrameLayout loadingOverlay;

    private ForgotPasswordViewModel viewModel;
    private boolean phoneTouched;
    private boolean phoneHasError;
    private int phoneBorderRes = R.drawable.bg_input_default;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);

        bindViews();
        applyPrefillPhone();
        setupInputBehavior();
        setupActions();
        observeViewModel();
        updateSendButtonState();
    }

    private void applyPrefillPhone() {
        String prefill = getIntent().getStringExtra(
                com.example.healthup.util.CheckoutIntentHelper.EXTRA_PREFILL_PHONE);
        if (prefill != null && !prefill.trim().isEmpty()) {
            phoneEditText.setText(com.example.healthup.util.PhoneNormalizer.normalize(prefill));
        }
    }

    private void bindViews() {
        phoneLabel = findViewById(R.id.phoneLabel);
        phoneInputContainer = findViewById(R.id.phoneInputContainer);
        phoneErrorLayout = findViewById(R.id.phoneErrorLayout);
        phoneErrorText = findViewById(R.id.phoneErrorText);
        phoneEditText = findViewById(R.id.phoneEditText);
        sendOtpButton = findViewById(R.id.sendOtpButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupActions() {
        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        sendOtpButton.setOnClickListener(v -> {
            phoneTouched = true;
            viewModel.sendOtp(getPhoneValue());
        });
        findViewById(R.id.supportLinkTextView).setOnClickListener(v ->
                Toast.makeText(this, R.string.forgot_password_support_link, Toast.LENGTH_SHORT).show());
    }

    private void setupInputBehavior() {
        phoneEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                phoneHasError = false;
                phoneErrorLayout.setVisibility(View.GONE);
                applyInputState(hasFocus ? InputState.FOCUSED : InputState.DEFAULT);
                return;
            }
            phoneTouched = true;
            validatePhoneField(true);
        });

        phoneEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (phoneHasError) {
                    clearFieldError(phoneEditText.hasFocus());
                    phoneHasError = false;
                }
                updateSendButtonState();
            }
        });
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
                case SUCCESS:
                case ERROR:
                case IDLE:
                default:
                    setLoading(false);
                    break;
            }
        });

        viewModel.getPhoneError().observe(this, errorCode -> {
            if (errorCode == null) {
                return;
            }
            if ("not_registered".equals(errorCode)) {
                showFieldError(getString(R.string.forgot_password_phone_not_registered));
            } else if ("google_linked".equals(errorCode)) {
                showFieldError(getString(R.string.forgot_password_use_google));
            } else {
                showFieldError(resolvePhoneError(errorCode));
            }
        });

        viewModel.getGeneralError().observe(this, event -> {
            if (event == null) {
                return;
            }
            if (event.getContentIfNotHandled() != null) {
                showSnackbar(getString(R.string.forgot_password_error_generic));
            }
        });

        viewModel.getNavigateToOtpEvent().observe(this, event -> {
            if (event == null) {
                return;
            }
            String phone = event.getContentIfNotHandled();
            if (phone == null || phone.isEmpty()) {
                return;
            }
            Intent intent = new Intent(this, ForgotPasswordOtpActivity.class);
            intent.putExtra(ForgotPasswordOtpActivity.EXTRA_PHONE, phone);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            viewModel.resetState();
        });
    }

    private void validatePhoneField(boolean showError) {
        String errorCode = RegisterValidator.validatePhone(getPhoneValue());
        if (errorCode == null) {
            if (showError || phoneTouched) {
                clearFieldError(phoneEditText.hasFocus());
                phoneHasError = false;
            }
            return;
        }
        if (!showError && !phoneTouched) {
            return;
        }
        showFieldError(resolvePhoneError(errorCode));
    }

    private String resolvePhoneError(String errorCode) {
        if ("length".equals(errorCode)) {
            return getString(R.string.register_phone_length_error);
        }
        if ("invalid".equals(errorCode)) {
            return getString(R.string.register_phone_invalid_error);
        }
        return getString(R.string.register_phone_required);
    }

    private void showFieldError(String message) {
        phoneHasError = true;
        phoneErrorText.setText(message);
        phoneErrorLayout.setVisibility(View.VISIBLE);
        applyInputState(InputState.ERROR);
        shakeView(phoneInputContainer);
        fadeInView(phoneErrorLayout);
    }

    private void clearFieldError(boolean isFocused) {
        phoneErrorLayout.setVisibility(View.GONE);
        applyInputState(isFocused ? InputState.FOCUSED : InputState.DEFAULT);
    }

    private void applyInputState(InputState state) {
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
        if (phoneBorderRes != borderRes) {
            animateBorder(phoneInputContainer, phoneBorderRes, borderRes);
            phoneBorderRes = borderRes;
        }
        phoneLabel.setTextColor(ContextCompat.getColor(this, labelColor));
    }

    private void updateSendButtonState() {
        boolean enabled = RegisterValidator.validatePhone(getPhoneValue()) == null && !isLoading();
        sendOtpButton.setEnabled(enabled);
        if (enabled) {
            sendOtpButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
            sendOtpButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        } else {
            sendOtpButton.setBackgroundResource(R.drawable.bg_button_disabled);
            sendOtpButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_disabled));
        }
    }

    private String getPhoneValue() {
        return phoneEditText.getText() == null ? "" : phoneEditText.getText().toString().trim();
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        phoneEditText.setEnabled(!loading);
        updateSendButtonState();
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

    private void fadeInView(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(200).start();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.forgotPasswordScrollView), message, Snackbar.LENGTH_LONG).show();
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
