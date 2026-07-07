package com.example.healthup;

import android.app.DatePickerDialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityAccountInfoBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AccountInfoActivity extends AppCompatActivity {
    private ActivityAccountInfoBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private boolean isShowPassword = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
            loadUserInfo();
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> {
            UIUtils.hideKeyboard(this);
            saveUserInfo();
        });

        setupKeyboardHandling();
        
        // Date Picker for DOB
        View.OnClickListener dobListener = v -> showDatePicker();
        binding.containerDob.setOnClickListener(dobListener);
        binding.ivEditDob.setOnClickListener(dobListener);
        
        // --- CÁC SỰ KIỆN CLICK MỞ MÀN HÌNH CHỈNH SỬA ---
        
        // Chỉnh sửa Tên người dùng
        View.OnClickListener changeUsernameListener = v -> {
            startActivity(new android.content.Intent(this, ChangeUsernameActivity.class));
        };
        binding.containerUsername.setOnClickListener(changeUsernameListener);
        binding.ivEditUsername.setOnClickListener(changeUsernameListener);

        // Chỉnh sửa Email
        View.OnClickListener changeEmailListener = v -> {
            startActivity(new android.content.Intent(this, ChangeEmailActivity.class));
        };
        binding.containerEmail.setOnClickListener(changeEmailListener);
        binding.ivEditEmail.setOnClickListener(changeEmailListener);

        // Chỉnh sửa Số điện thoại
        View.OnClickListener changePhoneListener = v -> {
            startActivity(new android.content.Intent(this, ChangePhoneActivity.class));
        };
        binding.containerPhone.setOnClickListener(changePhoneListener);
        binding.ivEditPhone.setOnClickListener(changePhoneListener);

        // Chỉnh sửa Mật khẩu
        View.OnClickListener changePasswordListener = v -> {
            startActivity(new android.content.Intent(this, ChangePasswordActivity.class));
        };
        binding.containerPassword.setOnClickListener(changePasswordListener);
        binding.ivEditPassword.setOnClickListener(changePasswordListener);

        // Toggle Password Visibility (Icon con mắt)
        binding.ivShowPasswordAccount.setOnClickListener(v -> {
            isShowPassword = !isShowPassword;
            if (isShowPassword) {
                binding.etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                binding.ivShowPasswordAccount.setImageResource(R.drawable.ic_eye_show);
            } else {
                binding.etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                binding.ivShowPasswordAccount.setImageResource(R.drawable.ic_eye_hide);
            }
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });
    }

    private void setupKeyboardHandling() {
        binding.etFullName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                UIUtils.hideKeyboard(this);
                return true;
            }
            return false;
        });

        binding.etFullName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.scrollContent.post(() ->
                        binding.scrollContent.smoothScrollTo(0, v.getBottom()));
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                Rect rect = new Rect();
                focused.getGlobalVisibleRect(rect);
                if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    focused.clearFocus();
                    UIUtils.hideKeyboard(this);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = String.format("%02d/%02d/%d", dayOfMonth, monthOfYear + 1, year1);
                    binding.etDob.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsername();
    }

    private void refreshUsername() {
        if (userId == null) return;
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        binding.etUsername.setText(documentSnapshot.getString("username"));
                    }
                });
    }

    private void loadUserInfo() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        binding.etFullName.setText(documentSnapshot.getString("fullName"));
                        binding.etUsername.setText(documentSnapshot.getString("username"));
                        binding.etEmail.setText(documentSnapshot.getString("email"));
                        binding.etPhone.setText(documentSnapshot.getString("phone"));
                        binding.etDob.setText(documentSnapshot.getString("dob"));
                        
                        String gender = documentSnapshot.getString("gender");
                        if ("Nam".equals(gender)) binding.rbMale.setChecked(true);
                        else if ("Nữ".equals(gender)) binding.rbFemale.setChecked(true);
                        else if ("Khác".equals(gender)) binding.rbOther.setChecked(true);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi tải thông tin", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserInfo() {
        String fullName = binding.etFullName.getText().toString().trim();
        String dob = binding.etDob.getText().toString().trim();
        String gender = "";
        if (binding.rbMale.isChecked()) gender = "Nam";
        else if (binding.rbFemale.isChecked()) gender = "Nữ";
        else if (binding.rbOther.isChecked()) gender = "Khác";

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập họ tên", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("gender", gender);
        updates.put("dob", dob);

        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UIUtils.showSuccessDialog(this, null);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
