package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();
            
            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_categories) {
                selectedFragment = new ProductListFragment();
            } else if (id == R.id.nav_cart) {
                selectedFragment = new CartFragment();
            } else if (id == R.id.nav_notifications) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
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
            // Mặc định load HomeFragment nếu không có yêu cầu điều hướng đặc biệt
            loadFragment(new HomeFragment());
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
}
