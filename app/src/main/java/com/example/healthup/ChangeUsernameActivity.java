package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityChangeUsernameBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeUsernameActivity extends AppCompatActivity {
    private ActivityChangeUsernameBinding binding;
    private FirebaseFirestore db;
    private String userId;

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
        
        binding.btnClear.setOnClickListener(v -> binding.etNewUsername.setText(""));

        binding.btnSave.setOnClickListener(v -> {
            String newUsername = binding.etNewUsername.getText().toString().trim();
            
            if (newUsername.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên người dùng", Toast.LENGTH_SHORT).show();
                return;
            }

            // Thực hiện update Firestore
            db.collection("users").document(userId)
                    .update("username", newUsername)
                    .addOnSuccessListener(aVoid -> {
                        UIUtils.showSuccessDialog(this, this::finish);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}
