package com.example.healthup.auth;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.healthup.OTPActivity;
import com.example.healthup.R;
import com.example.healthup.RegisterValidator;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class SocialCompleteProfileActivity extends AppCompatActivity {

    public static final String EXTRA_FULL_NAME = "extra_social_full_name";
    public static final String EXTRA_AUTH_EMAIL = "extra_social_auth_email";
    public static final String EXTRA_AUTH_PROVIDER = "extra_social_auth_provider";

    private static final int BORDER_ANIMATION_MS = 200;

    private enum InputState {
        DEFAULT,
        FOCUSED,
        ERROR
    }

    private String authEmail;
    private String authProvider;

    private TextView fullNameLabel;
    private TextView phoneLabel;
    private FrameLayout fullNameInputContainer;
    private FrameLayout phoneInputContainer;
    private LinearLayout fullNameErrorLayout;
    private LinearLayout phoneErrorLayout;
    private TextView fullNameErrorText;
    private TextView phoneErrorText;
    private EditText fullNameEditText;
    private EditText phoneEditText;
    private MaterialButton continueButton;
    private FrameLayout loadingOverlay;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    private boolean fullNameTouched;
    private boolean phoneTouched;
    private boolean fullNameHasError;
    private boolean phoneHasError;
    private int fullNameBorderRes = R.drawable.bg_input_default;
    private int phoneBorderRes = R.drawable.bg_input_default;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_social_complete_profile);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        if (firebaseAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        authEmail = getIntent().getStringExtra(EXTRA_AUTH_EMAIL);
        authProvider = getIntent().getStringExtra(EXTRA_AUTH_PROVIDER);
        if (TextUtils.isEmpty(authProvider)) {
            authProvider = UserProfileBuilder.AUTH_PROVIDER_GOOGLE;
        }
        if (authEmail == null) {
            authEmail = "";
        }

        bindViews();
        String prefillName = getIntent().getStringExtra(EXTRA_FULL_NAME);
        if (!TextUtils.isEmpty(prefillName)) {
            fullNameEditText.setText(prefillName);
        }

        setupInputBehavior();
        setupActions();
        updateContinueButtonState();
    }

    private void bindViews() {
        fullNameLabel = findViewById(R.id.fullNameLabel);
        phoneLabel = findViewById(R.id.phoneLabel);
        fullNameInputContainer = findViewById(R.id.fullNameInputContainer);
        phoneInputContainer = findViewById(R.id.phoneInputContainer);
        fullNameErrorLayout = findViewById(R.id.fullNameErrorLayout);
        phoneErrorLayout = findViewById(R.id.phoneErrorLayout);
        fullNameErrorText = findViewById(R.id.fullNameErrorText);
        phoneErrorText = findViewById(R.id.phoneErrorText);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        continueButton = findViewById(R.id.continueButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupActions() {
        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        continueButton.setOnClickListener(v -> attemptContinue());
    }

    private void setupInputBehavior() {
        setupField(fullNameEditText, fullNameInputContainer, fullNameLabel, fullNameErrorLayout, true);
        setupField(phoneEditText, phoneInputContainer, phoneLabel, phoneErrorLayout, false);

        fullNameEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (fullNameHasError) {
                    clearFieldError(fullNameInputContainer, fullNameLabel, fullNameErrorLayout, fullNameEditText.hasFocus(), true);
                    fullNameHasError = false;
                }
                updateContinueButtonState();
            }
        });

        phoneEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (phoneHasError) {
                    clearFieldError(phoneInputContainer, phoneLabel, phoneErrorLayout, phoneEditText.hasFocus(), false);
                    phoneHasError = false;
                }
                updateContinueButtonState();
            }
        });
    }

    private void setupField(
            EditText editText,
            FrameLayout container,
            TextView label,
            LinearLayout errorLayout,
            boolean isFullName
    ) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (isFullName) {
                    fullNameHasError = false;
                } else {
                    phoneHasError = false;
                }
                errorLayout.setVisibility(View.GONE);
                applyInputState(container, label, InputState.FOCUSED, isFullName);
                return;
            }

            if (isFullName) {
                fullNameTouched = true;
                validateFullName(true);
            } else {
                phoneTouched = true;
                validatePhone(true);
            }
        });
    }

    private void attemptContinue() {
        fullNameTouched = true;
        phoneTouched = true;

        boolean fullNameValid = validateFullName(true);
        boolean phoneValid = validatePhone(true);
        if (!fullNameValid || !phoneValid) {
            return;
        }

        String fullName = getInputValue(fullNameEditText);
        String phone = PhoneNormalizer.normalize(getInputValue(phoneEditText));
        String currentUid = firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : "";

        setLoading(true);
        UserPhoneLookup.queryUsers(phone)
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot existing = query.getDocuments().get(0);
                        if (!currentUid.equals(existing.getId())) {
                            // Phone already belongs to another account → OTP then password link (option B).
                            String existingAuthEmail = existing.getString("email");
                            if (TextUtils.isEmpty(existingAuthEmail)) {
                                setLoading(false);
                                showPhoneError(getString(R.string.register_phone_exists));
                                return;
                            }
                            launchLinkExistingOtp(fullName, phone, existing.getId(), existingAuthEmail);
                            return;
                        }
                    }
                    launchSocialOtp(fullName, phone, false, null, null);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(getString(R.string.register_error_generic));
                });
    }

    private void launchLinkExistingOtp(
            String fullName,
            String phone,
            String existingUid,
            String existingAuthEmail
    ) {
        launchSocialOtp(fullName, phone, true, existingUid, existingAuthEmail);
    }

    private void launchSocialOtp(
            String fullName,
            String phone,
            boolean linkExisting,
            String existingUid,
            String existingAuthEmail
    ) {
        setLoading(false);
        Intent intent = new Intent(this, OTPActivity.class);
        intent.putExtra(OTPActivity.EXTRA_FULL_NAME, fullName);
        intent.putExtra(OTPActivity.EXTRA_PHONE, phone);
        intent.putExtra(OTPActivity.EXTRA_EMAIL, authEmail);
        intent.putExtra(OTPActivity.EXTRA_PASSWORD, "");
        intent.putExtra(OTPActivity.EXTRA_IS_SOCIAL_AUTH, true);
        intent.putExtra(OTPActivity.EXTRA_AUTH_PROVIDER, authProvider);
        intent.putExtra(OTPActivity.EXTRA_LINK_EXISTING_ACCOUNT, linkExisting);
        if (linkExisting) {
            intent.putExtra(OTPActivity.EXTRA_EXISTING_UID, existingUid);
            intent.putExtra(OTPActivity.EXTRA_EXISTING_AUTH_EMAIL, existingAuthEmail);
        }
        startActivity(intent);
        // Same as Register → OTP: don't leave incomplete profile under the back stack.
        finish();
    }

    private boolean validateFullName(boolean showError) {
        String fullName = getInputValue(fullNameEditText);
        String errorCode = RegisterValidator.validateFullName(fullName);

        if (errorCode == null) {
            if (showError || fullNameTouched) {
                clearFieldError(fullNameInputContainer, fullNameLabel, fullNameErrorLayout, fullNameEditText.hasFocus(), true);
                fullNameHasError = false;
            }
            return true;
        }

        if (!showError && !fullNameTouched) {
            return false;
        }

        showFullNameError(getString(R.string.register_full_name_required));
        return false;
    }

    private boolean validatePhone(boolean showError) {
        String phone = getInputValue(phoneEditText);
        String errorCode = RegisterValidator.validatePhone(phone);

        if (errorCode == null) {
            if (showError || phoneTouched) {
                clearFieldError(phoneInputContainer, phoneLabel, phoneErrorLayout, phoneEditText.hasFocus(), false);
                phoneHasError = false;
            }
            return true;
        }

        if (!showError && !phoneTouched) {
            return false;
        }

        String message;
        if ("length".equals(errorCode)) {
            message = getString(R.string.register_phone_length_error);
        } else if ("invalid".equals(errorCode)) {
            message = getString(R.string.register_phone_invalid_error);
        } else {
            message = getString(R.string.register_phone_required);
        }
        showPhoneError(message);
        return false;
    }

    private void showFullNameError(String message) {
        fullNameHasError = true;
        fullNameErrorText.setText(message);
        fullNameErrorLayout.setVisibility(View.VISIBLE);
        applyInputState(fullNameInputContainer, fullNameLabel, InputState.ERROR, true);
        shakeView(fullNameInputContainer);
    }

    private void showPhoneError(String message) {
        phoneHasError = true;
        phoneErrorText.setText(message);
        phoneErrorLayout.setVisibility(View.VISIBLE);
        applyInputState(phoneInputContainer, phoneLabel, InputState.ERROR, false);
        shakeView(phoneInputContainer);
    }

    private void clearFieldError(
            FrameLayout container,
            TextView label,
            LinearLayout errorLayout,
            boolean isFocused,
            boolean isFullName
    ) {
        errorLayout.setVisibility(View.GONE);
        applyInputState(container, label, isFocused ? InputState.FOCUSED : InputState.DEFAULT, isFullName);
    }

    private void applyInputState(FrameLayout container, TextView label, InputState state, boolean isFullName) {
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

        int currentBorderRes = isFullName ? fullNameBorderRes : phoneBorderRes;
        if (currentBorderRes != borderRes) {
            animateBorder(container, currentBorderRes, borderRes);
            if (isFullName) {
                fullNameBorderRes = borderRes;
            } else {
                phoneBorderRes = borderRes;
            }
        }

        label.setTextColor(ContextCompat.getColor(this, labelColor));
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

    private void updateContinueButtonState() {
        String fullName = getInputValue(fullNameEditText);
        String phone = getInputValue(phoneEditText);
        boolean enabled = RegisterValidator.validateFullName(fullName) == null
                && RegisterValidator.validatePhone(phone) == null
                && !isLoading();

        continueButton.setEnabled(enabled);
        if (enabled) {
            continueButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
            continueButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        } else {
            continueButton.setBackgroundResource(R.drawable.bg_button_disabled);
            continueButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_disabled));
        }
    }

    private boolean isLoading() {
        return loadingOverlay.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        fullNameEditText.setEnabled(!loading);
        phoneEditText.setEnabled(!loading);
        continueButton.setEnabled(!loading);
        if (!loading) {
            updateContinueButtonState();
        }
    }

    private String getInputValue(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.socialCompleteScrollView), message, Snackbar.LENGTH_LONG).show();
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
