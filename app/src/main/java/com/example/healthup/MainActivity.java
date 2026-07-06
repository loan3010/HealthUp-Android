package com.example.healthup;


import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.healthup.ui.notify.NotifyPermissionDialogFragment;
import com.example.healthup.util.NotificationPermissionHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            Toast.makeText(this, R.string.notify_permission_granted, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
            );

    private BottomNavigationView navView;
    private View rootLayout;
    private boolean isKeyboardShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.bottom_navigation);

        FloatingActionButton fabChat = findViewById(R.id.fabChat);
        if (fabChat != null) {
            fabChat.setOnClickListener(v ->
                    startActivity(ChatActivity.buyerIntent(MainActivity.this)));
        }

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
                loadFragment(new NotificationsFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });

        setupKeyboardVisibilityListener();
        maybeShowNotificationPermissionDialog();

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    private void maybeShowNotificationPermissionDialog() {
        if (!NotificationPermissionHelper.shouldShowPrompt(this)) {
            return;
        }

        NotifyPermissionDialogFragment.show(
                getSupportFragmentManager(),
                new NotifyPermissionDialogFragment.Listener() {
                    @Override
                    public void onAllow() {
                        NotificationPermissionHelper.request(notificationPermissionLauncher);
                    }

                    @Override
                    public void onDecline() {
                        NotificationPermissionHelper.markDeclined(MainActivity.this);
                    }
                }
        );
    }

    private void setupKeyboardVisibilityListener() {
        rootLayout = findViewById(android.R.id.content);
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootLayout.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootLayout.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            boolean keyboardNowShowing = keypadHeight > screenHeight * 0.15;

            if (keyboardNowShowing != isKeyboardShowing) {
                isKeyboardShowing = keyboardNowShowing;
                navView.setVisibility(isKeyboardShowing ? View.GONE : View.VISIBLE);
            }
        });
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
