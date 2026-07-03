package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangeUsernameBinding;

public class ChangeUsernameActivity extends AppCompatActivity {
    private ActivityChangeUsernameBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangeUsernameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnClear.setOnClickListener(v -> binding.etNewUsername.setText(""));

        binding.btnSave.setOnClickListener(v -> {
            String newUsername = binding.etNewUsername.getText().toString().trim();
            
            // Giả lập logic kiểm tra
            if ("xuanmai123".equals(newUsername)) {
                binding.tvError.setVisibility(View.VISIBLE);
            } else if (!newUsername.isEmpty()) {
                binding.tvError.setVisibility(View.GONE);
                UIUtils.showSuccessDialog(this, this::finish);
            }
        });
    }
}
