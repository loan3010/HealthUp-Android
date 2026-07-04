package com.group.healthup;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.group.healthup.databinding.ActivityLanguageSettingsBinding;

public class LanguageSettingsActivity extends AppCompatActivity {
    private ActivityLanguageSettingsBinding binding;
    private boolean isVi = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLanguageSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnVi.setOnClickListener(v -> {
            isVi = true;
            updateUI();
        });

        binding.btnEn.setOnClickListener(v -> {
            isVi = false;
            updateUI();
        });

        binding.btnConfirm.setOnClickListener(v -> finish());
    }

    private void updateUI() {
        binding.ivCheckVi.setVisibility(isVi ? View.VISIBLE : View.INVISIBLE);
        binding.ivCheckEn.setVisibility(isVi ? View.INVISIBLE : View.VISIBLE);
    }
}
