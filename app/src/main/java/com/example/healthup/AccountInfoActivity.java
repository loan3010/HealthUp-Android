package com.example.healthup;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.databinding.ActivityAccountInfoBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AccountInfoActivity extends BaseAppCompatActivity {
    private ActivityAccountInfoBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private boolean isShowPassword = false;

    private Uri selectedAvatarUri;
    private Uri cameraPhotoUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickAvatarLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedAvatarUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .circleCrop()
                            .into(binding.ivAvatar);
                    binding.ivAvatar.setImageTintList(null);
                    binding.ivAvatar.setPadding(0, 0, 0, 0);
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    selectedAvatarUri = cameraPhotoUri;
                    Glide.with(this)
                            .load(selectedAvatarUri)
                            .circleCrop()
                            .into(binding.ivAvatar);
                    binding.ivAvatar.setImageTintList(null);
                    binding.ivAvatar.setPadding(0, 0, 0, 0);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCameraIntent();
                } else {
                    Toast.makeText(this, "Bạn cần cấp quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        if (mAuth.getCurrentUser() == null) {
            setupLoginRequired();
            return;
        }

        userId = mAuth.getCurrentUser().getUid();
        loadUserInfo();

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

        binding.btnVerifyEmail.setOnClickListener(v -> openEmailVerification());

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

        // Set up avatar click listeners
        View.OnClickListener avatarPickerListener = v -> {
            showImageSourceDialog();
        };
        binding.containerAvatar.setOnClickListener(avatarPickerListener);
        binding.ivAvatar.setOnClickListener(avatarPickerListener);
        binding.ivEditAvatar.setOnClickListener(avatarPickerListener);
    }

    private void setupLoginRequired() {
        View layout = findViewById(R.id.layoutLoginRequired);
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
            layout.findViewById(R.id.btnLoginRequired).setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
            });
            layout.findViewById(R.id.btnLater).setOnClickListener(v -> finish());
        }
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
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            return;
        }
        userId = user.getUid();
        refreshUsername();
        refreshEmailField();
        refreshPhoneField();
    }

    private void refreshPhoneField() {
        if (userId == null) return;
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        binding.etPhone.setText(documentSnapshot.getString("phone"));
                    }
                });
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

    /** Prefer real displayEmail; never show synthetic phone@healthup.app as the user's email. */
    private void refreshEmailField() {
        if (userId == null) return;
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        updateEmailVerificationUi(null, null, false);
                        return;
                    }
                    String display = doc.getString("displayEmail");
                    String email = doc.getString("email");
                    String show = UserProfileBuilder.isRealEmail(display)
                            ? display
                            : (UserProfileBuilder.isRealEmail(email) ? email : null);
                    if (show != null) {
                        binding.etEmail.setText(show);
                    } else {
                        binding.etEmail.setText(getString(R.string.account_email_empty));
                    }
                    Boolean verified = doc.getBoolean("emailVerified");
                    updateEmailVerificationUi(show, display, verified != null && verified);
                });
    }

    private void updateEmailVerificationUi(@Nullable String shownEmail,
                                           @Nullable String displayEmail,
                                           boolean emailVerified) {
        String pendingEmail = UserProfileBuilder.isRealEmail(displayEmail)
                ? displayEmail
                : (UserProfileBuilder.isRealEmail(shownEmail) ? shownEmail : null);
        boolean needsVerify = pendingEmail != null && !emailVerified;
        binding.layoutEmailVerify.setVisibility(needsVerify ? View.VISIBLE : View.GONE);
    }

    private void openEmailVerification() {
        if (userId == null) return;
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String display = doc.getString("displayEmail");
                    String email = doc.getString("email");
                    String pending = UserProfileBuilder.isRealEmail(display)
                            ? display
                            : (UserProfileBuilder.isRealEmail(email) ? email : null);
                    if (pending == null) {
                        startActivity(new Intent(this, ChangeEmailActivity.class));
                        return;
                    }
                    Intent intent = new Intent(this, EmailVerificationPendingActivity.class);
                    intent.putExtra(EmailVerificationPendingActivity.EXTRA_EMAIL, pending);
                    intent.putExtra(EmailVerificationPendingActivity.EXTRA_RETURN_TO_ACCOUNT, true);
                    startActivity(intent);
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
                        binding.etPhone.setText(documentSnapshot.getString("phone"));
                        binding.etDob.setText(documentSnapshot.getString("dob"));
                        refreshEmailField();

                        String gender = documentSnapshot.getString("gender");
                        if ("Nam".equals(gender)) binding.rbMale.setChecked(true);
                        else if ("Nữ".equals(gender)) binding.rbFemale.setChecked(true);
                        else if ("Khác".equals(gender)) binding.rbOther.setChecked(true);

                        String avatarUrl = documentSnapshot.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(avatarUrl)
                                    .circleCrop()
                                    .into(binding.ivAvatar);
                            binding.ivAvatar.setImageTintList(null);
                            binding.ivAvatar.setPadding(0, 0, 0, 0);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi tải thông tin", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserInfo() {
        if (mAuth.getCurrentUser() == null || userId == null) {
            Toast.makeText(this, R.string.account_save_session_expired, Toast.LENGTH_LONG).show();
            return;
        }

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

        // Refresh token before write — avoids PERMISSION_DENIED after email-change flows.
        mAuth.getCurrentUser().getIdToken(true)
                .addOnCompleteListener(tokenTask -> {
                    if (selectedAvatarUri != null) {
                        uploadAvatarAndSave(updates);
                    } else {
                        updateFirestore(updates);
                    }
                });
    }

    private void uploadAvatarAndSave(Map<String, Object> updates) {
        // Tham khảo cách up ảnh từ ReturnRefundDetailActivity: 
        // Sử dụng FirebaseManager.uploadImage để nén và chuyển sang Base64 Data URI
        // giúp tránh lỗi Permission Denied từ Firebase Storage.
        FirebaseManager.getInstance().uploadImage(selectedAvatarUri)
                .addOnSuccessListener(downloadUri -> {
                    updates.put("avatarUrl", downloadUri.toString());
                    updateFirestore(updates);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateFirestore(Map<String, Object> updates) {
        if (mAuth.getCurrentUser() == null || userId == null) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, R.string.account_save_session_expired, Toast.LENGTH_LONG).show();
            return;
        }
        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UIUtils.showSuccessDialog(this, null);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.toLowerCase(Locale.ROOT).contains("permission")) {
                        Toast.makeText(this, R.string.account_save_session_expired, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Lỗi: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showImageSourceDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_image_source, null);
        dialog.setContentView(view);

        view.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });

        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            pickAvatarLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Lỗi tạo file ảnh: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
        if (photoFile != null) {
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(takePictureIntent);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return File.createTempFile("AVATAR_" + timeStamp + "_", ".jpg", getExternalFilesDir(null));
    }
}
