package com.example.healthup;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.healthup.account.AccountManagementActivity;
import com.example.healthup.account.AccountSessionRecorder;
import com.example.healthup.account.SavedAccountStore;
import com.example.healthup.admin.AdminLoginActivity;
import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.auth.SocialAuthHelper;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.util.AppEntryRouter;
import com.example.healthup.util.AccountDisabledWatcher;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserPhoneLookup;
import com.example.healthup.util.UserProfileResolver;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends BaseAppCompatActivity {

    public static final String EXTRA_ADD_ACCOUNT = "extra_add_account";
    public static final String EXTRA_PREFILL_IDENTIFIER = "extra_prefill_identifier";

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
    private TextView tvSavedAccounts;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private SocialAuthHelper socialAuthHelper;

    private boolean identifierTouched;
    private boolean passwordTouched;
    private boolean identifierHasError;
    private boolean passwordHasError;
    private int identifierBorderRes = R.drawable.bg_input_default;
    private int passwordBorderRes = R.drawable.bg_input_default;
    private boolean addingAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();
        addingAccount = getIntent().getBooleanExtra(EXTRA_ADD_ACCOUNT, false);

        bindViews();
        applyPrefillIdentifier();
        setupSocialAuth();
        setupInputBehavior();
        setupActions();
        setupLegalLinks();
        updateLoginButtonState();
        updateSavedAccountsLink();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSavedAccountsLink();
    }

    private void updateSavedAccountsLink() {
        if (tvSavedAccounts == null) {
            return;
        }
        boolean hasSavedAccounts = !SavedAccountStore.getAll(this).isEmpty();
        tvSavedAccounts.setVisibility(hasSavedAccounts ? View.VISIBLE : View.GONE);
    }

    private void applyPrefillIdentifier() {
        String prefillPhone = getIntent().getStringExtra(CheckoutIntentHelper.EXTRA_PREFILL_PHONE);
        if (!android.text.TextUtils.isEmpty(prefillPhone)) {
            identifierEditText.setText(PhoneNormalizer.normalize(prefillPhone));
        }
        String prefillIdentifier = getIntent().getStringExtra(EXTRA_PREFILL_IDENTIFIER);
        if (!TextUtils.isEmpty(prefillIdentifier)) {
            identifierEditText.setText(prefillIdentifier);
        }
        if (getIntent().getBooleanExtra(CheckoutIntentHelper.EXTRA_FOCUS_PASSWORD, false)) {
            passwordEditText.requestFocus();
        }
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
        tvSavedAccounts = findViewById(R.id.tvSavedAccounts);
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
        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> CheckoutIntentHelper.openMainHome(this));
        }
        forgotPasswordTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        registerTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        if (tvSavedAccounts != null) {
            tvSavedAccounts.setOnClickListener(v ->
                    startActivity(new Intent(LoginActivity.this, AccountManagementActivity.class)));
        }
        googleButton.setOnClickListener(v -> {
            if (shouldBlockSocialAddAccount()) {
                showAccountLimitDialog();
                return;
            }
            socialAuthHelper.signInWithGoogle();
        });
        facebookButton.setOnClickListener(v -> {
            if (shouldBlockSocialAddAccount()) {
                showAccountLimitDialog();
                return;
            }
            socialAuthHelper.signInWithFacebook();
        });

        TextView adminLoginLink = findViewById(R.id.tvAdminLoginLink);
        if (adminLoginLink != null) {
            adminLoginLink.setOnClickListener(v ->
                    startActivity(new Intent(LoginActivity.this, AdminLoginActivity.class)));
        }
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
        if (SavedAccountStore.isBlockedNewLogin(this, identifier)) {
            showAccountLimitDialog();
            return;
        }

        String password = getInputValue(passwordEditText);

        if (LoginValidator.isEmailIdentifier(identifier)) {
            signInWithEmailLookup(identifier, password);
            return;
        }

        if (LoginValidator.isPhoneIdentifier(identifier)) {
            signInWithPhone(PhoneNormalizer.normalize(identifier), password);
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
        UserPhoneLookup.queryUsers(phone)
                .addOnSuccessListener(queryDocumentSnapshots -> handlePhoneLoginResult(queryDocumentSnapshots, password))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showPasswordError(getString(R.string.login_failed_generic));
                });
    }

    private void signInWithEmailLookup(String email, String password) {
        setLoading(true);
        final String normalizedEmail = RegisterValidator.normalizeEmail(email);
        if (TextUtils.isEmpty(normalizedEmail)
                || UserProfileBuilder.isSyntheticAuthEmail(normalizedEmail)) {
            setLoading(false);
            showIdentifierError(getString(R.string.login_email_invalid_error));
            return;
        }

        firebaseFirestore.collection("users")
                .whereEqualTo("displayEmail", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(byDisplay -> {
                    if (!byDisplay.isEmpty()) {
                        DocumentSnapshot doc = byDisplay.getDocuments().get(0);
                        String authEmail = doc.getString("email");
                        if (TextUtils.isEmpty(authEmail)) {
                            authEmail = normalizedEmail;
                        }
                        signInWithEmail(authEmail, password, doc);
                        return;
                    }
                    firebaseFirestore.collection("users")
                            .whereEqualTo("email", normalizedEmail)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(byEmail -> {
                                if (!byEmail.isEmpty()) {
                                    DocumentSnapshot doc = byEmail.getDocuments().get(0);
                                    signInWithEmail(normalizedEmail, password, doc);
                                    return;
                                }
                                // No Firestore hit — still try Auth (may be legacy).
                                signInWithEmail(normalizedEmail, password, null);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showPasswordError(getString(R.string.login_failed_generic));
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showPasswordError(getString(R.string.login_failed_generic));
                });
    }

    private void handlePhoneLoginResult(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots, String password) {
        if (queryDocumentSnapshots.isEmpty()) {
            setLoading(false);
            showIdentifierError(getString(R.string.login_phone_not_registered));
            return;
        }

        DocumentSnapshot profileDoc = queryDocumentSnapshots.getDocuments().get(0);
        completeLoginWithProfile(profileDoc, password, false);
    }

    private void signInWithEmail(String email, String password, DocumentSnapshot profileDoc) {
        if (profileDoc != null) {
            // Email login requires Firestore emailVerified (mock verify).
            Boolean verified = profileDoc.getBoolean("emailVerified");
            if (verified == null || !verified) {
                setLoading(false);
                updateLoginButtonState();
                showIdentifierError(getString(R.string.login_email_not_verified));
                return;
            }
            completeLoginWithProfile(profileDoc, password, true);
            return;
        }
        // Legacy: no Firestore hit — try Auth with user password.
        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        updateLoginButtonState();
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthInvalidCredentialsException
                                || exception instanceof FirebaseAuthInvalidUserException) {
                            showPasswordError(getString(R.string.login_credentials_wrong));
                        } else {
                            showPasswordError(getString(R.string.login_failed_generic));
                        }
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showPasswordError(getString(R.string.login_failed_generic));
                        return;
                    }
                    setLoading(false);
                    updateLoginButtonState();
                    ensureProfileFromAuthEmail(user);
                    finishLoginAfterRecord(password);
                });
    }

    /**
     * App mode: verify Firestore hash, then Auth sign-in with derived phone secret.
     * Legacy: Auth sign-in with user password, then migrate to app mode.
     */
    private void completeLoginWithProfile(
            @NonNull DocumentSnapshot profileDoc,
            @NonNull String password,
            boolean emailIdentifier
    ) {
        String phone = profileDoc.getString("phone");
        String authEmail = AppPasswordHelper.authEmailFromProfile(profileDoc);
        if (TextUtils.isEmpty(authEmail)) {
            String displayEmail = profileDoc.getString("displayEmail");
            if (UserProfileBuilder.isRealEmail(displayEmail)) {
                authEmail = displayEmail;
            }
        }
        if (TextUtils.isEmpty(authEmail) || TextUtils.isEmpty(phone)) {
            setLoading(false);
            showPasswordError(getString(R.string.login_credentials_wrong));
            return;
        }

        String normalizedPhone = PhoneNormalizer.normalize(phone);
        if (AppPasswordHelper.isAppPasswordMode(profileDoc)) {
            if (!AppPasswordHelper.matchesUserPassword(
                    password, profileDoc.getString(AppPasswordHelper.FIELD_PASSWORD_HASH))) {
                setLoading(false);
                updateLoginButtonState();
                showPasswordError(getString(R.string.login_credentials_wrong));
                return;
            }
            String authSecret = AppPasswordHelper.authSecretForPhone(normalizedPhone);
            signInAuthAndFinish(authEmail, authSecret, password, normalizedPhone, profileDoc);
            return;
        }

        // Legacy Auth password == user password → migrate after success.
        final String legacyAuthEmail = authEmail;
        firebaseAuth.signInWithEmailAndPassword(legacyAuthEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()
                            && UserProfileBuilder.isRealEmail(profileDoc.getString("displayEmail"))
                            && !legacyAuthEmail.equals(profileDoc.getString("displayEmail"))) {
                        // Retry with displayEmail if Auth was updated previously.
                        firebaseAuth.signInWithEmailAndPassword(
                                        profileDoc.getString("displayEmail"), password)
                                .addOnCompleteListener(this, retry -> {
                                    if (!retry.isSuccessful()) {
                                        setLoading(false);
                                        updateLoginButtonState();
                                        showPasswordError(getString(R.string.login_credentials_wrong));
                                        return;
                                    }
                                    migrateLegacyPasswordThenFinish(password, normalizedPhone, profileDoc);
                                });
                        return;
                    }
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        updateLoginButtonState();
                        showPasswordError(getString(R.string.login_credentials_wrong));
                        return;
                    }
                    migrateLegacyPasswordThenFinish(password, normalizedPhone, profileDoc);
                });
    }

    private void migrateLegacyPasswordThenFinish(
            @NonNull String userPassword,
            @NonNull String normalizedPhone,
            @NonNull DocumentSnapshot profileDoc
    ) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            setLoading(false);
            showPasswordError(getString(R.string.login_failed_generic));
            return;
        }
        String authSecret = AppPasswordHelper.authSecretForPhone(normalizedPhone);
        user.updatePassword(authSecret)
                .addOnCompleteListener(updateTask -> {
                    firebaseFirestore.collection("users").document(profileDoc.getId())
                            .update(AppPasswordHelper.passwordFieldsForNewPassword(userPassword))
                            .addOnCompleteListener(ignored -> {
                                setLoading(false);
                                updateLoginButtonState();
                                UserProfileResolver.syncProfileAfterLogin(user.getUid(), profileDoc);
                                finishLoginAfterRecord(userPassword);
                            });
                });
    }

    private void signInAuthAndFinish(
            @NonNull String authEmail,
            @NonNull String authSecret,
            @NonNull String userPassword,
            @NonNull String normalizedPhone,
            @NonNull DocumentSnapshot profileDoc
    ) {
        firebaseAuth.signInWithEmailAndPassword(authEmail, authSecret)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        // Auth may still be legacy user-password — migrate then continue.
                        firebaseAuth.signInWithEmailAndPassword(authEmail, userPassword)
                                .addOnCompleteListener(this, legacyTask -> {
                                    if (!legacyTask.isSuccessful()) {
                                        setLoading(false);
                                        updateLoginButtonState();
                                        showPasswordError(getString(R.string.login_credentials_wrong));
                                        return;
                                    }
                                    migrateLegacyPasswordThenFinish(
                                            userPassword, normalizedPhone, profileDoc);
                                });
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showPasswordError(getString(R.string.login_failed_generic));
                        return;
                    }
                    setLoading(false);
                    updateLoginButtonState();
                    UserProfileResolver.syncProfileAfterLogin(user.getUid(), profileDoc);
                    finishLoginAfterRecord(userPassword);
                });
    }

    private void finishLoginAfterRecord(@Nullable String passwordForQuickLogin) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            return;
        }
        AccountSessionRecorder.fetchAndRecord(this, user.getUid(), passwordForQuickLogin,
                new AccountSessionRecorder.Listener() {
                    @Override
                    public void onRecorded() {
                        Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        openMainScreen();
                    }

                    @Override
                    public void onRejectedAccountLimit() {
                        firebaseAuth.signOut();
                        Toast.makeText(LoginActivity.this,
                                R.string.account_management_full_blocked, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean shouldBlockSocialAddAccount() {
        return addingAccount && SavedAccountStore.isFull(this);
    }

    private void showAccountLimitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_management_full_title)
                .setMessage(R.string.account_management_full_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void syncEmailVerifiedFlag(@NonNull FirebaseUser user, @NonNull String profileDocId) {
        // No-op: emailVerified is owned by Firestore (mock OTP).
    }

    private void ensureProfileFromAuthEmail(@NonNull FirebaseUser user) {
        String phone = UserProfileResolver.extractPhoneFromAuthEmail(user.getEmail());
        if (phone == null) {
            return;
        }
        UserPhoneLookup.queryUsers(phone)
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        UserProfileResolver.syncProfileAfterLogin(
                                user.getUid(),
                                snapshot.getDocuments().get(0)
                        );
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
        com.google.firebase.auth.FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        GuestCartManager.getInstance(this).mergeToFirestore(user.getUid(), () -> runOnUiThread(() ->
                AccountDisabledWatcher.checkBeforeEnterApp(LoginActivity.this, () ->
                        AppEntryRouter.navigateHomeAndFinish(LoginActivity.this))));
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
