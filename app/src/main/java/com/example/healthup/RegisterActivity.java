package com.example.healthup;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.healthup.auth.AuthOrphanCleaner;
import com.example.healthup.auth.SocialAuthHelper;
import com.example.healthup.auth.SyntheticAuthProbe;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends BaseAppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";
    public static final String EXTRA_PREFILL_EMAIL = "extra_prefill_email";
    public static final String EXTRA_PREFILL_FULL_NAME = "extra_prefill_full_name";
    public static final String EXTRA_EMAIL_EXISTS = "extra_email_exists";

    private static final int BORDER_ANIMATION_MS = 200;

    private enum InputState {
        DEFAULT,
        FOCUSED,
        ERROR
    }

    private enum FieldType {
        FULL_NAME,
        PHONE,
        EMAIL,
        PASSWORD,
        CONFIRM_PASSWORD
    }

    private static final class FieldViews {
        TextView label;
        FrameLayout container;
        LinearLayout errorLayout;
        TextView errorText;
        EditText editText;
        boolean touched;
        boolean hasError;
        int borderRes = R.drawable.bg_input_default;
    }

    private final Map<FieldType, FieldViews> fields = new HashMap<>();

    private MaterialButton registerButton;
    private FrameLayout loadingOverlay;
    private LinearLayout googleButton;
    private LinearLayout facebookButton;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private SocialAuthHelper socialAuthHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        bindViews();
        applyPrefillPhone();
        applyPrefillFromExtras();
        setupSocialAuth();
        setupInputBehavior();
        setupActions();
        updateRegisterButtonState();
    }

    private void applyPrefillFromExtras() {
        String prefillName = getIntent().getStringExtra(EXTRA_PREFILL_FULL_NAME);
        if (!TextUtils.isEmpty(prefillName)) {
            fields.get(FieldType.FULL_NAME).editText.setText(prefillName);
        }
        String prefillEmail = getIntent().getStringExtra(EXTRA_PREFILL_EMAIL);
        if (!TextUtils.isEmpty(prefillEmail)) {
            fields.get(FieldType.EMAIL).editText.setText(prefillEmail);
        }
        if (getIntent().getBooleanExtra(EXTRA_EMAIL_EXISTS, false)) {
            showFieldError(fields.get(FieldType.EMAIL), getString(R.string.register_email_exists));
        }
    }

    private void applyPrefillPhone() {
        String prefillPhone = getIntent().getStringExtra(EXTRA_PHONE);
        if (TextUtils.isEmpty(prefillPhone)) {
            prefillPhone = getIntent().getStringExtra(CheckoutIntentHelper.EXTRA_PREFILL_PHONE);
        }
        if (!TextUtils.isEmpty(prefillPhone)) {
            fields.get(FieldType.PHONE).editText.setText(PhoneNormalizer.normalize(prefillPhone));
        }
    }

    private void bindViews() {
        registerButton = findViewById(R.id.registerButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        googleButton = findViewById(R.id.googleButton);
        facebookButton = findViewById(R.id.facebookButton);

        bindField(FieldType.FULL_NAME,
                findViewById(R.id.fullNameLabel),
                findViewById(R.id.fullNameInputContainer),
                findViewById(R.id.fullNameErrorLayout),
                findViewById(R.id.fullNameErrorText),
                findViewById(R.id.fullNameEditText));

        bindField(FieldType.PHONE,
                findViewById(R.id.phoneLabel),
                findViewById(R.id.phoneInputContainer),
                findViewById(R.id.phoneErrorLayout),
                findViewById(R.id.phoneErrorText),
                findViewById(R.id.phoneEditText));

        bindField(FieldType.EMAIL,
                findViewById(R.id.emailLabel),
                findViewById(R.id.emailInputContainer),
                findViewById(R.id.emailErrorLayout),
                findViewById(R.id.emailErrorText),
                findViewById(R.id.emailEditText));

        bindField(FieldType.PASSWORD,
                findViewById(R.id.passwordLabel),
                findViewById(R.id.passwordInputContainer),
                findViewById(R.id.passwordErrorLayout),
                findViewById(R.id.passwordErrorText),
                findViewById(R.id.passwordEditText));

        bindField(FieldType.CONFIRM_PASSWORD,
                findViewById(R.id.confirmPasswordLabel),
                findViewById(R.id.confirmPasswordInputContainer),
                findViewById(R.id.confirmPasswordErrorLayout),
                findViewById(R.id.confirmPasswordErrorText),
                findViewById(R.id.confirmPasswordEditText));
    }

    private void setupSocialAuth() {
        socialAuthHelper = new SocialAuthHelper(this, new SocialAuthHelper.Listener() {
            @Override
            public void onLoadingChanged(boolean loading) {
                setLoading(loading);
            }

            @Override
            public void onError(@NonNull String message) {
                showSnackbar(message);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (socialAuthHelper != null) {
            socialAuthHelper.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void bindField(
            FieldType type,
            TextView label,
            FrameLayout container,
            LinearLayout errorLayout,
            TextView errorText,
            EditText editText
    ) {
        FieldViews fieldViews = new FieldViews();
        fieldViews.label = label;
        fieldViews.container = container;
        fieldViews.errorLayout = errorLayout;
        fieldViews.errorText = errorText;
        fieldViews.editText = editText;
        fields.put(type, fieldViews);
    }

    private void setupActions() {
        TextView loginTextView = findViewById(R.id.loginTextView);

        registerButton.setOnClickListener(v -> attemptRegister());
        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> CheckoutIntentHelper.openMainHome(this));
        }
        loginTextView.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
        googleButton.setOnClickListener(v -> socialAuthHelper.signInWithGoogle());
        facebookButton.setOnClickListener(v -> socialAuthHelper.signInWithFacebook());
    }

    private void setupInputBehavior() {
        for (FieldType type : FieldType.values()) {
            FieldViews field = fields.get(type);
            setupField(type, field);
            field.editText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (field.touched) {
                        validateField(type, true);
                    }
                    updateRegisterButtonState();
                }
            });
        }
    }

    private void setupField(FieldType type, FieldViews field) {
        field.editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (field.touched) {
                    validateField(type, true);
                } else {
                    applyInputState(field, InputState.FOCUSED);
                }
                return;
            }

            field.touched = true;
            validateField(type, true);
        });
    }

    private void attemptRegister() {
        for (FieldType type : FieldType.values()) {
            fields.get(type).touched = true;
        }

        boolean allValid = true;
        for (FieldType type : FieldType.values()) {
            if (!validateField(type, true)) {
                allValid = false;
            }
        }
        if (!allValid) {
            return;
        }

        String fullName = getFieldValue(FieldType.FULL_NAME);
        String phone = PhoneNormalizer.normalize(getFieldValue(FieldType.PHONE));
        String email = getFieldValue(FieldType.EMAIL);
        String password = getFieldValue(FieldType.PASSWORD);

        setLoading(true);
        checkPhoneAndSendOtp(fullName, phone, email, password);
    }

    private void checkPhoneAndSendOtp(String fullName, String phone, String email, String password) {
        UserPhoneLookup.queryUsers(phone)
                .addOnSuccessListener(phoneQuery -> {
                    if (!phoneQuery.isEmpty()) {
                        setLoading(false);
                        handleApiError(RegisterApiErrorHandler.ErrorType.PHONE_ALREADY_EXISTS);
                        return;
                    }

                    probeSyntheticAuthPhoneThenContinue(fullName, phone, email, password);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    handleApiError(RegisterApiErrorHandler.mapException(e));
                });
    }

    /**
     * Firestore may be empty while Auth still has {@code phone@healthup.app} from a
     * failed/incomplete registration — probe Auth before OTP to avoid the mismatch
     * with forgot-password (Firestore-only) vs register (OTP collision).
     */
    private void probeSyntheticAuthPhoneThenContinue(
            String fullName,
            String phone,
            String email,
            String password
    ) {
        SyntheticAuthProbe.probePhoneAccount(firebaseAuth, phone, new SyntheticAuthProbe.Callback() {
            @Override
            public void onExists() {
                AuthOrphanCleaner.deleteSyntheticAuthOrphan(phone, new AuthOrphanCleaner.Callback() {
                    @Override
                    public void onDeleted() {
                        continueAfterSyntheticProbe(fullName, phone, email, password);
                    }

                    @Override
                    public void onNotFound() {
                        // Probe saw a collision but orphan is already gone — continue.
                        continueAfterSyntheticProbe(fullName, phone, email, password);
                    }

                    @Override
                    public void onProfileExists() {
                        setLoading(false);
                        handleApiError(RegisterApiErrorHandler.ErrorType.PHONE_ALREADY_EXISTS);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        setLoading(false);
                        handleApiError(RegisterApiErrorHandler.mapException(error));
                    }
                });
            }

            @Override
            public void onNotExists() {
                continueAfterSyntheticProbe(fullName, phone, email, password);
            }

            @Override
            public void onError(@NonNull Exception error) {
                setLoading(false);
                handleApiError(RegisterApiErrorHandler.mapException(error));
            }
        });
    }

    private void continueAfterSyntheticProbe(
            String fullName,
            String phone,
            String email,
            String password
    ) {
        if (TextUtils.isEmpty(email)) {
            launchOtpScreen(fullName, phone, email, password);
            return;
        }
        checkEmailAvailableThenContinue(fullName, phone, email.trim(), password);
    }

    /**
     * Block OTP if email is already used in Firestore or Firebase Auth.
     * Auth check uses a create+delete probe because {@code fetchSignInMethodsForEmail}
     * is empty under email-enumeration protection — that was why duplicates only failed after OTP.
     */
    private void checkEmailAvailableThenContinue(
            String fullName,
            String phone,
            String email,
            String password
    ) {
        // Always lowercase — matches how profiles store displayEmail/email.
        final String normalizedEmail = RegisterValidator.normalizeEmail(email);
        if (TextUtils.isEmpty(normalizedEmail)) {
            launchOtpScreen(fullName, phone, email, password);
            return;
        }

        firebaseFirestore.collection("users")
                .whereEqualTo("displayEmail", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(byDisplay -> {
                    if (!byDisplay.isEmpty()) {
                        setLoading(false);
                        handleApiError(RegisterApiErrorHandler.ErrorType.EMAIL_ALREADY_EXISTS);
                        return;
                    }
                    firebaseFirestore.collection("users")
                            .whereEqualTo("email", normalizedEmail)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(byEmail -> {
                                if (!byEmail.isEmpty()) {
                                    setLoading(false);
                                    handleApiError(RegisterApiErrorHandler.ErrorType.EMAIL_ALREADY_EXISTS);
                                    return;
                                }
                                probeAuthEmailThenContinue(fullName, phone, normalizedEmail, password);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                handleApiError(RegisterApiErrorHandler.mapException(e));
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    handleApiError(RegisterApiErrorHandler.mapException(e));
                });
    }

    /**
     * Reliably detect Auth email collision on the Register screen (before OTP).
     * Creates then immediately deletes the Auth user if the email is free.
     * Uses a random probe password so a failed cleanup cannot leave an account
     * with the user's real password.
     */
    private void probeAuthEmailThenContinue(
            String fullName,
            String phone,
            String email,
            String password
    ) {
        String probePassword = java.util.UUID.randomUUID() + "Aa1!";
        firebaseAuth.createUserWithEmailAndPassword(email, probePassword)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Exception exception = task.getException();
                        if (exception instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            handleApiError(RegisterApiErrorHandler.ErrorType.EMAIL_ALREADY_EXISTS);
                        } else {
                            handleApiError(RegisterApiErrorHandler.mapException(exception));
                        }
                        return;
                    }

                    FirebaseUser created = firebaseAuth.getCurrentUser();
                    if (created == null) {
                        setLoading(false);
                        handleApiError(RegisterApiErrorHandler.ErrorType.INVALID_REQUEST);
                        return;
                    }

                    created.delete()
                            .addOnCompleteListener(deleteTask -> {
                                firebaseAuth.signOut();
                                if (!deleteTask.isSuccessful()) {
                                    // Email was free but cleanup failed — still block to avoid orphan confusion.
                                    setLoading(false);
                                    showSnackbar(getString(R.string.register_error_generic));
                                    return;
                                }
                                launchOtpScreen(fullName, phone, email, password);
                            });
                });
    }

    private void launchOtpScreen(String fullName, String phone, String email, String password) {
        setLoading(false);
        Intent intent = new Intent(RegisterActivity.this, OTPActivity.class);
        intent.putExtra(OTPActivity.EXTRA_FULL_NAME, fullName);
        intent.putExtra(OTPActivity.EXTRA_PHONE, phone);
        intent.putExtra(OTPActivity.EXTRA_EMAIL, email == null ? "" : email);
        intent.putExtra(OTPActivity.EXTRA_PASSWORD, password);
        intent.putExtra(CheckoutIntentHelper.EXTRA_RETURN_TO_CHECKOUT,
                CheckoutIntentHelper.shouldReturnToCheckout(getIntent()));
        startActivity(intent);
        // Don't leave Register under OTP — after success user must not land back on the form.
        finish();
    }

    private boolean validateField(FieldType type, boolean showError) {
        FieldViews field = fields.get(type);
        String value = getFieldValue(type);
        String password = getFieldValue(FieldType.PASSWORD);
        String errorCode;

        switch (type) {
            case FULL_NAME:
                errorCode = RegisterValidator.validateFullName(value);
                break;
            case PHONE:
                errorCode = RegisterValidator.validatePhone(value);
                break;
            case EMAIL:
                errorCode = RegisterValidator.validateEmail(value);
                break;
            case PASSWORD:
                errorCode = RegisterValidator.validatePassword(value);
                break;
            case CONFIRM_PASSWORD:
                errorCode = RegisterValidator.validateConfirmPassword(password, value);
                break;
            default:
                errorCode = null;
        }

        if (errorCode == null) {
            if (showError || field.touched) {
                clearFieldError(field, field.editText.hasFocus());
                field.hasError = false;
            }
            return true;
        }

        if (!showError && !field.touched) {
            return false;
        }

        showFieldError(field, resolveErrorMessage(type, errorCode));
        return false;
    }

    private String resolveErrorMessage(FieldType type, String errorCode) {
        switch (type) {
            case FULL_NAME:
                return getString(R.string.register_full_name_required);
            case PHONE:
                if ("length".equals(errorCode)) {
                    return getString(R.string.register_phone_length_error);
                }
                if ("invalid".equals(errorCode)) {
                    return getString(R.string.register_phone_invalid_error);
                }
                return getString(R.string.register_phone_required);
            case EMAIL:
                return getString(R.string.register_email_invalid_error);
            case PASSWORD:
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
            case CONFIRM_PASSWORD:
                if ("mismatch".equals(errorCode)) {
                    return getString(R.string.register_confirm_password_mismatch);
                }
                return getString(R.string.register_confirm_password_required);
            default:
                return getString(R.string.register_error_generic);
        }
    }

    private void handleApiError(RegisterApiErrorHandler.ErrorType errorType) {
        switch (errorType) {
            case PHONE_ALREADY_EXISTS:
                showFieldError(fields.get(FieldType.PHONE), getString(R.string.register_phone_exists));
                break;
            case EMAIL_ALREADY_EXISTS:
                showFieldError(fields.get(FieldType.EMAIL), getString(R.string.register_email_exists));
                break;
            case ACCOUNT_ALREADY_EXISTS:
                showAllFieldsError(getString(R.string.register_account_exists));
                break;
            case NETWORK_ERROR:
                showSnackbar(getString(R.string.register_error_network));
                break;
            case TIMEOUT:
                showSnackbar(getString(R.string.register_error_timeout));
                break;
            case INVALID_REQUEST:
            default:
                showSnackbar(getString(R.string.register_error_generic));
                break;
        }
    }

    private void showAllFieldsError(String message) {
        showFieldError(fields.get(FieldType.FULL_NAME), message);
        showFieldError(fields.get(FieldType.PHONE), getString(R.string.register_phone_exists));
        showFieldError(fields.get(FieldType.EMAIL), message);
        showFieldError(fields.get(FieldType.PASSWORD), message);
        showFieldError(fields.get(FieldType.CONFIRM_PASSWORD), message);
    }

    private void showFieldError(FieldViews field, String message) {
        field.hasError = true;
        field.errorText.setText(message);
        field.errorLayout.setVisibility(View.VISIBLE);
        applyInputState(field, InputState.ERROR);
        shakeView(field.container);
        fadeInView(field.errorLayout);
    }

    private void clearFieldError(FieldViews field, boolean isFocused) {
        field.errorLayout.setVisibility(View.GONE);
        applyInputState(field, isFocused ? InputState.FOCUSED : InputState.DEFAULT);
    }

    private void applyInputState(FieldViews field, InputState state) {
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

        if (field.borderRes != borderRes) {
            animateBorder(field.container, field.borderRes, borderRes);
            field.borderRes = borderRes;
        }

        field.label.setTextColor(ContextCompat.getColor(this, labelColor));
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

    private void updateRegisterButtonState() {
        String fullName = getFieldValue(FieldType.FULL_NAME);
        String phone = getFieldValue(FieldType.PHONE);
        String email = getFieldValue(FieldType.EMAIL);
        String password = getFieldValue(FieldType.PASSWORD);
        String confirmPassword = getFieldValue(FieldType.CONFIRM_PASSWORD);

        boolean enabled = RegisterValidator.isFormValid(
                fullName, phone, email, password, confirmPassword
        ) && !isLoading();

        registerButton.setEnabled(enabled);
        if (enabled) {
            registerButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
            registerButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        } else {
            registerButton.setBackgroundResource(R.drawable.bg_button_disabled);
            registerButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_disabled));
        }
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);

        for (FieldViews field : fields.values()) {
            field.editText.setEnabled(!loading);
        }
        googleButton.setEnabled(!loading);
        facebookButton.setEnabled(!loading);
        registerButton.setEnabled(!loading && RegisterValidator.isFormValid(
                getFieldValue(FieldType.FULL_NAME),
                getFieldValue(FieldType.PHONE),
                getFieldValue(FieldType.EMAIL),
                getFieldValue(FieldType.PASSWORD),
                getFieldValue(FieldType.CONFIRM_PASSWORD)
        ));
        if (!loading) {
            updateRegisterButtonState();
        }
    }

    private String getFieldValue(FieldType type) {
        EditText editText = fields.get(type).editText;
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void showSnackbar(String message) {
        View root = findViewById(R.id.registerScrollView);
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
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
