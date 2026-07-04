package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (BuildConfig.DEBUG) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
                return;
            }

            Class<?> destination = FirebaseAuth.getInstance().getCurrentUser() == null
                    ? LoginActivity.class
                    : MainActivity.class;
            startActivity(new Intent(SplashActivity.this, destination));
            finish();
        }, 1200);
    }
}
