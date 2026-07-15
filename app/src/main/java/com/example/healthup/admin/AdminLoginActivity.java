package com.example.healthup.admin;



import android.content.Intent;

import android.os.Bundle;

import android.text.Editable;

import android.text.TextUtils;

import android.text.TextWatcher;

import android.view.View;

import android.widget.ProgressBar;

import android.widget.TextView;



import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

import com.example.healthup.BaseAppCompatActivity;



import com.example.healthup.LoginActivity;

import com.example.healthup.R;

import com.example.healthup.auth.AppPasswordHelper;

import com.example.healthup.util.AdminEmailLookup;

import com.example.healthup.util.AppEntryRouter;

import com.example.healthup.util.StaffRoleHelper;

import com.google.android.material.button.MaterialButton;

import com.google.android.material.textfield.TextInputEditText;

import com.google.android.material.textfield.TextInputLayout;

import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentSnapshot;

import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.google.firebase.firestore.Source;



import java.util.HashMap;

import java.util.Map;



public class AdminLoginActivity extends BaseAppCompatActivity {



    private FirebaseAuth auth;

    private TextInputLayout tilEmail;

    private TextInputLayout tilPassword;

    private TextInputEditText etEmail;

    private TextInputEditText etPassword;

    private TextView tvGeneralError;

    private ProgressBar progressBar;



    @Override

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_admin_login);



        auth = FirebaseAuth.getInstance();

        tilEmail = findViewById(R.id.tilAdminEmail);

        tilPassword = findViewById(R.id.tilAdminPassword);

        etEmail = findViewById(R.id.etAdminEmail);

        etPassword = findViewById(R.id.etAdminPassword);

        tvGeneralError = findViewById(R.id.tvAdminLoginGeneralError);

        progressBar = findViewById(R.id.progressAdminLogin);

        MaterialButton btnLogin = findViewById(R.id.btnAdminLogin);

        TextView tvBack = findViewById(R.id.tvBackToUserLogin);

        TextView tvForgot = findViewById(R.id.tvAdminForgotPassword);



        btnLogin.setOnClickListener(v -> attemptLogin());

        tvBack.setOnClickListener(v -> {

            startActivity(new Intent(this, LoginActivity.class));

            finish();

        });

        if (tvForgot != null) {

            tvForgot.setOnClickListener(v ->

                    startActivity(new Intent(this, AdminForgotPasswordActivity.class)));

        }



        TextWatcher clearErrorsWatcher = new SimpleTextWatcher() {

            @Override

            public void afterTextChanged(Editable s) {

                clearFieldErrors();

            }

        };

        etEmail.addTextChangedListener(clearErrorsWatcher);

        etPassword.addTextChangedListener(clearErrorsWatcher);

    }



    private void attemptLogin() {

        clearFieldErrors();



        String email = etEmail.getText() != null ? AdminEmailLookup.normalize(etEmail.getText().toString()) : "";

        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";



        boolean valid = true;

        if (TextUtils.isEmpty(email)) {

            tilEmail.setError(getString(R.string.admin_login_required_email));

            valid = false;

        } else if (!AdminEmailLookup.isValidEmail(email)) {

            tilEmail.setError(getString(R.string.admin_forgot_email_invalid));

            valid = false;

        }

        if (TextUtils.isEmpty(password)) {

            tilPassword.setError(getString(R.string.admin_login_required_password));

            valid = false;

        }

        if (!valid) {

            return;

        }



        setLoading(true);

        final String loginEmail = email;

        final String loginPassword = password;

        AdminEmailLookup.findAdminByEmail(loginEmail)

                .addOnSuccessListener(adminDoc -> {

                    if (adminDoc == null) {

                        signInLegacy(loginEmail, loginPassword, null);

                        return;

                    }

                    loadFreshAdminProfile(adminDoc.getId(), loginEmail, loginPassword);

                })

                .addOnFailureListener(e -> {

                    setLoading(false);

                    showGeneralError(getString(R.string.admin_login_check_failed));

                });

    }



    private void loadFreshAdminProfile(

            @NonNull String adminUid,

            @NonNull String loginEmail,

            @NonNull String password

    ) {

        FirebaseFirestore.getInstance().collection("users").document(adminUid)

                .get(Source.SERVER)

                .addOnSuccessListener(freshDoc -> {

                    if (!freshDoc.exists()

                            || !StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(freshDoc))) {

                        setLoading(false);

                        showWrongCredentialsError();

                        return;

                    }

                    if (AppPasswordHelper.canUseAppPasswordLogin(freshDoc)) {

                        signInWithAppPassword(freshDoc, loginEmail, password);

                    } else {

                        signInLegacy(loginEmail, password, asQueryDoc(freshDoc));

                    }

                })

                .addOnFailureListener(e -> {

                    setLoading(false);

                    showGeneralError(getString(R.string.admin_login_check_failed));

                });

    }



    private void signInWithAppPassword(

            @NonNull DocumentSnapshot adminDoc,

            @NonNull String loginEmail,

            @NonNull String password

    ) {

        if (!AppPasswordHelper.matchesUserPassword(

                password, adminDoc.getString(AppPasswordHelper.FIELD_PASSWORD_HASH))) {

            setLoading(false);

            showWrongCredentialsError();

            return;

        }



        String syntheticAuthEmail = AppPasswordHelper.syntheticAuthEmailForAdmin(adminDoc.getId());

        String derivedSecret = AppPasswordHelper.authSecretForAdminEmail(syntheticAuthEmail);

        auth.signInWithEmailAndPassword(syntheticAuthEmail, derivedSecret)

                .addOnSuccessListener(result -> onAdminAuthSuccess(adminDoc, result.getUser()))

                .addOnFailureListener(e -> createSyntheticAdminAuthSession(adminDoc, syntheticAuthEmail, derivedSecret));

    }



    private void createSyntheticAdminAuthSession(

            @NonNull DocumentSnapshot adminDoc,

            @NonNull String syntheticAuthEmail,

            @NonNull String derivedSecret

    ) {

        auth.createUserWithEmailAndPassword(syntheticAuthEmail, derivedSecret)

                .addOnSuccessListener(result -> onAdminAuthSuccess(adminDoc, result.getUser()))

                .addOnFailureListener(e -> {

                    setLoading(false);

                    showGeneralError(getString(R.string.admin_login_auth_sync_required));

                });

    }



    private void onAdminAuthSuccess(

            @NonNull DocumentSnapshot adminDoc,

            @Nullable FirebaseUser user

    ) {

        if (user == null) {

            setLoading(false);

            showGeneralError(getString(R.string.admin_access_denied));

            return;

        }

        if (adminDoc.getId().equals(user.getUid())) {

            verifyAdminAndOpen(adminDoc.getId());

            return;

        }

        linkProfileSession(user.getUid(), adminDoc.getId(), () -> verifyAdminAndOpen(adminDoc.getId()));

    }



    private void linkProfileSession(

            @NonNull String authUid,

            @NonNull String profileDocId,

            @NonNull Runnable onSuccess

    ) {

        Map<String, Object> session = new HashMap<>();

        session.put("profileDocId", profileDocId);

        FirebaseFirestore.getInstance().collection("user_sessions").document(authUid)

                .set(session)

                .addOnSuccessListener(unused -> onSuccess.run())

                .addOnFailureListener(e -> {

                    setLoading(false);

                    showGeneralError(getString(R.string.admin_login_verify_failed));

                });

    }



    private void signInLegacy(

            @NonNull String email,

            @NonNull String password,

            @Nullable QueryDocumentSnapshot adminDoc

    ) {

        String authEmail = adminDoc != null

                ? AdminEmailLookup.resolveAuthEmail(adminDoc, email)

                : email;

        auth.signInWithEmailAndPassword(authEmail, password)

                .addOnSuccessListener(result -> {

                    FirebaseUser user = result.getUser();

                    if (user == null) {

                        setLoading(false);

                        showGeneralError(getString(R.string.admin_access_denied));

                        return;

                    }

                    if (adminDoc != null) {

                        migrateLegacyAdminIfNeeded(user, adminDoc, email, password);

                        onAdminAuthSuccess(adminDoc, user);

                    } else {

                        verifyAdminAndOpen(user.getUid());

                    }

                })

                .addOnFailureListener(e -> {

                    if (adminDoc != null && tryAlternateLegacyEmail(adminDoc, email, password)) {

                        return;

                    }

                    setLoading(false);

                    showWrongCredentialsError();

                });

    }



    private boolean tryAlternateLegacyEmail(

            @NonNull QueryDocumentSnapshot adminDoc,

            @NonNull String loginEmail,

            @NonNull String password

    ) {

        String primary = AdminEmailLookup.resolveAuthEmail(adminDoc, loginEmail);

        String alternate = resolveAlternateAuthEmail(adminDoc, primary);

        if (alternate == null) {

            return false;

        }

        auth.signInWithEmailAndPassword(alternate, password)

                .addOnSuccessListener(result -> {

                    FirebaseUser user = result.getUser();

                    if (user == null) {

                        setLoading(false);

                        showGeneralError(getString(R.string.admin_access_denied));

                        return;

                    }

                    migrateLegacyAdminIfNeeded(user, adminDoc, loginEmail, password);

                    onAdminAuthSuccess(adminDoc, user);

                })

                .addOnFailureListener(e -> {

                    setLoading(false);

                    showWrongCredentialsError();

                });

        return true;

    }



    @Nullable

    private String resolveAlternateAuthEmail(@NonNull DocumentSnapshot doc, @NonNull String primary) {

        String email = doc.getString("email");

        String displayEmail = doc.getString("displayEmail");

        if (!TextUtils.isEmpty(email)) {

            String normalized = AdminEmailLookup.normalize(email);

            if (!normalized.equals(primary)) {

                return normalized;

            }

        }

        if (!TextUtils.isEmpty(displayEmail)) {

            String normalized = AdminEmailLookup.normalize(displayEmail);

            if (!normalized.equals(primary)) {

                return normalized;

            }

        }

        return null;

    }



    private void migrateLegacyAdminIfNeeded(

            @NonNull FirebaseUser user,

            @NonNull QueryDocumentSnapshot adminDoc,

            @NonNull String email,

            @NonNull String password

    ) {

        if (AppPasswordHelper.canUseAppPasswordLogin(adminDoc)) {

            return;

        }

        Map<String, Object> updates = AppPasswordHelper.passwordFieldsForNewPassword(password);

        FirebaseFirestore.getInstance().collection("users").document(adminDoc.getId()).update(updates);

        String syntheticAuthEmail = AppPasswordHelper.syntheticAuthEmailForAdmin(adminDoc.getId());

        user.updatePassword(AppPasswordHelper.authSecretForAdminEmail(syntheticAuthEmail));

    }



    private void verifyAdminAndOpen(String uid) {

        FirebaseFirestore.getInstance().collection("users").document(uid)

                .get(Source.SERVER)

                .addOnSuccessListener(doc -> {

                    setLoading(false);

                    if (StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(doc))) {

                        AppEntryRouter.navigateAdminHomeAndFinish(this);

                    } else {

                        auth.signOut();

                        showGeneralError(getString(R.string.admin_access_denied));

                    }

                })

                .addOnFailureListener(e -> {

                    setLoading(false);

                    auth.signOut();

                    showGeneralError(getString(R.string.admin_login_verify_failed));

                });

    }



    @Nullable

    private QueryDocumentSnapshot asQueryDoc(@NonNull DocumentSnapshot doc) {

        return doc instanceof QueryDocumentSnapshot ? (QueryDocumentSnapshot) doc : null;

    }



    private void showWrongCredentialsError() {

        String message = getString(R.string.admin_login_wrong_credentials);

        tilEmail.setError(message);

        tilPassword.setError(message);

    }



    private void showGeneralError(@NonNull String message) {

        tvGeneralError.setText(message);

        tvGeneralError.setVisibility(View.VISIBLE);

    }



    private void clearFieldErrors() {

        tilEmail.setError(null);

        tilPassword.setError(null);

        tvGeneralError.setVisibility(View.GONE);

    }



    private void setLoading(boolean loading) {

        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);

        etEmail.setEnabled(!loading);

        etPassword.setEnabled(!loading);

    }



    private abstract static class SimpleTextWatcher implements TextWatcher {

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    }

}


