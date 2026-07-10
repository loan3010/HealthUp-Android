package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public class AdminLoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        auth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etAdminEmail);
        etPassword = findViewById(R.id.etAdminPassword);
        progressBar = findViewById(R.id.progressAdminLogin);
        MaterialButton btnLogin = findViewById(R.id.btnAdminLogin);
        TextView tvBack = findViewById(R.id.tvBackToUserLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        tvBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() == null) {
                        setLoading(false);
                        Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyAdminAndOpen(result.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "Đăng nhập thất bại", Toast.LENGTH_SHORT).show();
                });
    }

    private void verifyAdminAndOpen(String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    if (com.example.healthup.util.StaffRoleHelper.isAdmin(com.example.healthup.util.StaffRoleHelper.resolveRole(doc))) {
                        startActivity(new Intent(this, AdminActivity.class));
                        finish();
                    } else {
                        auth.signOut();
                        Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    auth.signOut();
                    Toast.makeText(this, "Không thể xác thực quyền admin", Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }
}
