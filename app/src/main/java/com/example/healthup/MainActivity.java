package com.example.healthup;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        
        // Mặc định load HomeFragment
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

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
                // Bạn có thể tạo NotificationsFragment sau, tạm thời dùng Home
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
    }
}
