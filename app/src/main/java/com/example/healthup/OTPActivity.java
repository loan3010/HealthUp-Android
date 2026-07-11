package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.data.repository.RegistrationRepository;
import com.example.healthup.ui.otp.OtpBoxesHelper;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UsernameGenerator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class OTPActivity extends AppCompatActivity {

    public static final String EXTRA_FULL_NAME = "extra_full_name";
    public static final String EXTRA_PHONE = "extra_phone";
    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_IS_SOCIAL_AUTH = "extra_is_social_auth";
    public static final String EXTRA_AUTH_PROVIDER = "extra_auth_provider";
    /** When true, after OTP ask for password to link Google to existing phone account. */
    public static final String EXTRA_LINK_EXISTING_ACCOUNT = "extra_link_existing_account";
    public static final String EXTRA_EXISTING_AUTH_EMAIL = "extra_existing_auth_email";
    public static final String EXTRA_EXISTING_UID = "extra_existing_uid";

    private static final long OTP_TIMEOUT_SECONDS = 60L;

    private String fullName;
    private String localPhone;
    private String email;
    private String password;
    private boolean isSocialAuth;
    private String authProvider;
    private boolean linkExistingAccount;
    private String existingAuthEmail;
    private String existingUid;

    private OtpBoxesHelper otpBoxesHelper;
    private LinearLayout otpErrorLayout;
    private TextView otpErrorText;
    private TextView otpSubtitleTextView;
    private TextView debugOtpTextView;
    private TextView resendOtpTextView;
    private MaterialButton verifyOtpButton;
    private FrameLayout loadingOverlay;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private RegistrationRepository registrationRepository;
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();
        registrationRepository = new RegistrationRepository();

        if (!readExtras()) {
            finish();
            return;
        }

        bindViews();
        setupOtpBoxes();
        setupActions();
        otpSubtitleTextView.setText(getString(R.string.otp_subtitle, PhoneNumberUtils.maskPhone(localPhone)));

        sendOtp(false);
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        super.onDestroy();
    }

    private boolean readExtras() {
        fullName = getIntent().getStringExtra(EXTRA_FULL_NAME);
        localPhone = PhoneNormalizer.normalize(getIntent().getStringExtra(EXTRA_PHONE));
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);
        isSocialAuth = getIntent().getBooleanExtra(EXTRA_IS_SOCIAL_AUTH, false);
        authProvider = getIntent().getStringExtra(EXTRA_AUTH_PROVIDER);
        linkExistingAccount = getIntent().getBooleanExtra(EXTRA_LINK_EXISTING_ACCOUNT, false);
        existingAuthEmail = getIntent().getStringExtra(EXTRA_EXISTING_AUTH_EMAIL);
        existingUid = getIntent().getStringExtra(EXTRA_EXISTING_UID);

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(localPhone)) {
            return false;
        }

        if (linkExistingAccount) {
            return !TextUtils.isEmpty(existingAuthEmail)
                    && !TextUtils.isEmpty(existingUid)
                    && !TextUtils.isEmpty(PhoneNumberUtils.toE164(localPhone));
        }

        if (!isSocialAuth && TextUtils.isEmpty(password)) {
            return false;
        }

        if (TextUtils.isEmpty(authProvider)) {
            authProvider = UserProfileBuilder.AUTH_PROVIDER_GOOGLE;
        }

        return !TextUtils.isEmpty(PhoneNumberUtils.toE164(localPhone));
    }

    private void bindViews() {
        otpErrorLayout = findViewById(R.id.otpErrorLayout);
        otpErrorText = findViewById(R.id.otpErrorText);
        otpSubtitleTextView = findViewById(R.id.otpSubtitleTextView);
        debugOtpTextView = findViewById(R.id.debugOtpTextView);
        resendOtpTextView = findViewById(R.id.resendOtpTextView);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupOtpBoxes() {
        otpBoxesHelper = new OtpBoxesHelper(this, findViewById(R.id.otpBoxesInclude));
        otpBoxesHelper.setListener(new OtpBoxesHelper.Listener() {
            @Override
            public void onOtpChanged(String otp, boolean complete) {
                otpErrorLayout.setVisibility(View.GONE);
                otpBoxesHelper.clearError();
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
        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        verifyOtpButton.setOnClickListener(v -> verifyOtpCode());
        resendOtpTextView.setOnClickListener(v -> {
            if (resendOtpTextView.isEnabled()) {
                sendOtp(true);
            }
        });
    }

    private void sendOtp(boolean isResend) {
        setLoading(true);
        otpErrorLayout.setVisibility(View.GONE);
        otpBoxesHelper.clearError();

        RegistrationRepository.SendOtpCallback callback = (result, otpForDebug) -> {
            setLoading(false);
            if (result == RegistrationRepository.SendOtpResult.SUCCESS) {
                showDebugOtp(otpForDebug);
                startResendCountdown();
                Toast.makeText(
                        OTPActivity.this,
                        isResend ? R.string.otp_sent : R.string.otp_sent,
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                String errorMsg = TextUtils.isEmpty(otpForDebug) ? getString(R.string.otp_send_failed) : otpForDebug;
                showSnackbar(errorMsg);
            }
        };

        if (isResend) {
            registrationRepository.resendOtp(localPhone, callback);
        } else {
            registrationRepository.sendOtp(localPhone, callback);
        }

        resendOtpTextView.setEnabled(false);
    }

    private void showDebugOtp(String otp) {
        if (!BuildConfig.DEBUG || TextUtils.isEmpty(otp)) {
            debugOtpTextView.setVisibility(View.GONE);
            return;
        }
        debugOtpTextView.setText(getString(R.string.otp_debug_label, otp));
        debugOtpTextView.setVisibility(View.VISIBLE);
    }

    private void verifyOtpCode() {
        String code = otpBoxesHelper.getOtp();
        if (code.length() != 6) {
            showOtpError(getString(R.string.otp_invalid_length));
            return;
        }

        setLoading(true);
        registrationRepository.verifyOtp(localPhone, code, result -> {
            switch (result) {
                case SUCCESS:
                    completeRegistration();
                    break;
                case WRONG_OTP:
                    setLoading(false);
                    showOtpError(getString(R.string.otp_invalid_code));
                    break;
                case EXPIRED:
                    setLoading(false);
                    showOtpError(getString(R.string.otp_forgot_expired));
                    break;
                case ERROR:
                default:
                    setLoading(false);
                    showSnackbar(getString(R.string.register_error_generic));
                    break;
            }
        });
    }

    private void completeRegistration() {
        if (linkExistingAccount) {
            navigateToLinkPassword();
            return;
        }
        if (isSocialAuth) {
            completeSocialRegistration();
            return;
        }

        // Auth email is always synthetic — real email lives in displayEmail + mock verify.
        String authEmail = RegisterValidator.buildAuthEmail(localPhone, null);
        String normalizedPhone = PhoneNormalizer.normalize(localPhone);
        String authSecret = AppPasswordHelper.authSecretForPhone(normalizedPhone);
        firebaseAuth.createUserWithEmailAndPassword(authEmail, authSecret)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        handleCreateUserFailure(task.getException());
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showSnackbar(getString(R.string.register_error_generic));
                        return;
                    }

                    saveUserProfile(user.getUid(), authEmail);
                });
    }

    private void handleCreateUserFailure(@Nullable Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            // Fallback only — RegisterActivity must catch this before OTP.
            String message = RegisterValidator.hasRealEmail(email)
                    ? getString(R.string.register_email_exists)
                    : getString(R.string.register_phone_exists);
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra(RegisterActivity.EXTRA_PHONE, localPhone);
            intent.putExtra(RegisterActivity.EXTRA_PREFILL_FULL_NAME, fullName);
            if (RegisterValidator.hasRealEmail(email)) {
                intent.putExtra(RegisterActivity.EXTRA_PREFILL_EMAIL, email);
                intent.putExtra(RegisterActivity.EXTRA_EMAIL_EXISTS, true);
            }
            startActivity(intent);
            finish();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        String detail = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : getString(R.string.register_error_generic);
        android.util.Log.w("OTPActivity", "createUser failed", exception);
        showSnackbar(detail);
    }

    private void navigateToLinkPassword() {
        setLoading(false);
        Intent intent = new Intent(this, com.example.healthup.auth.LinkGooglePasswordActivity.class);
        intent.putExtra(com.example.healthup.auth.LinkGooglePasswordActivity.EXTRA_PHONE, localPhone);
        intent.putExtra(com.example.healthup.auth.LinkGooglePasswordActivity.EXTRA_AUTH_EMAIL, existingAuthEmail);
        intent.putExtra(com.example.healthup.auth.LinkGooglePasswordActivity.EXTRA_EXISTING_UID, existingUid);
        intent.putExtra(com.example.healthup.auth.LinkGooglePasswordActivity.EXTRA_GOOGLE_EMAIL, email);
        startActivity(intent);
        finish();
    }

    private void completeSocialRegistration() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            setLoading(false);
            showSnackbar(getString(R.string.register_error_generic));
            return;
        }

        String authEmail = RegisterValidator.buildAuthEmail(localPhone, email);
        if (TextUtils.isEmpty(email) && !TextUtils.isEmpty(user.getEmail())) {
            authEmail = user.getEmail();
        }

        saveSocialUserProfile(user.getUid(), authEmail);
    }

    private void saveSocialUserProfile(String uid, String authEmail) {
        UsernameGenerator.generateUnique(fullName, new UsernameGenerator.Callback() {
            @Override
            public void onSuccess(@NonNull String username) {
                proceedToPersistSocial(uid, authEmail, username);
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                proceedToPersistSocial(uid, authEmail, localPhone);
            }
        });
    }

    private void proceedToPersistSocial(String uid, String authEmail, String username) {
        Map<String, Object> userData = UserProfileBuilder.buildSocialRegistration(
                fullName,
                localPhone,
                authEmail,
                email,
                authProvider,
                username
        );
        persistUserProfile(uid, userData);
    }

    private void saveUserProfile(String uid, String authEmail) {
        UsernameGenerator.generateUnique(fullName, new UsernameGenerator.Callback() {
            @Override
            public void onSuccess(@NonNull String username) {
                proceedToPersist(uid, authEmail, username);
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                // Nếu lỗi quyền (Permission), dùng username mặc định là số điện thoại để đăng ký luôn
                proceedToPersist(uid, authEmail, localPhone);
            }
        });
    }

    private void proceedToPersist(String uid, String authEmail, String username) {
        Map<String, Object> userData = UserProfileBuilder.buildPasswordRegistration(
                fullName,
                localPhone,
                authEmail,
                email,
                username,
                password
        );
        persistUserProfile(uid, userData);
    }

    private void persistUserProfile(String uid, Map<String, Object> userData) {
        firebaseFirestore.collection("users")
                .document(uid)
                .set(userData)
                .addOnSuccessListener(unused -> {
                    registrationRepository.deleteOtpDoc(localPhone);
                    boolean needsEmailVerify = !isSocialAuth && RegisterValidator.hasRealEmail(email);
                    if (needsEmailVerify) {
                        setLoading(false);
                        navigateToEmailVerification(uid, false);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                        navigateAfterRegistration(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(getString(R.string.register_error_generic));
                });
    }

    private void navigateToEmailVerification(String uid, boolean ignoredMailSent) {
        Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
        GuestCartManager.getInstance(this).mergeToFirestore(uid, () -> runOnUiThread(() -> {
            Intent intent = new Intent(OTPActivity.this, EmailVerificationPendingActivity.class);
            intent.putExtra(EmailVerificationPendingActivity.EXTRA_EMAIL, email);
            intent.putExtra(EmailVerificationPendingActivity.EXTRA_MAIL_ALREADY_SENT, false);
            startActivity(intent);
            finish();
        }));
    }

    private void navigateAfterRegistration(String uid) {
        GuestCartManager.getInstance(this).mergeToFirestore(uid, () -> runOnUiThread(() -> {
            startActivity(CheckoutIntentHelper.buildPostAuthMainIntent(OTPActivity.this));
            finish();
        }));
    }

    private void startResendCountdown() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }

        resendOtpTextView.setEnabled(false);
        resendOtpTextView.setTextColor(ContextCompat.getColor(this, R.color.placeholder));

        resendTimer = new CountDownTimer(OTP_TIMEOUT_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                resendOtpTextView.setText(getString(R.string.otp_resend_countdown, seconds));
            }

            @Override
            public void onFinish() {
                resendOtpTextView.setEnabled(true);
                resendOtpTextView.setText(getString(R.string.otp_resend));
                resendOtpTextView.setTextColor(ContextCompat.getColor(OTPActivity.this, R.color.brand_primary));
            }
        }.start();
    }

    private void showOtpError(String message) {
        otpErrorText.setText(message);
        otpErrorLayout.setVisibility(View.VISIBLE);
        otpBoxesHelper.showError();
    }

    private void updateVerifyButton(boolean enabled) {
        boolean buttonEnabled = enabled && !isLoading();
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
        otpBoxesHelper.setEnabled(!loading);
        updateVerifyButton(otpBoxesHelper.getOtp().length() == 6);
        resendOtpTextView.setEnabled(!loading && resendOtpTextView.isEnabled());
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.otpScrollView), message, Snackbar.LENGTH_LONG).show();
    }
}
