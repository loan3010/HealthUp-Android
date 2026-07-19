package com.example.healthup.admin;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.example.healthup.BaseAppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.healthup.AccountInfoActivity;
import com.example.healthup.LoginActivity;
import com.example.healthup.R;
import com.example.healthup.SellerChatListActivity;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.util.AppEntryRouter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminActivity extends BaseAppCompatActivity implements AdminNavigator {

    private final AdminDashboardFragment dashboardFragment = new AdminDashboardFragment();
    private final AdminProductsFragment productsFragment = new AdminProductsFragment();
    private final AdminOrdersFragment ordersFragment = new AdminOrdersFragment();
    private final AdminCustomersFragment customersFragment = new AdminCustomersFragment();

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private TextView tvNotifBadge;
    private TextView tvChatBadge;
    private ListenerRegistration notifBadgeListener;
    private ListenerRegistration chatBadgeListener;
    private View rootLayout;
    private boolean isKeyboardShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        drawerLayout = findViewById(R.id.adminDrawerLayout);
        toolbar = findViewById(R.id.adminToolbar);
        bottomNav = findViewById(R.id.admin_bottom_navigation);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        setupDrawer();

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

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment target;
            int titleRes;
            int id = item.getItemId();
            if (id == R.id.nav_admin_products) {
                target = productsFragment;
                titleRes = R.string.admin_nav_products;
            } else if (id == R.id.nav_admin_orders) {
                target = ordersFragment;
                titleRes = R.string.admin_nav_orders;
            } else if (id == R.id.nav_admin_customers) {
                target = customersFragment;
                titleRes = R.string.admin_nav_customers;
            } else {
                target = dashboardFragment;
                titleRes = R.string.admin_nav_dashboard;
            }
            toolbar.setTitle(titleRes);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.admin_fragment_container, target)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_admin_dashboard);
        } else {
            syncTitleWithSelectedTab();
        }

        setupKeyboardVisibilityListener();
        setupBackNavigation();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                confirmExitToBuyerApp();
            }
        });
    }

    private void confirmExitToBuyerApp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_exit_title)
                .setMessage(R.string.admin_exit_message)
                .setPositiveButton(R.string.admin_exit_confirm, (dialog, which) -> {
                    startActivity(AppEntryRouter.buyerHomeIntentSkippingAdminRedirect(AdminActivity.this));
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                if (bottomNav != null) {
                    bottomNav.setVisibility(isKeyboardShowing ? View.GONE : View.VISIBLE);
                }
            }
        });
    }

    private void setupDrawer() {
        TextView tvEmail = findViewById(R.id.tvAdminDrawerEmail);
        if (tvEmail != null) {
            bindAdminDrawerEmail(tvEmail);
        }

        View rowSettings = findViewById(R.id.rowAdminAccountSettings);
        View rowLogout = findViewById(R.id.rowAdminLogout);
        if (rowSettings != null) {
            rowSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, AccountInfoActivity.class));
            });
        }
        if (rowLogout != null) {
            rowLogout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                confirmLogout();
            });
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_logout_title)
                .setMessage(R.string.admin_logout_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_drawer_logout, (dialog, which) -> logout())
                .show();
    }

    /**
     * Prefer Firestore {@code displayEmail}/{@code email}; never show synthetic Auth emails
     * like {@code admin-...@healthup.app}.
     */
    private void bindAdminDrawerEmail(@NonNull TextView tvEmail) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvEmail.setText(R.string.account_email_empty);
            return;
        }
        // Placeholder until profile loads — never flash the synthetic Auth email.
        if (UserProfileBuilder.isRealEmail(user.getEmail())) {
            tvEmail.setText(user.getEmail());
        } else if (!TextUtils.isEmpty(user.getDisplayName())) {
            tvEmail.setText(user.getDisplayName());
        } else {
            tvEmail.setText(R.string.account_email_empty);
        }

        String authUid = user.getUid();
        AdminGate.resolveProfileDocId(authUid)
                .continueWithTask(task -> {
                    String profileDocId = (task.isSuccessful() && !TextUtils.isEmpty(task.getResult()))
                            ? task.getResult()
                            : authUid;
                    return FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(profileDocId)
                            .get();
                })
                .addOnSuccessListener(doc -> {
                    if (isFinishing()) return;
                    tvEmail.setText(resolveDrawerEmailLabel(doc, user));
                });
    }

    @NonNull
    private String resolveDrawerEmailLabel(@Nullable DocumentSnapshot doc, @NonNull FirebaseUser user) {
        if (doc != null && doc.exists()) {
            String display = doc.getString("displayEmail");
            String email = doc.getString("email");
            if (UserProfileBuilder.isRealEmail(display)) {
                return display.trim();
            }
            if (UserProfileBuilder.isRealEmail(email)) {
                return email.trim();
            }
            String name = firstNonEmpty(doc.getString("fullName"), doc.getString("displayName"));
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        if (UserProfileBuilder.isRealEmail(user.getEmail())) {
            return user.getEmail().trim();
        }
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        return getString(R.string.account_email_empty);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (!TextUtils.isEmpty(a)) return a.trim();
        if (!TextUtils.isEmpty(b)) return b.trim();
        return null;
    }

    private void syncTitleWithSelectedTab() {
        if (bottomNav == null || toolbar == null) return;
        int id = bottomNav.getSelectedItemId();
        if (id == R.id.nav_admin_products) {
            toolbar.setTitle(R.string.admin_nav_products);
        } else if (id == R.id.nav_admin_orders) {
            toolbar.setTitle(R.string.admin_nav_orders);
        } else if (id == R.id.nav_admin_customers) {
            toolbar.setTitle(R.string.admin_nav_customers);
        } else {
            toolbar.setTitle(R.string.admin_nav_dashboard);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_toolbar, menu);
        MenuItem notifItem = menu.findItem(R.id.action_admin_notifications);
        if (notifItem != null && notifItem.getActionView() != null) {
            View actionView = notifItem.getActionView();
            tvNotifBadge = actionView.findViewById(R.id.tvAdminNotifBadge);
            actionView.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminNotificationsActivity.class)));
            updateNotifBadgeUi(0);
        }
        MenuItem chatItem = menu.findItem(R.id.action_admin_chat);
        if (chatItem != null && chatItem.getActionView() != null) {
            View actionView = chatItem.getActionView();
            tvChatBadge = actionView.findViewById(R.id.tvAdminChatBadge);
            actionView.setOnClickListener(v ->
                    startActivity(new Intent(this, SellerChatListActivity.class)));
            updateChatBadgeUi(0);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_admin_notifications) {
            startActivity(new Intent(this, AdminNotificationsActivity.class));
            return true;
        }
        if (id == R.id.action_admin_chat) {
            startActivity(new Intent(this, SellerChatListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenUnreadNotifications();
        listenUnreadChats();
    }

    @Override
    protected void onStop() {
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
        if (chatBadgeListener != null) {
            chatBadgeListener.remove();
            chatBadgeListener = null;
        }
        super.onStop();
    }

    private void listenUnreadNotifications() {
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
        notifBadgeListener = FirebaseFirestore.getInstance()
                .collection("admin_notifications")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    int unread = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Boolean read = doc.getBoolean("read");
                        if (read == null || !read) {
                            unread++;
                        }
                    }
                    updateNotifBadgeUi(unread);
                });
    }

    private void listenUnreadChats() {
        if (chatBadgeListener != null) {
            chatBadgeListener.remove();
            chatBadgeListener = null;
        }
        // Count unread on client to avoid requiring a composite index.
        chatBadgeListener = FirebaseFirestore.getInstance()
                .collection("conversations")
                .whereEqualTo("sessionBucket", "active")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        updateChatBadgeUi(0);
                        return;
                    }
                    int unread = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (Boolean.TRUE.equals(doc.getBoolean("staffUnread"))) {
                            unread++;
                        }
                    }
                    updateChatBadgeUi(unread);
                });
    }

    private void updateNotifBadgeUi(int count) {
        if (tvNotifBadge == null) return;
        if (count <= 0) {
            tvNotifBadge.setVisibility(View.GONE);
            return;
        }
        tvNotifBadge.setVisibility(View.VISIBLE);
        tvNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private void updateChatBadgeUi(int count) {
        if (tvChatBadge == null) return;
        if (count <= 0) {
            tvChatBadge.setVisibility(View.GONE);
            return;
        }
        tvChatBadge.setVisibility(View.VISIBLE);
        tvChatBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    @Override
    public void openOrders(@Nullable String statusFilter) {
        bottomNav.setSelectedItemId(R.id.nav_admin_orders);
        ordersFragment.applyStatusFilter(statusFilter);
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void openProducts(@Nullable String productFilter) {
        bottomNav.setSelectedItemId(R.id.nav_admin_products);
        productsFragment.applyProductFilter(productFilter);
    }
}
