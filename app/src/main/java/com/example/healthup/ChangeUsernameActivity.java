package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.databinding.ActivityChangeUsernameBinding;
import com.example.healthup.util.UserUsernameLookup;
import com.example.healthup.util.UsernameValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;

public class ChangeUsernameActivity extends AppCompatActivity {
    private ActivityChangeUsernameBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private boolean isSaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeUsernameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnClear.setOnClickListener(v -> {
            binding.etNewUsername.setText("");
            hideError();
        });

        binding.etNewUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnSave.setOnClickListener(v -> saveUsername());
    }

    private void saveUsername() {
        if (isSaving || TextUtils.isEmpty(userId)) {
            if (TextUtils.isEmpty(userId)) {
                Toast.makeText(this, R.string.register_error_generic, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String rawUsername = binding.etNewUsername.getText().toString().trim();
        String validationError = UsernameValidator.validate(rawUsername);
        if (validationError != null) {
            showValidationError(validationError);
            return;
        }

        String normalizedUsername = UsernameValidator.normalize(rawUsername);
        isSaving = true;
        binding.btnSave.setEnabled(false);

        UserUsernameLookup.checkTakenByOther(normalizedUsername, userId, new UserUsernameLookup.Callback() {
            @Override
            public void onResult(boolean takenByOther) {
                if (takenByOther) {
                    runOnUiThread(() -> {
                        isSaving = false;
                        binding.btnSave.setEnabled(true);
                        binding.tvError.setText(R.string.username_taken_error);
                        binding.tvError.setVisibility(View.VISIBLE);
                    });
                    return;
                }

                db.collection("users").document(userId)
                        .set(Collections.singletonMap("username", normalizedUsername), SetOptions.merge())
                        .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                            isSaving = false;
                            binding.btnSave.setEnabled(true);
                            UIUtils.showSuccessDialog(ChangeUsernameActivity.this, ChangeUsernameActivity.this::finish);
                        }))
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            isSaving = false;
                            binding.btnSave.setEnabled(true);
                            Toast.makeText(
                                    ChangeUsernameActivity.this,
                                    getString(R.string.register_error_generic),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }));
            }

            @Override
            public void onError(@NonNull Exception error) {
                runOnUiThread(() -> {
                    isSaving = false;
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(
                            ChangeUsernameActivity.this,
                            getString(R.string.register_error_generic),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void showValidationError(@NonNull String errorKey) {
        if ("required".equals(errorKey)) {
            Toast.makeText(this, R.string.username_required_error, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.tvError.setText(R.string.username_invalid_format_error);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvError.setVisibility(View.GONE);
    }
}
