package com.example.healthup;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.healthup.databinding.ActivityPaymentInfoBinding;

public class PaymentInfoActivity extends AppCompatActivity {
    private ActivityPaymentInfoBinding binding;
    private boolean isAnyLinked = true; // Giả lập trạng thái đã liên kết như trong hình 2

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        updateUI();

        binding.btnBack.setOnClickListener(v -> finish());
        
        // Logic giả lập: Nhấn Liên kết để đổi trạng thái
        View.OnClickListener linkListener = v -> {
            isAnyLinked = true;
            updateUI();
        };
        
        binding.cardAddZalo.setOnClickListener(linkListener);
        binding.cardAddMoMo.setOnClickListener(linkListener);
        binding.cardAddBank.setOnClickListener(linkListener);
        binding.cardAddCard.setOnClickListener(linkListener);
    }

    private void updateUI() {
        if (isAnyLinked) {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.layoutLinked.setVisibility(View.VISIBLE);
            binding.cardAddZalo.setVisibility(View.GONE);
            binding.cardAddMoMo.setVisibility(View.GONE);
            binding.cardAddBank.setVisibility(View.VISIBLE);
            binding.cardAddCard.setVisibility(View.VISIBLE);
        } else {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.layoutLinked.setVisibility(View.GONE);
            binding.cardAddZalo.setVisibility(View.VISIBLE);
            binding.cardAddMoMo.setVisibility(View.VISIBLE);
            binding.cardAddBank.setVisibility(View.VISIBLE);
            binding.cardAddCard.setVisibility(View.VISIBLE);
        }
    }
}
