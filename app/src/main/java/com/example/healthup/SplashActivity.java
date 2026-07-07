package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logo);
        TextView tagline = findViewById(R.id.tagline);

        // Trạng thái ban đầu: Tất cả ẩn và ở dưới
        logo.setAlpha(0f);
        logo.setTranslationY(300f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);

        tagline.setAlpha(0f);

        // 1. Logo vươn lên và nở ra (Mầm cây vươn lên)
        logo.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(2000)
                .setInterpolator(new AnticipateOvershootInterpolator(1.2f))
                .start();

        // 2. Tagline hiện cuối cùng
        tagline.animate()
                .alpha(0.6f)
                .setDuration(1000)
                .setStartDelay(2000)
                .start();

        // Firebase seeding
        FirebaseManager.getInstance().seedProductsIfEmpty();

        // Chuyển màn hình sau khi hoàn tất (tổng cộng 5 giây cho thong thả)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 5000);
    }
}
