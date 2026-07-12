package com.example.healthup;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.healthup.admin.AdminActivity;
import com.example.healthup.ui.notify.NotifyPermissionDialogFragment;
import com.example.healthup.ui.welcome.WelcomePromoBottomSheet;
import com.example.healthup.util.AccountDisabledWatcher;
import com.example.healthup.util.AppEntryRouter;
import com.example.healthup.util.CartHelper;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.FloatingChatBubbleController;
import com.example.healthup.util.NotificationPermissionHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.models.CartItem;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.example.healthup.util.LocaleHelper;
import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    /** Bỏ qua một lần load ProductListFragment mặc định khi HomeFragment đã tự navigate. */
    static boolean skipNextCategoryNavLoad = false;

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
    private FloatingActionButton fabChat;
    private FloatingChatBubbleController floatingChatBubble;
    private View rootLayout;
    private boolean isKeyboardShowing = false;
    /** Prevents bottom-nav listener from loading fragments during programmatic tab changes. */
    private boolean suppressNavSelection;
    @Nullable
    private Intent deferredIntent;
    private ListenerRegistration cartListener;
    private ListenerRegistration notifBadgeListener;
    private final AccountDisabledWatcher accountDisabledWatcher = new AccountDisabledWatcher();
    private final BroadcastReceiver guestCartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshGuestCartBadge();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.bottom_navigation);

        fabChat = findViewById(R.id.fabChat);
        View mainRoot = findViewById(R.id.main_root);
        View dismissZone = findViewById(R.id.chatDismissZone);
        if (fabChat != null && mainRoot instanceof ViewGroup) {
            floatingChatBubble = new FloatingChatBubbleController(
                    this,
                    fabChat,
                    (ViewGroup) mainRoot,
                    () -> openBuyerChat(),
                    dismissZone);
            floatingChatBubble.attach();
            fabChat.setOnClickListener(v -> openBuyerChat());
        }

        applySystemBarInsets();

        navView.setOnItemSelectedListener(item -> {
            if (suppressNavSelection) {
                return true;
            }
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_category) {
                if (skipNextCategoryNavLoad) {
                    skipNextCategoryNavLoad = false;
                    return true;
                }
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

        // ✅ Tự động tạo dữ liệu Voucher và Sản phẩm nếu chưa có
        FirebaseManager.getInstance().seedVouchersIfEmpty();
        FirebaseManager.getInstance().seedProductsIfEmpty();

        if (savedInstanceState == null) {
            routeAdminAwayIfNeeded();
            handleIntent(getIntent());
            maybeShowWelcomePromo();
        }

        setupCartBadgeListener();
        setupNotificationBadgeListener();

        // ✅ FIX: Lắng nghe thay đổi BackStack để hiện lại thanh Nav Bar khi quay về các tab chính
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateNavigationVisibility);
    }

    private void routeAdminAwayIfNeeded() {
        AppEntryRouter.resolveHomeIntent(this, intent -> {
            if (AdminActivity.class.getName().equals(intent.getComponent().getClassName())) {
                startActivity(intent);
                finish();
            }
        });
    }

    private void updateNavigationVisibility() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            boolean hideNavigation = isCheckoutFlow(currentFragment);
            if (navView != null) {
                navView.setVisibility(hideNavigation ? View.GONE : View.VISIBLE);
            }
            if (fabChat != null) {
                boolean showBubble = !hideNavigation
                        && (floatingChatBubble == null || !floatingChatBubble.isDismissed());
                fabChat.setVisibility(showBubble ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void setupCartBadgeListener() {
        if (cartListener != null) {
            cartListener.remove();
            cartListener = null;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            cartListener = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("cart")
                    .addSnapshotListener((value, error) -> {
                        if (value != null) {
                            int count = 0;
                            for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                                // Đồng bộ logic đếm với CartFragment: chấp nhận cả field "name"
                                // ở top-level hoặc lồng trong map "product", để badge khớp với
                                // tiêu đề "Giỏ hàng (n)".
                                if (CartHelper.isValidCartDocument(doc)) {
                                    count++;
                                }
                            }
                            updateCartBadge(count);
                        }
                    });
        } else {
            refreshGuestCartBadge();
        }
    }

    private void updateCartBadge(int count) {
        if (navView == null) return;
        BadgeDrawable badge = navView.getOrCreateBadge(R.id.nav_cart);
        if (count > 0) {
            badge.setVisible(true);
            badge.setNumber(count);
        } else {
            badge.setVisible(false);
        }
    }

    private void setupNotificationBadgeListener() {
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateNotificationBadge(0);
            return;
        }

        notifBadgeListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("notifications")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    int unread = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                        Boolean read = doc.getBoolean("read");
                        if (read == null || !read) {
                            unread++;
                        }
                    }
                    updateNotificationBadge(unread);
                });
    }

    private void updateNotificationBadge(int count) {
        if (navView == null) return;
        BadgeDrawable badge = navView.getOrCreateBadge(R.id.nav_notifications);
        if (count > 0) {
            badge.setVisible(true);
            badge.setNumber(Math.min(count, 99));
        } else {
            badge.setVisible(false);
            badge.clearNumber();
        }
    }

    public void refreshGuestCartBadge() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) return;

        List<CartItem> items = GuestCartManager.getInstance(this).getItems();
        int count = 0;
        for (CartItem item : items) {
            if (item.getProductId() != null && !item.getProductId().isEmpty()) {
                count++;
            }
        }
        updateCartBadge(count);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh listener in case user logged in/out
        setupCartBadgeListener();
        setupNotificationBadgeListener();
        accountDisabledWatcher.attach(this);

        IntentFilter filter = new IntentFilter(GuestCartManager.ACTION_GUEST_CART_CHANGED);
        ContextCompat.registerReceiver(this, guestCartReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (deferredIntent != null) {
            Intent intent = deferredIntent;
            deferredIntent = null;
            handleIntent(intent);
        }
    }

    @Override
    protected void onPause() {
        accountDisabledWatcher.detach();
        unregisterReceiver(guestCartReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (cartListener != null) {
            cartListener.remove();
        }
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
        }
        super.onDestroy();
    }

    private void maybeShowWelcomePromo() {
        if (getIntent() != null && getIntent().hasExtra("navigate_to")) {
            return;
        }
        getSupportFragmentManager().executePendingTransactions();
        WelcomePromoBottomSheet.showIfNeeded(getSupportFragmentManager(), this);
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
                
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                boolean isHideFlow = isKeyboardShowing || isCheckoutFlow(currentFragment);
                navView.setVisibility(isHideFlow ? View.GONE : View.VISIBLE);
            }
        });
    }

    private boolean isCheckoutFlow(Fragment fragment) {
        return (fragment instanceof CartFragment ||
                fragment instanceof CheckoutFragment ||
                fragment instanceof PhoneVerificationFragment ||
                fragment instanceof AddressBookFragment ||
                fragment instanceof PromoCouponFragment ||
                fragment instanceof OrderHistoryFragment || // ✅ Thêm lại để ẩn Nav Bar
                fragment instanceof PolicyFragment ||
                fragment instanceof MemberTierFragment ||
                fragment instanceof WishlistFragment ||
                fragment instanceof FAQFragment);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Defer navigation until onResume so fragment transactions are safe after CLEAR_TOP.
        deferredIntent = intent;
    }

    @SuppressWarnings("unchecked")
    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("navigate_to")) {
            String target = intent.getStringExtra("navigate_to");

            // FIX: điều hướng nhanh sang tab Giỏ hàng (sau khi "Mua ngay") hoặc tab Danh mục
            // (sau khi bấm "Xem tất cả"), không cần tạo OrderHistoryFragment cho các case này.
            if ("home_tab".equals(target)) {
                showHomeTab();
                return;
            } else if ("cart_tab".equals(target) || "cart".equals(target)) {
                selectNavTab(R.id.nav_cart);
                boolean isRebuy = intent.getBooleanExtra("is_rebuy", false);
                // FIX (bug #4): truyền cờ "return_to_previous" xuống CartFragment để nút
                // "Quay lại" biết cần finish() Activity này (quay về ProductDetailActivity)
                // thay vì cố popBackStack rồi rơi về tab Trang chủ.
                boolean returnToPrevious = intent.getBooleanExtra("return_to_previous", false);
                CartFragment fragment = new CartFragment();
                Bundle args = new Bundle();
                if (isRebuy) {
                    args.putBoolean("is_rebuy_flow", true);
                }
                if (returnToPrevious) {
                    args.putBoolean("return_to_previous", true);
                }
                if (!args.isEmpty()) {
                    fragment.setArguments(args);
                }
                loadFragmentAllowingStateLoss(fragment);
                return;
            } else if ("category_tab".equals(target)) {
                selectNavTab(R.id.nav_category);
                return;
            } else if ("phone_verification".equals(target)) {
                boolean returnToPrevious = intent.getBooleanExtra("return_to_previous", false);
                if (!returnToPrevious) {
                    selectNavTab(R.id.nav_cart);
                }
                PhoneVerificationFragment fragment = new PhoneVerificationFragment();
                Bundle phoneArgs = new Bundle();
                phoneArgs.putBoolean(PhoneVerificationFragment.ARG_RETURN_TO_PREVIOUS, returnToPrevious);
                fragment.setArguments(phoneArgs);
                loadFragmentAllowingStateLoss(fragment);
                return;
            } else if ("checkout".equals(target)) {
                List<CartItem> checkoutItems = readCheckoutItems(intent);
                if (checkoutItems != null && !checkoutItems.isEmpty()) {
                    CheckoutFragment fragment = new CheckoutFragment();
                    Bundle args = new Bundle();
                    args.putSerializable("selected_items", (Serializable) checkoutItems);
                    fragment.setArguments(args);
                    loadFragmentAllowingStateLoss(fragment);
                } else {
                    selectNavTab(R.id.nav_cart);
                }
                return;
            }

            OrderHistoryFragment fragment = new OrderHistoryFragment();
            Bundle args = new Bundle();

            if ("returned_tab".equals(target)) {
                args.putInt("initial_tab", 5);
            } else if ("cancelled_tab".equals(target)) {
                args.putInt("initial_tab", 6);
            } else if ("delivered_tab".equals(target)) {
                args.putInt("initial_tab", 4);
            } else if ("shipping_tab".equals(target)) {
                args.putInt("initial_tab", 3);
            } else if ("confirmed_tab".equals(target)) {
                args.putInt("initial_tab", 2);
            } else if ("pending_tab".equals(target)) {
                args.putInt("initial_tab", 1);
            } else if ("faq".equals(target)) {
                loadFragment(new FAQFragment());
                return;
            } else if ("policy".equals(target)) {
                loadFragment(new PolicyFragment());
                return;
            }

            fragment.setArguments(args);
            loadFragment(fragment);
        } else {
            loadFragment(new HomeFragment());
        }
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.main_root);
        // Cho phép app vẽ tràn viền (Edge-to-edge)
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Fix Top: Tránh bị thanh trạng thái che (Status Bar)
            View container = findViewById(R.id.fragment_container);
            if (container != null) {
                container.setPadding(0, systemBars.top, 0, 0);
            }

            // Fix Nav Bar: dùng padding bottom thay vì bóp nghẹt chiều cao
            navView.setPadding(0, 0, 0, systemBars.bottom);

            updateFloatingChatReservedSpace(systemBars.top, systemBars.bottom);
            return windowInsets;
        });
    }

    private void updateFloatingChatReservedSpace(int topInset, int systemBottomInset) {
        if (floatingChatBubble == null || navView == null) {
            return;
        }
        navView.post(() -> {
            int margin = (int) (16 * getResources().getDisplayMetrics().density);
            int reserved = navView.getHeight() + systemBottomInset + margin;
            floatingChatBubble.updateInsets(topInset, reserved);
        });
    }

    public void restoreFloatingChatBubble() {
        if (floatingChatBubble != null) {
            floatingChatBubble.restoreAfterChatEntry();
        }
    }

    private void openBuyerChat() {
        restoreFloatingChatBubble();
        startActivity(ChatActivity.buyerIntent(MainActivity.this));
    }


    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();

        navView.post(this::updateNavigationVisibility);
    }

    private void loadFragmentAllowingStateLoss(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();

        navView.post(this::updateNavigationVisibility);
    }

    private void selectNavTab(int itemId) {
        if (navView == null) {
            return;
        }
        suppressNavSelection = true;
        try {
            navView.setSelectedItemId(itemId);
        } finally {
            suppressNavSelection = false;
        }
    }

    public void showCartTab() {
        if (navView == null) {
            return;
        }
        Runnable action = () -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            selectNavTab(R.id.nav_cart);
            loadFragmentAllowingStateLoss(new CartFragment());
        };
        if (navView.isAttachedToWindow()) {
            action.run();
        } else {
            navView.post(action);
        }
    }

    public void showHomeTab() {
        if (navView == null) {
            return;
        }
        Runnable action = () -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            selectNavTab(R.id.nav_home);
            loadFragmentAllowingStateLoss(new HomeFragment());
        };
        if (navView.isAttachedToWindow()) {
            action.run();
        } else {
            navView.post(action);
        }
    }

    public void showProfileTab() {
        if (navView != null) {
            navView.setSelectedItemId(R.id.nav_profile);
            loadFragment(new ProfileFragment());
        }
    }

    @SuppressWarnings("unchecked")
    private List<CartItem> readCheckoutItems(Intent intent) {
        if (intent == null) {
            return null;
        }

        Serializable data;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data = intent.getSerializableExtra(CheckoutIntentHelper.EXTRA_CHECKOUT_ITEMS, Serializable.class);
        } else {
            data = intent.getSerializableExtra(CheckoutIntentHelper.EXTRA_CHECKOUT_ITEMS);
        }

        if (data instanceof List) {
            return (List<CartItem>) data;
        }
        return null;
    }


}
