package com.example.healthup.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.healthup.BaseAppCompatActivity;

import com.example.healthup.R;
import com.example.healthup.account.AccountSessionRecorder;
import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.data.repository.RegistrationRepository;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneNormalizer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * After OTP proves ownership of an existing phone account, ask for that account's
 * password then link the pending Google credential (brief option B).
 */
public class LinkGooglePasswordActivity extends BaseAppCompatActivity {

    public static final String EXTRA_PHONE = "extra_link_phone";
    public static final String EXTRA_AUTH_EMAIL = "extra_link_auth_email";
    public static final String EXTRA_EXISTING_UID = "extra_link_existing_uid";
    public static final String EXTRA_GOOGLE_EMAIL = "extra_link_google_email";

    private String phone;
    private String authEmail;
    private String existingUid;
    private String googleEmail;

    private EditText passwordEditText;
    private TextView passwordErrorText;
    private MaterialButton linkButton;
    private FrameLayout loadingOverlay;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private RegistrationRepository registrationRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_google_password);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        registrationRepository = new RegistrationRepository();

        phone = getIntent().getStringExtra(EXTRA_PHONE);
        authEmail = getIntent().getStringExtra(EXTRA_AUTH_EMAIL);
        existingUid = getIntent().getStringExtra(EXTRA_EXISTING_UID);
        googleEmail = getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL);
        if (TextUtils.isEmpty(googleEmail)) {
            googleEmail = PendingGoogleLink.getEmail();
        }

        if (TextUtils.isEmpty(authEmail) || TextUtils.isEmpty(existingUid) || !PendingGoogleLink.hasPending()) {
            Toast.makeText(this, R.string.social_link_session_invalid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        passwordEditText = findViewById(R.id.passwordEditText);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        linkButton = findViewById(R.id.linkButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        TextView subtitle = findViewById(R.id.subtitleText);
        subtitle.setText(getString(R.string.social_link_password_subtitle, phone));

        findViewById(R.id.backTextView).setOnClickListener(v -> abortIncompleteLink());
        linkButton.setOnClickListener(v -> attemptLink());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                abortIncompleteLink();
            }
        });
    }

    private void abortIncompleteLink() {
        setLoading(true);
        IncompleteSocialSessionCleaner.cleanup(() -> runOnUiThread(() -> {
            setLoading(false);
            finish();
        }));
    }

    private void attemptLink() {
        String password = passwordEditText.getText() == null
                ? ""
                : passwordEditText.getText().toString();
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            passwordErrorText.setVisibility(View.VISIBLE);
            passwordErrorText.setText(R.string.login_password_short);
            return;
        }
        passwordErrorText.setVisibility(View.GONE);

        String idToken = PendingGoogleLink.getIdToken();
        if (TextUtils.isEmpty(idToken)) {
            Toast.makeText(this, R.string.social_link_session_invalid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setLoading(true);
        // Delete the temporary Google-only Auth user first, then sign into the
        // existing password account and link the Google credential (Option B).
        String googleUidToDelete = firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : null;
        if (googleUidToDelete != null && googleUidToDelete.equals(existingUid)) {
            googleUidToDelete = null;
        }

        AuthCredential googleCredential = GoogleAuthProvider.getCredential(idToken, null);
        final String orphanGoogleUid = googleUidToDelete;

        Runnable signInAndLink = () -> firestore.collection("users").document(existingUid).get()
                .addOnSuccessListener(doc -> {
                    String authPassword = password;
                    if (AppPasswordHelper.isAppPasswordMode(doc)) {
                        if (!AppPasswordHelper.matchesUserPassword(
                                password, doc.getString(AppPasswordHelper.FIELD_PASSWORD_HASH))) {
                            setLoading(false);
                            passwordErrorText.setVisibility(View.VISIBLE);
                            passwordErrorText.setText(R.string.social_link_password_wrong);
                            return;
                        }
                        String phoneForSecret = !TextUtils.isEmpty(phone)
                                ? phone
                                : doc.getString("phone");
                        authPassword = AppPasswordHelper.authSecretForPhone(
                                PhoneNormalizer.normalize(phoneForSecret != null ? phoneForSecret : ""));
                    }
                    final String signInPassword = authPassword;
                    firebaseAuth.signInWithEmailAndPassword(authEmail, signInPassword)
                            .addOnSuccessListener(result -> {
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                if (user == null || !existingUid.equals(user.getUid())) {
                                    setLoading(false);
                                    showError(getString(R.string.social_link_password_wrong));
                                    return;
                                }
                                user.linkWithCredential(googleCredential)
                                        .addOnSuccessListener(linked -> finishLinkSuccess(user, orphanGoogleUid))
                                        .addOnFailureListener(e -> {
                                            if (e.getMessage() != null
                                                    && e.getMessage().toLowerCase().contains("already")) {
                                                finishLinkSuccess(user, orphanGoogleUid);
                                            } else {
                                                setLoading(false);
                                                showError(getString(R.string.social_link_failed));
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                    passwordErrorText.setVisibility(View.VISIBLE);
                                    passwordErrorText.setText(R.string.social_link_password_wrong);
                                } else {
                                    showError(getString(R.string.social_link_failed));
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getString(R.string.social_link_failed));
                });

        FirebaseUser googleUser = firebaseAuth.getCurrentUser();
        if (googleUser != null && orphanGoogleUid != null && orphanGoogleUid.equals(googleUser.getUid())) {
            googleUser.delete()
                    .addOnCompleteListener(deleteTask -> {
                        firebaseAuth.signOut();
                        signInAndLink.run();
                    });
        } else {
            firebaseAuth.signOut();
            signInAndLink.run();
        }
    }

    private void finishLinkSuccess(@NonNull FirebaseUser user, @Nullable String orphanGoogleUid) {
        String emailToStore = !TextUtils.isEmpty(googleEmail) ? googleEmail : user.getEmail();
        if (TextUtils.isEmpty(emailToStore)) {
            setLoading(false);
            showError(getString(R.string.social_link_failed));
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .update(UserProfileBuilder.buildGoogleLinkUpdates(emailToStore))
                .addOnSuccessListener(unused -> {
                    if (!TextUtils.isEmpty(phone)) {
                        registrationRepository.deleteOtpDoc(phone);
                    }
                    PendingGoogleLink.clear();
                    AccountSessionRecorder.fetchAndRecord(this, user.getUid(), null);
                    maybeDeleteOrphanProfile(orphanGoogleUid, () -> {
                        setLoading(false);
                        Toast.makeText(this, R.string.social_link_success, Toast.LENGTH_SHORT).show();
                        GuestCartManager.getInstance(this).mergeToFirestore(user.getUid(), () ->
                                runOnUiThread(() -> {
                                    startActivity(CheckoutIntentHelper.buildPostAuthMainIntent(this));
                                    finish();
                                }));
                    });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getString(R.string.social_link_failed));
                });
    }

    private void maybeDeleteOrphanProfile(@Nullable String orphanUid, @NonNull Runnable onDone) {
        if (TextUtils.isEmpty(orphanUid)) {
            onDone.run();
            return;
        }
        // Also drop the orphan from local account switcher (otherwise the same email
        // appears as multiple cards under different Auth UIDs).
        com.example.healthup.account.SavedAccountStore.remove(this, orphanUid);
        firestore.collection("users").document(orphanUid).delete()
                .addOnCompleteListener(task -> onDone.run());
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        linkButton.setEnabled(!loading);
        passwordEditText.setEnabled(!loading);
    }

    private void showError(String message) {
        Snackbar.make(findViewById(R.id.linkScrollView), message, Snackbar.LENGTH_LONG).show();
    }
}
