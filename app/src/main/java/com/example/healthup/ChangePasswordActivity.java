package com.example.healthup;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangePasswordBinding;

public class ChangePasswordActivity extends AppCompatActivity {
    private ActivityChangePasswordBinding binding;
    private boolean isShowCurrent = false;
    private boolean isShowNew = false;
    private boolean isShowConfirm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        setupPasswordVisibility();

        binding.btnSave.setOnClickListener(v -> {
            String current = binding.etCurrentPassword.getText().toString();
            String newPass = binding.etNewPassword.getText().toString();
            String confirm = binding.etConfirmPassword.getText().toString();

            boolean hasError = false;

            if (current.isEmpty()) {
                binding.tvErrorCurrent.setVisibility(View.VISIBLE);
                hasError = true;
            } else {
                binding.tvErrorCurrent.setVisibility(View.GONE);
            }

            if (newPass.length() < 8) {
                binding.tvErrorNew.setVisibility(View.VISIBLE);
                hasError = true;
            } else {
                binding.tvErrorNew.setVisibility(View.GONE);
            }

            if (!confirm.equals(newPass)) {
                binding.tvErrorConfirm.setVisibility(View.VISIBLE);
                hasError = true;
            } else {
                binding.tvErrorConfirm.setVisibility(View.GONE);
            }

            if (!hasError && !current.isEmpty()) {
                UIUtils.showSuccessDialog(this, this::finish);
            }
        });
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
