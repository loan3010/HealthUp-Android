package com.example.healthup.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity implements AdminNavigator {

    private final AdminDashboardFragment dashboardFragment = new AdminDashboardFragment();
    private final AdminProductsFragment productsFragment = new AdminProductsFragment();
    private final AdminOrdersFragment ordersFragment = new AdminOrdersFragment();
    private final AdminCustomersFragment customersFragment = new AdminCustomersFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        MaterialToolbar toolbar = findViewById(R.id.adminToolbar);
        setSupportActionBar(toolbar);

        AdminGate.verifyAdminFromServer(new AdminGate.RoleCallback() {
            @Override
            public void onResult(boolean isAdmin, String role) {
                if (!isAdmin) {
                    Toast.makeText(AdminActivity.this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show();
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(AdminActivity.this, AdminLoginActivity.class));
                    finish();
                }
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        BottomNavigationView bottomNav = findViewById(R.id.admin_bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();
            if (id == R.id.nav_admin_products) {
                target = productsFragment;
            } else if (id == R.id.nav_admin_orders) {
                target = ordersFragment;
            } else if (id == R.id.nav_admin_customers) {
                target = customersFragment;
            } else {
                target = dashboardFragment;
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.admin_fragment_container, target)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_admin_dashboard);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_admin_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void openOrders(@Nullable String statusFilter) {
        BottomNavigationView bottomNav = findViewById(R.id.admin_bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_admin_orders);
        ordersFragment.applyStatusFilter(statusFilter);
    }

    @Override
    public void openProducts(@Nullable String productFilter) {
        BottomNavigationView bottomNav = findViewById(R.id.admin_bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_admin_products);
        productsFragment.applyProductFilter(productFilter);
    }
}
