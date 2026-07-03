package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("navigate_to")) {
            String target = intent.getStringExtra("navigate_to");
            OrderHistoryFragment fragment = new OrderHistoryFragment();
            Bundle args = new Bundle();
            
            if ("returned_tab".equals(target)) {
                args.putInt("initial_tab", 5); // Tab Trả hàng
            } else if ("cancelled_tab".equals(target)) {
                args.putInt("initial_tab", 6); // Tab Đã hủy
            } else if ("delivered_tab".equals(target)) {
                args.putInt("initial_tab", 4); // Tab Đã giao
            }

            fragment.setArguments(args);
            loadFragment(fragment);
        } else {
            // Load OrderHistoryFragment by default for testing
            loadFragment(new OrderHistoryFragment());
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
}
