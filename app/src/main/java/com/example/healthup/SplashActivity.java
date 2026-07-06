package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        FirebaseManager.getInstance().seedProductsIfEmpty();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> destination = FirebaseAuth.getInstance().getCurrentUser() == null
                    ? LoginActivity.class
                    : MainActivity.class;
            startActivity(new Intent(SplashActivity.this, destination));
            finish();
        }, 1500);
    }
}
