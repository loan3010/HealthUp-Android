package com.example.healthup;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        FloatingActionButton fabChat = findViewById(R.id.fabChat);
        if (fabChat != null) {
            fabChat.setOnClickListener(v ->
                    startActivity(ChatActivity.buyerIntent(MainActivity.this)));
        }

        maybeShowNotificationPermissionDialog();
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
}
