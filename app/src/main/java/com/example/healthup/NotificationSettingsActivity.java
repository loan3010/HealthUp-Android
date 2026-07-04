package com.example.healthup;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityNotificationSettingsBinding;
import com.example.healthup.databinding.ItemSettingSwitchRowBinding;

public class NotificationSettingsActivity extends AppCompatActivity {
    private ActivityNotificationSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        setupRows();
    }

    private void setupPermissionRow(android.view.View root, String title) {
        ItemSettingSwitchRowBinding rowBinding = ItemSettingSwitchRowBinding.bind(root);
        rowBinding.tvTitle.setText(title);
        
        // Kiểm tra xem ứng dụng có được bật thông báo hệ thống không
        boolean areNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        rowBinding.switchItem.setChecked(areNotificationsEnabled);

        // Khi gạt nút, mở cài đặt thông báo của máy
        rowBinding.switchItem.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", getPackageName());
                intent.putExtra("app_uid", getApplicationInfo().uid);
            }
            startActivity(intent);
        });
    }
    
    private void setupRows() {
        setupPermissionRow(binding.rowNotification.getRoot(), "Thông báo");
        setupPermissionRow(binding.rowInApp.getRoot(), "Thông báo trong ứng dụng");
        setupPermissionRow(binding.rowEmail.getRoot(), "Thông báo email");
        setupPermissionRow(binding.rowSms.getRoot(), "SMS Notifications");
    }
}
