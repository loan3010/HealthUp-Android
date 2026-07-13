package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.healthup.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class AdminForgotPasswordActivity extends AppCompatActivity {

    private AdminForgotPasswordViewModel viewModel;
    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private TextView tvGeneralError;
    private MaterialButton btnSendOtp;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_forgot_password);

        viewModel = new ViewModelProvider(this).get(AdminForgotPasswordViewModel.class);
        tilEmail = findViewById(R.id.tilAdminForgotEmail);
        etEmail = findViewById(R.id.etAdminForgotEmail);
        tvGeneralError = findViewById(R.id.tvAdminForgotGeneralError);
        btnSendOtp = findViewById(R.id.btnAdminSendOtp);
        progressBar = findViewById(R.id.progressAdminForgot);

        findViewById(R.id.backTextView).setOnClickListener(v -> finish());
        btnSendOtp.setOnClickListener(v -> {
            clearErrors();
            String email = etEmail.getText() != null ? etEmail.getText().toString() : "";
            viewModel.sendOtp(email);
        });

        etEmail.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                clearErrors();
            }
        });

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, state -> {
            if (state == null) return;
            boolean loading = state == AdminForgotPasswordViewModel.UiState.LOADING;
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnSendOtp.setEnabled(!loading);
            etEmail.setEnabled(!loading);
        });

        viewModel.getEmailError().observe(this, error -> {
            if (error == null) return;
            if ("not_registered".equals(error)) {
                tilEmail.setError(getString(R.string.admin_forgot_email_not_registered));
            } else {
                tilEmail.setError(getString(R.string.admin_forgot_email_invalid));
            }
        });

        viewModel.getGeneralError().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled() != null) {
                tvGeneralError.setText(R.string.forgot_password_error_generic);
                tvGeneralError.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getNavigateToOtpEvent().observe(this, event -> {
            if (event == null) return;
            AdminForgotPasswordViewModel.NavigatePayload payload = event.getContentIfNotHandled();
            if (payload == null || TextUtils.isEmpty(payload.adminUid)) return;
            Intent intent = new Intent(this, AdminForgotPasswordOtpActivity.class);
            intent.putExtra(AdminForgotPasswordOtpActivity.EXTRA_EMAIL, payload.email);
            intent.putExtra(AdminForgotPasswordOtpActivity.EXTRA_ADMIN_UID, payload.adminUid);
            startActivity(intent);
            finish();
            viewModel.resetState();
        });
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tvGeneralError.setVisibility(View.GONE);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
