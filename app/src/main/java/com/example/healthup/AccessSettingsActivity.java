package com.example.healthup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.healthup.databinding.ActivityAccessSettingsBinding;
import com.example.healthup.databinding.ItemSettingSwitchRowBinding;

public class AccessSettingsActivity extends AppCompatActivity {
    private ActivityAccessSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccessSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        setupRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cập nhật lại trạng thái switch khi người dùng quay lại từ trang cài đặt máy
        setupRows();
    }

    private void setupRows() {
        setupPermissionRow(binding.rowLocation.getRoot(), "Cho phép truy cập Vị trí", Manifest.permission.ACCESS_FINE_LOCATION);
        setupPermissionRow(binding.rowCamera.getRoot(), "Cho phép truy cập Máy ảnh", Manifest.permission.CAMERA);
        setupPermissionRow(binding.rowLibrary.getRoot(), "Cho phép truy cập Thư viện", getStoragePermission());
        setupPermissionRow(binding.rowMicro.getRoot(), "Cho phép truy cập Micro", Manifest.permission.RECORD_AUDIO);
        
        // Thêm hàng thông báo nếu layout có hỗ trợ
        if (binding.getRoot().findViewById(R.id.rowNotification) != null) {
            setupPermissionRow(binding.rowNotification.getRoot(), "Cho phép gửi Thông báo", "NOTIFICATION");
        }
    }

    private String getStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    private void setupPermissionRow(android.view.View root, String title, String permission) {
        ItemSettingSwitchRowBinding rowBinding = ItemSettingSwitchRowBinding.bind(root);
        rowBinding.tvTitle.setText(title);

        // Kiểm tra quyền của hệ thống
        boolean isGranted = isPermissionGranted(permission);
        
        // Android không cho phép ứng dụng tự ý bật switch nếu chưa có quyền thực tế.
        // Chặn sự kiện gạt switch tự động, thay vào đó điều hướng người dùng.
        rowBinding.switchItem.setOnCheckedChangeListener(null);
        rowBinding.switchItem.setChecked(isGranted);

        rowBinding.switchItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Kiểm tra lại quyền thực tế
            boolean actualGranted = isPermissionGranted(permission);
            
            if (isChecked != actualGranted) {
                // Nếu người dùng cố tình gạt nút không khớp với thực tế hệ thống
                // Trả lại trạng thái cũ và mở cài đặt máy
                buttonView.setChecked(actualGranted);
                openAppSettings();
                Toast.makeText(this, "Vui lòng thay đổi quyền trong cài đặt hệ thống", Toast.LENGTH_SHORT).show();
            }
        });

        // Cho phép bấm vào cả dòng để mở cài đặt cho tiện
        root.setOnClickListener(v -> openAppSettings());
    }

    private boolean isPermissionGranted(String permission) {
        if (permission.equals("NOTIFICATION")) {
            return androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
}
