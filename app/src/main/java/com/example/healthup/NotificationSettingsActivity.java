package com.example.healthup;

import android.os.Bundle;
import com.example.healthup.databinding.ActivityNotificationSettingsBinding;
import com.example.healthup.databinding.ItemSettingSwitchRowBinding;

public class NotificationSettingsActivity extends BaseAppCompatActivity {
    private ActivityNotificationSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        setupRows();
    }

    private void setupRows() {
        // Nút Cha: Thông báo hệ thống
        ItemSettingSwitchRowBinding parentBinding = ItemSettingSwitchRowBinding.bind(binding.rowNotification.getRoot());
        parentBinding.tvTitle.setText("Thông báo hệ thống");
        
        boolean isSystemEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        parentBinding.switchItem.setChecked(isSystemEnabled);
        
        // Khi bấm vào nút cha hoặc cả hàng cha
        parentBinding.switchItem.setOnClickListener(v -> openSystemNotificationSettings());
        binding.rowNotification.getRoot().setOnClickListener(v -> openSystemNotificationSettings());

        // Các nút con
        setupChildRow(binding.rowInApp.getRoot(), "Thông báo trong ứng dụng", isSystemEnabled);
        setupChildRow(binding.rowEmail.getRoot(), "Thông báo email", isSystemEnabled);
        setupChildRow(binding.rowSms.getRoot(), "Thông báo SMS", isSystemEnabled);
    }

    private void setupChildRow(android.view.View root, String title, boolean isParentEnabled) {
        ItemSettingSwitchRowBinding rowBinding = ItemSettingSwitchRowBinding.bind(root);
        rowBinding.tvTitle.setText(title);
        
        // Nếu cha tắt thì con phải tắt và không cho bấm
        rowBinding.switchItem.setEnabled(isParentEnabled);
        if (!isParentEnabled) {
            rowBinding.switchItem.setChecked(false);
            rowBinding.tvTitle.setAlpha(0.5f);
        } else {
            rowBinding.tvTitle.setAlpha(1.0f);
            // Giả lập trạng thái đã lưu (mặc định bật khi cha bật)
            rowBinding.switchItem.setChecked(true);
        }
    }

    private void openSystemNotificationSettings() {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh lại trạng thái khi quay từ cài đặt máy về
        setupRows();
    }
}
