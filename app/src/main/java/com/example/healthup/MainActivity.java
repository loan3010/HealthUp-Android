package com.example.healthup;


import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class MainActivity extends AppCompatActivity {


    private BottomNavigationView navView;
    private View rootLayout;
    private boolean isKeyboardShowing = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.bottom_navigation);

        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_category) {
                loadFragment(new ProductListFragment());
                return true;
            } else if (id == R.id.nav_cart) {
                loadFragment(new CartFragment());
                return true;
            } else if (id == R.id.nav_notifications) {
                // loadFragment(new NotificationsFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });


        // FIX: tự ẩn bottom nav khi bàn phím hiện lên, hiện lại khi bàn phím tắt
        setupKeyboardVisibilityListener();


        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }


    private void setupKeyboardVisibilityListener() {
        rootLayout = findViewById(android.R.id.content);
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootLayout.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootLayout.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;


            // Nếu bàn phím chiếm hơn ~15% chiều cao màn hình -> coi như đang mở
            boolean keyboardNowShowing = keypadHeight > screenHeight * 0.15;


            if (keyboardNowShowing != isKeyboardShowing) {
                isKeyboardShowing = keyboardNowShowing;
                navView.setVisibility(isKeyboardShowing ? View.GONE : View.VISIBLE);
            }
        });
    }


    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }


    private void handleIntent(android.content.Intent intent) {
        if (intent != null && intent.hasExtra("navigate_to")) {
            String target = intent.getStringExtra("navigate_to");
            OrderHistoryFragment fragment = new OrderHistoryFragment();
            Bundle args = new Bundle();

            if ("returned_tab".equals(target)) {
                args.putInt("initial_tab", 5);
            } else if ("cancelled_tab".equals(target)) {
                args.putInt("initial_tab", 6);
            } else if ("delivered_tab".equals(target)) {
                args.putInt("initial_tab", 4);
            }


            fragment.setArguments(args);
            loadFragment(fragment);
        } else {
            loadFragment(new HomeFragment());
        }
    }


    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}