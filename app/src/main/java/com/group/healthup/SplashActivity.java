package com.group.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Nạp dữ liệu Firebase nếu trống
        FirebaseManager.getInstance().seedProductsIfEmpty();
        FirebaseManager.getInstance().seedOrdersIfEmpty();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Vào thẳng trang Home
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 1500);
    }
}
