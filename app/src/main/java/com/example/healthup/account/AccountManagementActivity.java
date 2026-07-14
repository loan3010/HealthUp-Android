package com.example.healthup.account;

import android.text.TextUtils;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.example.healthup.BaseAppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.example.healthup.auth.SocialAuthHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class AccountManagementActivity extends BaseAppCompatActivity implements SavedAccountAdapter.Listener {

    public static final String EXTRA_ADD_ACCOUNT = "extra_add_account";

    private FirebaseAuth firebaseAuth;
    private SavedAccountAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TextView tvAccountManagementHint;
    private FrameLayout loadingOverlay;
    private MaterialButton btnAddAccount;
    @Nullable
    private SocialAuthHelper socialAuthHelper;
    @Nullable
    private SavedAccount pendingSocialAccount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_management);

        firebaseAuth = FirebaseAuth.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbarAccountManagement);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rvSavedAccounts);
        tvEmpty = findViewById(R.id.tvEmptyAccounts);
        tvAccountManagementHint = findViewById(R.id.tvAccountManagementHint);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        btnAddAccount = findViewById(R.id.btnAddAccount);

        adapter = new SavedAccountAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        socialAuthHelper = new SocialAuthHelper(this, new SocialAuthHelper.Listener() {
            @Override
            public void onLoadingChanged(boolean loading) {
                setLoading(loading);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(AccountManagementActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });

        btnAddAccount.setOnClickListener(v -> openAddAccountLogin());

        syncCurrentSessionIfNeeded();
        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void syncCurrentSessionIfNeeded() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            return;
        }
        AccountSessionRecorder.fetchAndRecord(this, user.getUid(), null);
    }

    private void refreshList() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        String activeUid = current != null ? current.getUid() : "";
        List<SavedAccount> accounts = SavedAccountStore.getAll(this);
        adapter.submit(accounts, activeUid);
        boolean empty = accounts.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        boolean full = SavedAccountStore.isFull(this);
        btnAddAccount.setEnabled(!full);
        btnAddAccount.setVisibility(full ? View.GONE : View.VISIBLE);
        if (tvAccountManagementHint != null) {
            tvAccountManagementHint.setText(full
                    ? getString(R.string.account_management_hint_full, SavedAccountStore.MAX_ACCOUNTS)
                    : getString(R.string.account_management_hint));
        }
    }

    @Override
    public void onCardClick(@NonNull SavedAccount account) {
        if (!TextUtils.isEmpty(SavedAccountStore.getPassword(this, account.uid))) {
            startPasswordQuickLogin(account);
            return;
        }
        if (account.isGoogleSwitchable()) {
            quickLoginGoogle(account);
            return;
        }
        if (SavedAccount.PROVIDER_FACEBOOK.equals(account.authProvider)) {
            quickLoginFacebook(account);
            return;
        }
        resolveGoogleLinkedAndSwitch(account);
    }

    private void resolveGoogleLinkedAndSwitch(@NonNull SavedAccount account) {
        setLoading(true);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(account.uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && AccountSessionRecorder.isGoogleLinked(doc)) {
                        quickLoginGoogle(account);
                        return;
                    }
                    setLoading(false);
                    startPasswordQuickLogin(account);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    startPasswordQuickLogin(account);
                });
    }

    private void startPasswordQuickLogin(@NonNull SavedAccount account) {
        setLoading(true);
        AccountQuickLogin.signIn(this, account, new AccountQuickLogin.Callback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                AccountQuickLogin.openMainAfterSwitch(AccountManagementActivity.this);
            }

            @Override
            public void onWrongPassword() {
                setLoading(false);
                Toast.makeText(AccountManagementActivity.this,
                        R.string.account_management_wrong_password, Toast.LENGTH_LONG).show();
                openLoginForAccount(account);
            }

            @Override
            public void onNeedManualLogin(@NonNull String message) {
                setLoading(false);
                if (account.isGoogleSwitchable()) {
                    quickLoginGoogle(account);
                    return;
                }
                if ("social".equals(message)
                        && SavedAccount.PROVIDER_GOOGLE.equals(account.authProvider)) {
                    quickLoginGoogle(account);
                    return;
                }
                if ("social".equals(message)
                        && SavedAccount.PROVIDER_FACEBOOK.equals(account.authProvider)) {
                    quickLoginFacebook(account);
                    return;
                }
                openLoginForAccount(account);
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                Toast.makeText(AccountManagementActivity.this,
                        R.string.account_management_switch_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void quickLoginGoogle(@NonNull SavedAccount account) {
        pendingSocialAccount = account;
        setLoading(false);
        if (socialAuthHelper != null) {
            socialAuthHelper.signInWithGoogleForSwitch(account.email, account.uid);
        }
    }

    private void quickLoginFacebook(@NonNull SavedAccount account) {
        pendingSocialAccount = account;
        if (socialAuthHelper != null) {
            socialAuthHelper.signInWithFacebook();
        }
    }

    private void openAddAccountLogin() {
        if (SavedAccountStore.isFull(this)) {
            showAccountFullDialog();
            return;
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_ADD_ACCOUNT, true);
        startActivity(intent);
    }

    private void showAccountFullDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_management_full_title)
                .setMessage(R.string.account_management_full_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onRemoveClick(@NonNull SavedAccount account) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_management_remove_title)
                .setMessage(getString(R.string.account_management_remove_message, account.displayName))
                .setNegativeButton(R.string.account_management_cancel, null)
                .setPositiveButton(R.string.account_management_remove, (d, w) -> {
                    SavedAccountStore.remove(this, account.uid);
                    refreshList();
                    Toast.makeText(this, R.string.account_management_removed, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openLoginForAccount(@NonNull SavedAccount account) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_ADD_ACCOUNT, true);
        if (!account.phone.isEmpty()) {
            intent.putExtra(LoginActivity.EXTRA_PREFILL_IDENTIFIER, account.phone);
        } else if (!account.email.isEmpty()) {
            intent.putExtra(LoginActivity.EXTRA_PREFILL_IDENTIFIER, account.email);
        }
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (socialAuthHelper != null) {
            socialAuthHelper.onActivityResult(requestCode, resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
