package com.example.healthup.admin;

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
import com.example.healthup.R;
import com.example.healthup.data.repository.OtpRepository;
import com.example.healthup.ui.otp.OtpBoxesHelper;
import com.google.android.material.button.MaterialButton;

public class AdminForgotPasswordOtpActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_admin_email";
    public static final String EXTRA_ADMIN_UID = "extra_admin_uid";

    private static final long OTP_VALIDITY_SECONDS =
            OtpRepository.ADMIN_OTP_EXPIRY_MS / 1000L;

    private AdminForgotPasswordOtpViewModel viewModel;
    private OtpBoxesHelper otpBoxesHelper;
    private TextView otpSubtitleTextView;
    private TextView otpValidityHintTextView;
    private LinearLayout otpErrorLayout;
    private TextView otpErrorText;
    private TextView debugOtpTextView;
    private TextView showDemoOtpTextView;
    private MaterialButton verifyOtpButton;
    private TextView resendOtpTextView;
    private FrameLayout loadingOverlay;

    private CountDownTimer otpValidityTimer;
    private boolean otpExpired;
    private boolean suppressOtpChangeClear;
    private boolean isVerifying;
    private boolean allowFinish;
    private String lastDebugOtp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password_otp);

        viewModel = new ViewModelProvider(this).get(AdminForgotPasswordOtpViewModel.class);
        viewModel.cancelPendingVerify();

        bindViews();
        initSession(savedInstanceState);
        setupOtpBoxes();
        setupActions();
        setupBackPressedHandler();
        observeViewModel();

        if (otpValidityHintTextView != null) {
            otpValidityHintTextView.setText(R.string.admin_otp_validity_hint);
        }

        if (!TextUtils.isEmpty(viewModel.getEmail()) && !TextUtils.isEmpty(viewModel.getAdminUid())) {
            otpSubtitleTextView.setText(
                    getString(R.string.admin_forgot_otp_subtitle, maskEmail(viewModel.getEmail())));
            showDemoOtpTextView.setVisibility(View.VISIBLE);
            if (!otpExpired) {
                startOtpValidityCountdown();
            } else {
                onOtpExpiredUi();
            }
            if (BuildConfig.DEBUG) {
                viewModel.loadDebugOtp();
            }
        } else {
            showOtpError(getString(R.string.reset_password_session_invalid));
            verifyOtpButton.setEnabled(false);
            otpBoxesHelper.setEnabled(false);
        }
    }

    @Override
    public void finish() {
        if (!allowFinish) return;
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(viewModel.getEmail())) {
            outState.putString(EXTRA_EMAIL, viewModel.getEmail());
        }
        if (!TextUtils.isEmpty(viewModel.getAdminUid())) {
            outState.putString(EXTRA_ADMIN_UID, viewModel.getAdminUid());
        }
        outState.putBoolean("is_verifying", isVerifying);
        outState.putBoolean("otp_expired", otpExpired);
    }

    @Override
    protected void onDestroy() {
        if (otpValidityTimer != null) otpValidityTimer.cancel();
        super.onDestroy();
    }

    private void initSession(Bundle savedInstanceState) {
        String email = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_EMAIL)
                : getIntent().getStringExtra(EXTRA_EMAIL);
        String adminUid = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_ADMIN_UID)
                : getIntent().getStringExtra(EXTRA_ADMIN_UID);
        if (!TextUtils.isEmpty(email)) viewModel.setEmail(email);
        if (!TextUtils.isEmpty(adminUid)) viewModel.setAdminUid(adminUid);
        if (savedInstanceState != null) {
            isVerifying = savedInstanceState.getBoolean("is_verifying", false);
            otpExpired = savedInstanceState.getBoolean("otp_expired", false);
            setLoading(isVerifying);
        }
    }

    private void bindViews() {
        otpSubtitleTextView = findViewById(R.id.otpSubtitleTextView);
        otpValidityHintTextView = findViewById(R.id.otpValidityHintTextView);
        otpErrorLayout = findViewById(R.id.otpErrorLayout);
        otpErrorText = findViewById(R.id.otpErrorText);
        debugOtpTextView = findViewById(R.id.debugOtpTextView);
        showDemoOtpTextView = findViewById(R.id.showDemoOtpTextView);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resendOtpTextView = findViewById(R.id.resendOtpTextView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupOtpBoxes() {
        otpBoxesHelper = new OtpBoxesHelper(this, findViewById(R.id.otpBoxesInclude));
        otpBoxesHelper.setListener(new OtpBoxesHelper.Listener() {
            @Override
            public void onOtpChanged(String otp, boolean complete) {
                if (otpExpired) return;
                if (!suppressOtpChangeClear) {
                    otpErrorLayout.setVisibility(View.GONE);
                    otpBoxesHelper.clearError();
                }
                updateVerifyButton(complete);
            }

            @Override
            public void onOtpComplete(String otp) {
                if (!otpExpired) {
                    updateVerifyButton(true);
                }
            }
        });
        if (!otpExpired) {
            otpBoxesHelper.requestFocusFirst();
        }
        updateVerifyButton(false);
    }

    private void setupActions() {
        findViewById(R.id.backTextView).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        verifyOtpButton.setOnClickListener(v -> submitOtp());
        resendOtpTextView.setOnClickListener(v -> {
            if (otpExpired && resendOtpTextView.isEnabled() && !isVerifying) {
                viewModel.resendOtp();
            }
        });
        if (showDemoOtpTextView != null) {
            showDemoOtpTextView.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(lastDebugOtp)) {
                    Toast.makeText(this, getString(R.string.otp_debug_label, lastDebugOtp), Toast.LENGTH_LONG).show();
                } else {
                    viewModel.loadDebugOtp();
                }
            });
        }
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isVerifying) return;
                setEnabled(false);
                navigateToAdminLogin();
            }
        });
    }

    private void submitOtp() {
        if (otpExpired) {
            showOtpError(getString(R.string.otp_forgot_expired));
            return;
        }
        if (isVerifying || isFinishing() || TextUtils.isEmpty(viewModel.getAdminUid())) return;
        String otp = otpBoxesHelper.getOtp();
        if (otp.length() != 6) {
            showOtpError(getString(R.string.otp_invalid_length));
            return;
        }
        isVerifying = true;
        setLoading(true);
        viewModel.verifyOtp(otp, this::handleVerifyResult);
    }

    private void handleVerifyResult(AdminPasswordResetRepository.VerifyOtpResult result) {
        if (isFinishing() || isDestroyed()) return;
        isVerifying = false;
        setLoading(false);
        switch (result) {
            case SUCCESS:
                if (otpValidityTimer != null) otpValidityTimer.cancel();
                otpErrorLayout.setVisibility(View.GONE);
                otpBoxesHelper.clearError();
                navigateToResetPassword();
                return;
            case WRONG_OTP:
                if (otpExpired) {
                    onOtpExpiredUi();
                    return;
                }
                showOtpError(getString(R.string.otp_forgot_wrong));
                resetOtpInputForRetry();
                return;
            case EXPIRED:
                onOtpExpiredUi();
                return;
            case ERROR:
            default:
                if (otpExpired) {
                    onOtpExpiredUi();
                    return;
                }
                showOtpError(getString(R.string.forgot_password_error_generic));
                resetOtpInputForRetry();
                break;
        }
    }

    private void resetOtpInputForRetry() {
        if (otpExpired) return;
        otpBoxesHelper.clear();
        otpBoxesHelper.setEnabled(true);
        otpBoxesHelper.requestFocusFirst();
        updateVerifyButton(false);
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, state -> {
            if (state == null || isVerifying) return;
            if (state == AdminForgotPasswordOtpViewModel.UiState.RESEND_SUCCESS) {
                setLoading(false);
                otpExpired = false;
                otpBoxesHelper.clear();
                otpErrorLayout.setVisibility(View.GONE);
                otpBoxesHelper.clearError();
                otpBoxesHelper.setEnabled(true);
                otpBoxesHelper.requestFocusFirst();
                startOtpValidityCountdown();
                Toast.makeText(this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                viewModel.loadDebugOtp();
                viewModel.resetToIdle();
            } else if (state == AdminForgotPasswordOtpViewModel.UiState.LOADING) {
                setLoading(true);
            } else {
                setLoading(false);
            }
        });

        viewModel.getResendError().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled() != null) {
                showOtpError(getString(R.string.forgot_password_error_generic));
            }
        });

        viewModel.getDebugOtp().observe(this, otp -> {
            lastDebugOtp = otp != null ? otp : "";
            if (BuildConfig.DEBUG && !lastDebugOtp.isEmpty()) {
                debugOtpTextView.setVisibility(View.VISIBLE);
                debugOtpTextView.setText(getString(R.string.otp_debug_label, lastDebugOtp));
            } else {
                debugOtpTextView.setVisibility(View.GONE);
            }
        });
    }

    private void startOtpValidityCountdown() {
        if (otpValidityTimer != null) otpValidityTimer.cancel();
        otpExpired = false;
        otpBoxesHelper.setEnabled(true);
        resendOtpTextView.setEnabled(false);
        resendOtpTextView.setTextColor(ContextCompat.getColor(this, R.color.placeholder));

        otpValidityTimer = new CountDownTimer(OTP_VALIDITY_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                resendOtpTextView.setText(getString(R.string.admin_otp_expires_in, seconds));
            }

            @Override
            public void onFinish() {
                onOtpExpiredUi();
            }
        }.start();
    }

    private void onOtpExpiredUi() {
        otpExpired = true;
        if (otpValidityTimer != null) otpValidityTimer.cancel();
        otpBoxesHelper.clear();
        otpBoxesHelper.setEnabled(false);
        updateVerifyButton(false);
        showOtpError(getString(R.string.otp_forgot_expired));
        resendOtpTextView.setEnabled(!isLoading() && !isVerifying);
        resendOtpTextView.setText(getString(R.string.otp_resend));
        resendOtpTextView.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
    }

    private void navigateToResetPassword() {
        allowFinish = true;
        Intent intent = new Intent(this, AdminResetPasswordActivity.class);
        intent.putExtra(AdminResetPasswordActivity.EXTRA_ADMIN_UID, viewModel.getAdminUid());
        startActivity(intent);
        finish();
    }

    private void navigateToAdminLogin() {
        allowFinish = true;
        Intent intent = new Intent(this, AdminLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showOtpError(String message) {
        try {
            suppressOtpChangeClear = true;
            otpErrorText.setText(message);
            otpErrorLayout.setVisibility(View.VISIBLE);
            if (!otpExpired) {
                otpBoxesHelper.showError();
            }
        } finally {
            suppressOtpChangeClear = false;
        }
    }

    private void updateVerifyButton(boolean enabled) {
        boolean ready = enabled
                && !otpExpired
                && !TextUtils.isEmpty(viewModel.getAdminUid())
                && !isLoading()
                && !isVerifying;
        verifyOtpButton.setEnabled(ready);
        verifyOtpButton.setBackgroundResource(ready
                ? R.drawable.bg_login_button_enabled
                : R.drawable.bg_button_disabled);
        verifyOtpButton.setTextColor(ContextCompat.getColor(this, ready
                ? R.color.button_text_on_primary
                : R.color.button_text_disabled));
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (otpBoxesHelper != null && !otpExpired) {
            otpBoxesHelper.setEnabled(!loading);
        }
        updateVerifyButton(otpBoxesHelper != null && otpBoxesHelper.getOtp().length() == 6);
        if (otpExpired) {
            resendOtpTextView.setEnabled(!loading && !isVerifying);
        }
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    @NonNull
    private static String maskEmail(@NonNull String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
