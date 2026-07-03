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
        
        // Mặc định cho phép như trong hình bạn gửi
        rowBinding.switchItem.setChecked(true);
    }
    
    private void setupRows() {
        setupPermissionRow(binding.rowNotification.getRoot(), "Thông báo");
        setupPermissionRow(binding.rowInApp.getRoot(), "Thông báo trong ứng dụng");
        setupPermissionRow(binding.rowEmail.getRoot(), "Thông báo email");
        setupPermissionRow(binding.rowSms.getRoot(), "SMS Notifications");
    }
}
