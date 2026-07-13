package com.example.healthup.forgotpassword;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.healthup.BuildConfig;
import com.example.healthup.LoginActivity;
import com.example.healthup.PhoneNumberUtils;
import com.example.healthup.R;
import com.example.healthup.data.repository.PasswordResetRepository;
import com.example.healthup.ui.otp.OtpBoxesHelper;
import com.google.android.material.button.MaterialButton;

public class ForgotPasswordOtpActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";

    private static final long RESEND_COUNTDOWN_SECONDS = 60L;

    private ForgotPasswordOtpViewModel viewModel;
    private OtpBoxesHelper otpBoxesHelper;

    private TextView otpSubtitleTextView;
    private LinearLayout otpErrorLayout;
    private TextView otpErrorText;
    private TextView debugOtpTextView;
    private MaterialButton verifyOtpButton;
    private TextView resendOtpTextView;
    private FrameLayout loadingOverlay;

    private CountDownTimer resendTimer;
    private boolean resendCooldownActive;
    private boolean suppressOtpChangeClear;
    private boolean isVerifying;
    private boolean allowFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password_otp);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordOtpViewModel.class);
        viewModel.cancelPendingVerify();

        bindViews();
        initPhone(savedInstanceState);
        setupOtpBoxes();
        setupActions();
        setupBackPressedHandler();
        observeViewModel();

        if (!TextUtils.isEmpty(viewModel.getPhone())) {
            otpSubtitleTextView.setText(
                    getString(R.string.otp_subtitle, PhoneNumberUtils.maskPhone(viewModel.getPhone())));
            startResendCountdown();
            if (BuildConfig.DEBUG) {
                viewModel.loadDebugOtp();
            }
        } else {
            showOtpError(getString(R.string.reset_password_session_invalid));
            verifyOtpButton.setEnabled(false);
        }
    }

    @Override
    public void finish() {
        if (!allowFinish) {
            return;
        }
        super.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String phone = intent.getStringExtra(EXTRA_PHONE);
        if (!TextUtils.isEmpty(phone)) {
            viewModel.setPhone(phone);
            otpSubtitleTextView.setText(
                    getString(R.string.otp_subtitle, PhoneNumberUtils.maskPhone(phone)));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String phone = viewModel.getPhone();
        if (!TextUtils.isEmpty(phone)) {
            outState.putString(EXTRA_PHONE, phone);
        }
        outState.putBoolean("is_verifying", isVerifying);
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }

    private void initPhone(Bundle savedInstanceState) {
        String phone = resolvePhone(savedInstanceState);
        if (!TextUtils.isEmpty(phone)) {
            viewModel.setPhone(phone);
        }
        if (savedInstanceState != null) {
            isVerifying = savedInstanceState.getBoolean("is_verifying", false);
            setLoading(isVerifying);
        }
    }

    private String resolvePhone(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String savedPhone = savedInstanceState.getString(EXTRA_PHONE);
            if (!TextUtils.isEmpty(savedPhone)) {
                return savedPhone;
            }
        }
        String intentPhone = getIntent().getStringExtra(EXTRA_PHONE);
        if (!TextUtils.isEmpty(intentPhone)) {
            return intentPhone;
        }
        return viewModel.getPhone();
    }

    private void bindViews() {
        otpSubtitleTextView = findViewById(R.id.otpSubtitleTextView);
        otpErrorLayout = findViewById(R.id.otpErrorLayout);
        otpErrorText = findViewById(R.id.otpErrorText);
        debugOtpTextView = findViewById(R.id.debugOtpTextView);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resendOtpTextView = findViewById(R.id.resendOtpTextView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupOtpBoxes() {
        otpBoxesHelper = new OtpBoxesHelper(this, findViewById(R.id.otpBoxesInclude));
        otpBoxesHelper.setListener(new OtpBoxesHelper.Listener() {
            @Override
            public void onOtpChanged(String otp, boolean complete) {
                if (!suppressOtpChangeClear) {
                    otpErrorLayout.setVisibility(View.GONE);
                    otpBoxesHelper.clearError();
                }
                updateVerifyButton(complete);
            }

            @Override
            public void onOtpComplete(String otp) {
                updateVerifyButton(true);
            }
        });
        otpBoxesHelper.requestFocusFirst();
        updateVerifyButton(false);
    }

    private void setupActions() {
        findViewById(R.id.backTextView).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        verifyOtpButton.setOnClickListener(v -> submitOtp());
        resendOtpTextView.setOnClickListener(v -> {
            if (resendOtpTextView.isEnabled() && !isVerifying) {
                viewModel.resendOtp();
            }
        });
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isVerifying) {
                    return;
                }
                setEnabled(false);
                navigateToLogin();
            }
        });
    }

    private void submitOtp() {
        if (isVerifying || isFinishing() || TextUtils.isEmpty(viewModel.getPhone())) {
            return;
        }
        String otp = otpBoxesHelper.getOtp();
        if (otp.length() != 6) {
            showOtpError(getString(R.string.otp_invalid_length));
            return;
        }

        isVerifying = true;
        setLoading(true);
        viewModel.verifyOtp(otp, this::handleVerifyResult);
    }

    private void handleVerifyResult(PasswordResetRepository.VerifyOtpResult result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        isVerifying = false;
        setLoading(false);

        switch (result) {
            case SUCCESS:
                if (TextUtils.isEmpty(viewModel.getPhone())) {
                    showOtpError(getString(R.string.forgot_password_error_generic));
                    return;
                }
                otpErrorLayout.setVisibility(View.GONE);
                otpBoxesHelper.clearError();
                navigateToResetPassword();
                return;
            case WRONG_OTP:
                showOtpError(getString(R.string.otp_forgot_wrong));
                resetOtpInputForRetry();
                return;
            case EXPIRED:
                showOtpError(getString(R.string.otp_forgot_expired));
                resetOtpInputForRetry();
                enableResendNow();
                return;
            case ERROR:
            default:
                showOtpError(getString(R.string.forgot_password_error_generic));
                resetOtpInputForRetry();
                break;
        }
    }

    private void resetOtpInputForRetry() {
        otpBoxesHelper.clear();
        otpBoxesHelper.setEnabled(true);
        otpBoxesHelper.requestFocusFirst();
        updateVerifyButton(false);
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, state -> {
            if (state == null || isVerifying) {
                return;
            }
            if (state == ForgotPasswordOtpViewModel.UiState.RESEND_SUCCESS) {
                setLoading(false);
                otpBoxesHelper.clear();
                otpErrorLayout.setVisibility(View.GONE);
                otpBoxesHelper.clearError();
                startResendCountdown();
                Toast.makeText(this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                if (BuildConfig.DEBUG) {
                    viewModel.loadDebugOtp();
                }
                viewModel.resetToIdle();
            } else if (state == ForgotPasswordOtpViewModel.UiState.LOADING) {
                setLoading(true);
            } else {
                setLoading(false);
            }
        });

        viewModel.getResendError().observe(this, event -> {
            if (event == null) {
                return;
            }
            if (event.getContentIfNotHandled() != null) {
                showOtpError(getString(R.string.forgot_password_error_generic));
            }
        });

        viewModel.getDebugOtp().observe(this, otp -> {
            if (BuildConfig.DEBUG && otp != null && !otp.isEmpty()) {
                debugOtpTextView.setVisibility(View.VISIBLE);
                debugOtpTextView.setText(getString(R.string.otp_debug_label, otp));
            } else {
                debugOtpTextView.setVisibility(View.GONE);
            }
        });
    }

    private void navigateToResetPassword() {
        allowFinish = true;
        Intent intent = new Intent(this, ResetPasswordActivity.class);
        intent.putExtra(ResetPasswordActivity.EXTRA_PHONE, viewModel.getPhone());
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        allowFinish = true;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showOtpError(String message) {
        try {
            suppressOtpChangeClear = true;
            otpErrorText.setText(message);
            otpErrorLayout.setVisibility(View.VISIBLE);
            if (otpBoxesHelper != null) {
                otpBoxesHelper.showError();
            }
        } catch (Exception ignored) {
            otpErrorText.setText(message);
            otpErrorLayout.setVisibility(View.VISIBLE);
        } finally {
            suppressOtpChangeClear = false;
        }
    }

    private void startResendCountdown() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendCooldownActive = true;
        refreshResendState();

        resendTimer = new CountDownTimer(RESEND_COUNTDOWN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                resendOtpTextView.setText(getString(R.string.otp_resend_countdown, seconds));
            }

            @Override
            public void onFinish() {
                resendCooldownActive = false;
                refreshResendState();
            }
        }.start();
    }

    private void enableResendNow() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendCooldownActive = false;
        refreshResendState();
    }

    private void refreshResendState() {
        boolean canResend = !resendCooldownActive && !isLoading() && !isVerifying;
        resendOtpTextView.setEnabled(canResend);
        if (canResend) {
            resendOtpTextView.setText(getString(R.string.otp_resend));
            resendOtpTextView.setTextColor(
                    ContextCompat.getColor(ForgotPasswordOtpActivity.this, R.color.brand_primary));
        } else if (resendCooldownActive) {
            resendOtpTextView.setTextColor(ContextCompat.getColor(this, R.color.placeholder));
        }
    }

    private void updateVerifyButton(boolean enabled) {
        boolean hasPhone = !TextUtils.isEmpty(viewModel.getPhone());
        boolean buttonEnabled = enabled && hasPhone && !isLoading() && !isVerifying;
        verifyOtpButton.setEnabled(buttonEnabled);
        if (buttonEnabled) {
            verifyOtpButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
            verifyOtpButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        } else {
            verifyOtpButton.setBackgroundResource(R.drawable.bg_button_disabled);
            verifyOtpButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_disabled));
        }
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (otpBoxesHelper != null) {
            otpBoxesHelper.setEnabled(!loading);
        }
        updateVerifyButton(otpBoxesHelper != null && otpBoxesHelper.getOtp().length() == 6);
        refreshResendState();
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }
}
