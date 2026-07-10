package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityLanguageSettingsBinding;
import com.example.healthup.util.LocaleHelper;

public class LanguageSettingsActivity extends AppCompatActivity {
    private ActivityLanguageSettingsBinding binding;
    private String selectedLanguage = "vi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLanguageSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        selectedLanguage = LocaleHelper.getLanguage(this);
        updateUI();

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnVi.setOnClickListener(v -> {
            selectedLanguage = "vi";
            updateUI();
        });

        binding.btnEn.setOnClickListener(v -> {
            selectedLanguage = "en";
            updateUI();
        });

        binding.btnConfirm.setOnClickListener(v -> {
            LocaleHelper.setLocale(this, selectedLanguage);
            
            // Thông báo và khởi động lại ứng dụng để áp dụng ngôn ngữ mới
            Toast.makeText(this, "Đã thay đổi ngôn ngữ thành " + 
                (selectedLanguage.equals("vi") ? "Tiếng Việt" : "English"), Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void updateUI() {
        boolean isVi = selectedLanguage.equals("vi");
        binding.ivCheckVi.setVisibility(isVi ? View.VISIBLE : View.INVISIBLE);
        binding.ivCheckEn.setVisibility(isVi ? View.INVISIBLE : View.VISIBLE);
    }
}
