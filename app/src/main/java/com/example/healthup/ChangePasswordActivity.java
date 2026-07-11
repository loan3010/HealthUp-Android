package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.databinding.ActivityChangePasswordBinding;
import com.example.healthup.util.PhoneNormalizer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangePasswordActivity extends AppCompatActivity {
    private ActivityChangePasswordBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private boolean isShowCurrent = false;
    private boolean isShowNew = false;
    private boolean isShowConfirm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnForgotPassword.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(this, ForgotPasswordActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        setupPasswordVisibility();
        binding.btnSave.setOnClickListener(v -> attemptChangePassword());
    }

    private void attemptChangePassword() {
        String current = getFieldValue(binding.etCurrentPassword);
        String newPass = getFieldValue(binding.etNewPassword);
        String confirm = getFieldValue(binding.etConfirmPassword);

        boolean hasError = false;

        if (current.isEmpty()) {
            binding.tvErrorCurrent.setVisibility(View.VISIBLE);
            hasError = true;
        } else {
            binding.tvErrorCurrent.setVisibility(View.GONE);
        }

        String passwordError = RegisterValidator.validatePassword(newPass);
        if (passwordError != null) {
            binding.tvErrorNew.setVisibility(View.VISIBLE);
            hasError = true;
        } else {
            binding.tvErrorNew.setVisibility(View.GONE);
        }

        String confirmError = RegisterValidator.validateConfirmPassword(newPass, confirm);
        if (confirmError != null) {
            binding.tvErrorConfirm.setVisibility(View.VISIBLE);
            hasError = true;
        } else {
            binding.tvErrorConfirm.setVisibility(View.GONE);
        }

        if (hasError) {
            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        firestore.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        setLoading(false);
                        Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (AppPasswordHelper.isAppPasswordMode(doc)) {
                        if (!AppPasswordHelper.matchesUserPassword(
                                current, doc.getString(AppPasswordHelper.FIELD_PASSWORD_HASH))) {
                            setLoading(false);
                            binding.tvErrorCurrent.setVisibility(View.VISIBLE);
                            Toast.makeText(this, R.string.login_credentials_wrong, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        writeNewPasswordHash(user.getUid(), newPass);
                        return;
                    }

                    // Legacy: verify via Auth reauthenticate, then migrate to app hash.
                    String email = user.getEmail();
                    if (TextUtils.isEmpty(email)) {
                        setLoading(false);
                        Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    user.reauthenticate(
                                    com.google.firebase.auth.EmailAuthProvider.getCredential(email, current))
                            .addOnSuccessListener(unused -> {
                                String phone = doc.getString("phone");
                                String normalized = PhoneNormalizer.normalize(phone != null ? phone : "");
                                if (!normalized.isEmpty()) {
                                    user.updatePassword(AppPasswordHelper.authSecretForPhone(normalized));
                                }
                                writeNewPasswordHash(user.getUid(), newPass);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                binding.tvErrorCurrent.setVisibility(View.VISIBLE);
                                Toast.makeText(this, R.string.login_credentials_wrong, Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                });
    }

    private void writeNewPasswordHash(@NonNull String uid, @NonNull String newPass) {
        firestore.collection("users").document(uid)
                .update(AppPasswordHelper.passwordFieldsForNewPassword(newPass))
                .addOnSuccessListener(done -> {
                    setLoading(false);
                    UIUtils.showSuccessDialog(this, this::finish);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.reset_password_error_generic, Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.btnSave.setEnabled(!loading);
        binding.btnForgotPassword.setEnabled(!loading);
        binding.etCurrentPassword.setEnabled(!loading);
        binding.etNewPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);
    }

    private String getFieldValue(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private void setupPasswordVisibility() {
        binding.ivShowCurrent.setOnClickListener(v -> {
            isShowCurrent = !isShowCurrent;
            togglePassword(binding.etCurrentPassword, binding.ivShowCurrent, isShowCurrent);
        });
        binding.ivShowNew.setOnClickListener(v -> {
            isShowNew = !isShowNew;
            togglePassword(binding.etNewPassword, binding.ivShowNew, isShowNew);
        });
        binding.ivShowConfirm.setOnClickListener(v -> {
            isShowConfirm = !isShowConfirm;
            togglePassword(binding.etConfirmPassword, binding.ivShowConfirm, isShowConfirm);
        });
    }

    private void togglePassword(EditText editText, ImageView imageView, boolean show) {
        if (show) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            imageView.setImageResource(R.drawable.ic_eye_show);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            imageView.setImageResource(R.drawable.ic_eye_hide);
        }
        editText.setSelection(editText.getText().length());
    }
}
