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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.healthup.auth.SocialAuthHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final int BORDER_ANIMATION_MS = 200;

    private enum InputState {
        DEFAULT,
        FOCUSED,
        ERROR
    }

    private TextView identifierLabel;
    private TextView passwordLabel;
    private FrameLayout identifierInputContainer;
    private FrameLayout passwordInputContainer;
    private LinearLayout identifierErrorLayout;
    private LinearLayout passwordErrorLayout;
    private TextView identifierErrorText;
    private TextView passwordErrorText;
    private EditText identifierEditText;
    private TextInputEditText passwordEditText;
    private MaterialButton loginButton;
    private ProgressBar loginProgressBar;
    private LinearLayout googleButton;
    private LinearLayout facebookButton;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private SocialAuthHelper socialAuthHelper;

    private boolean identifierTouched;
    private boolean passwordTouched;
    private boolean identifierHasError;
    private boolean passwordHasError;
    private int identifierBorderRes = R.drawable.bg_input_default;
    private int passwordBorderRes = R.drawable.bg_input_default;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        bindViews();
        setupSocialAuth();
        setupInputBehavior();
        setupActions();
        setupLegalLinks();
        updateLoginButtonState();
    }

    private void bindViews() {
        identifierLabel = findViewById(R.id.identifierLabel);
        passwordLabel = findViewById(R.id.passwordLabel);
        identifierInputContainer = findViewById(R.id.identifierInputContainer);
        passwordInputContainer = findViewById(R.id.passwordInputContainer);
        identifierErrorLayout = findViewById(R.id.identifierErrorLayout);
        passwordErrorLayout = findViewById(R.id.passwordErrorLayout);
        identifierErrorText = findViewById(R.id.identifierErrorText);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        identifierEditText = findViewById(R.id.identifierEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        loginProgressBar = findViewById(R.id.loginProgressBar);
        googleButton = findViewById(R.id.googleButton);
        facebookButton = findViewById(R.id.facebookButton);
    }

    private void setupSocialAuth() {
        socialAuthHelper = new SocialAuthHelper(this, new SocialAuthHelper.Listener() {
            @Override
            public void onLoadingChanged(boolean loading) {
                setLoading(loading);
            }

            @Override
            public void onError(@NonNull String message) {
                Snackbar.make(loginButton, message, Snackbar.LENGTH_LONG).show();
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

    private void setupActions() {
        TextView forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        TextView registerTextView = findViewById(R.id.registerTextView);
        LinearLayout googleButton = findViewById(R.id.googleButton);
        LinearLayout facebookButton = findViewById(R.id.facebookButton);

        loginButton.setOnClickListener(v -> attemptLogin());
        forgotPasswordTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        registerTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        googleButton.setOnClickListener(v -> socialAuthHelper.signInWithGoogle());
        facebookButton.setOnClickListener(v -> socialAuthHelper.signInWithFacebook());
    }

    private void setupLegalLinks() {
        View.OnClickListener openPolicy = v -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("navigate_to", "policy");
            startActivity(intent);
        };
        TextView termsLink = findViewById(R.id.termsLink);
        TextView privacyLink = findViewById(R.id.privacyLink);
        if (termsLink != null) {
            termsLink.setOnClickListener(openPolicy);
        }
        if (privacyLink != null) {
            privacyLink.setOnClickListener(openPolicy);
        }
    }

    private void setupInputBehavior() {
        setupField(
                identifierEditText,
                identifierInputContainer,
                identifierLabel,
                identifierErrorLayout,
                true
        );
        setupField(
                passwordEditText,
                passwordInputContainer,
                passwordLabel,
                passwordErrorLayout,
                false
        );

        identifierEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (identifierTouched) {
                    validateIdentifierField(true);
                }
                updateLoginButtonState();
            }
        });

        passwordEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (passwordTouched) {
                    validatePasswordField(true);
                }
                updateLoginButtonState();
            }
        });
    }

    private void setupField(
            EditText editText,
            FrameLayout container,
            TextView label,
            LinearLayout errorLayout,
            boolean isIdentifier
    ) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (isIdentifier) {
                    if (identifierTouched) {
                        validateIdentifierField(true);
                    } else {
                        applyInputState(container, label, InputState.FOCUSED, true);
                    }
                } else {
                    if (passwordTouched) {
                        validatePasswordField(true);
                    } else {
                        applyInputState(container, label, InputState.FOCUSED, false);
                    }
                }
                return;
            }

            if (isIdentifier) {
                identifierTouched = true;
                validateIdentifierField(true);
            } else {
                passwordTouched = true;
                validatePasswordField(true);
            }
        });
    }

    private void attemptLogin() {
        identifierTouched = true;
        passwordTouched = true;

        boolean identifierValid = validateIdentifierField(true);
        boolean passwordValid = validatePasswordField(true);
        if (!identifierValid || !passwordValid) {
            return;
        }

        String identifier = getInputValue(identifierEditText);
        String password = getInputValue(passwordEditText);

        if (LoginValidator.isEmailIdentifier(identifier)) {
            signInWithEmail(identifier, password);
            return;
        }

        if (LoginValidator.isPhoneIdentifier(identifier)) {
            signInWithPhone(identifier, password);
            return;
        }

        showIdentifierError(getString(R.string.login_identifier_invalid));
    }

    private boolean validateIdentifierField(boolean showError) {
        String identifier = getInputValue(identifierEditText);
        String errorCode = LoginValidator.validateIdentifier(identifier);

        if (errorCode == null) {
            if (showError || identifierTouched) {
                clearFieldError(
                        identifierInputContainer,
                        identifierLabel,
                        identifierErrorLayout,
                        identifierEditText.hasFocus(),
                        true
                );
                identifierHasError = false;
            }
            return true;
        }

        if (!showError && !identifierTouched) {
            return false;
        }

        String message = resolveIdentifierErrorMessage(errorCode);
        showIdentifierError(message);
        return false;
    }

    private boolean validatePasswordField(boolean showError) {
        String password = getInputValue(passwordEditText);
        String errorCode = LoginValidator.validatePassword(password);

        if (errorCode == null) {
            if (showError || passwordTouched) {
                clearFieldError(
                        passwordInputContainer,
                        passwordLabel,
                        passwordErrorLayout,
                        passwordEditText.hasFocus(),
                        false
                );
                passwordHasError = false;
            }
            return true;
        }

        if (!showError && !passwordTouched) {
            return false;
        }

        String message = resolvePasswordErrorMessage(errorCode);
        showPasswordError(message);
        return false;
    }

    private String resolveIdentifierErrorMessage(String errorCode) {
        switch (errorCode) {
            case "phone_length":
                return getString(R.string.login_phone_length_error);
            case "phone_invalid":
                return getString(R.string.login_phone_invalid_error);
            case "email_invalid":
                return getString(R.string.login_email_invalid_error);
            case "required":
            default:
                return getString(R.string.login_identifier_required);
        }
    }

    private String resolvePasswordErrorMessage(String errorCode) {
        if ("too_short".equals(errorCode)) {
            return getString(R.string.login_password_short);
        }
        return getString(R.string.login_password_required);
    }

    private void showIdentifierError(String message) {
        identifierHasError = true;
        identifierErrorText.setText(message);
        identifierErrorLayout.setVisibility(View.VISIBLE);
        applyInputState(identifierInputContainer, identifierLabel, InputState.ERROR, true);
        shakeView(identifierInputContainer);
        fadeInView(identifierErrorLayout);
    }

    private void showPasswordError(String message) {
        passwordHasError = true;
        passwordErrorText.setText(message);
        passwordErrorLayout.setVisibility(View.VISIBLE);
        applyInputState(passwordInputContainer, passwordLabel, InputState.ERROR, false);
        shakeView(passwordInputContainer);
        fadeInView(passwordErrorLayout);
    }

    private void clearFieldError(
            FrameLayout container,
            TextView label,
            LinearLayout errorLayout,
            boolean isFocused,
            boolean isIdentifier
    ) {
        errorLayout.setVisibility(View.GONE);
        applyInputState(
                container,
                label,
                isFocused ? InputState.FOCUSED : InputState.DEFAULT,
                isIdentifier
        );
    }

    private void applyInputState(
            FrameLayout container,
            TextView label,
            InputState state,
            boolean isIdentifier
    ) {
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

        int currentBorderRes = isIdentifier ? identifierBorderRes : passwordBorderRes;
        if (currentBorderRes != borderRes) {
            animateBorder(container, currentBorderRes, borderRes);
            if (isIdentifier) {
                identifierBorderRes = borderRes;
            } else {
                passwordBorderRes = borderRes;
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

    private void fadeInView(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(200).start();
    }

    private void updateLoginButtonState() {
        String identifier = getInputValue(identifierEditText);
        String password = getInputValue(passwordEditText);
        boolean enabled = LoginValidator.isFormValid(identifier, password) && !isLoading();

        loginButton.setEnabled(enabled);
        loginButton.setBackgroundResource(R.drawable.bg_login_button_enabled);
        loginButton.setTextColor(ContextCompat.getColor(this, R.color.button_text_on_primary));
        loginButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private boolean isLoading() {
        return loginProgressBar.getVisibility() == View.VISIBLE;
    }

    private void signInWithPhone(String phone, String password) {
        setLoading(true);
        firebaseFirestore.collection("users")
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        setLoading(false);
                        showIdentifierError(getString(R.string.login_phone_not_registered));
                        return;
                    }

                    String email = queryDocumentSnapshots.getDocuments().get(0).getString("email");
                    if (email == null || email.trim().isEmpty()) {
                        setLoading(false);
                        showPasswordError(getString(R.string.login_credentials_wrong));
                        return;
                    }

                    signInWithEmail(email, password);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showPasswordError(getString(R.string.login_failed_generic));
                });
    }

    private void signInWithEmail(String email, String password) {
        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    updateLoginButtonState();

                    if (task.isSuccessful()) {
                        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        openMainScreen();
                        return;
                    }

                    Exception exception = task.getException();
                    if (exception instanceof FirebaseAuthInvalidCredentialsException
                            || exception instanceof FirebaseAuthInvalidUserException) {
                        showPasswordError(getString(R.string.login_credentials_wrong));
                    } else {
                        showPasswordError(getString(R.string.login_failed_generic));
                    }
                });
    }

    private String getInputValue(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        loginProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        identifierEditText.setEnabled(!loading);
        passwordEditText.setEnabled(!loading);
        googleButton.setEnabled(!loading);
        facebookButton.setEnabled(!loading);
        updateLoginButtonState();
    }

    private void openMainScreen() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
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
