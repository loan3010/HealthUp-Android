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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.healthup.ui.notify.NotifyPermissionDialogFragment;
import com.example.healthup.ui.welcome.WelcomePromoBottomSheet;
import com.example.healthup.util.CartHelper;
import com.example.healthup.util.CheckoutIntentHelper;
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
    private View rootLayout;
    private boolean isKeyboardShowing = false;
    private ListenerRegistration cartListener;
    private final BroadcastReceiver guestCartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshGuestCartBadge();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.bottom_navigation);

        fabChat = findViewById(R.id.fabChat);
        if (fabChat != null) {
            fabChat.setOnClickListener(v ->
                    startActivity(ChatActivity.buyerIntent(MainActivity.this)));
        }

        applySystemBarInsets();

        navView.setOnItemSelectedListener(item -> {
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
            handleIntent(getIntent());
            maybeShowWelcomePromo();
        }

        setupCartBadgeListener();
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

        IntentFilter filter = new IntentFilter(GuestCartManager.ACTION_GUEST_CART_CHANGED);
        ContextCompat.registerReceiver(this, guestCartReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(guestCartReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (cartListener != null) {
            cartListener.remove();
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
                fragment instanceof OrderHistoryFragment ||
                fragment instanceof PolicyFragment ||
                fragment instanceof FAQFragment);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @SuppressWarnings("unchecked")
    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("navigate_to")) {
            String target = intent.getStringExtra("navigate_to");

            // FIX: điều hướng nhanh sang tab Giỏ hàng (sau khi "Mua ngay") hoặc tab Danh mục
            // (sau khi bấm "Xem tất cả"), không cần tạo OrderHistoryFragment cho các case này.
            if ("cart_tab".equals(target) || "cart".equals(target)) {
                navView.setSelectedItemId(R.id.nav_cart);
                boolean isRebuy = intent.getBooleanExtra("is_rebuy", false);
                CartFragment fragment = new CartFragment();
                if (isRebuy) {
                    Bundle args = new Bundle();
                    args.putBoolean("is_rebuy_flow", true);
                    fragment.setArguments(args);
                }
                loadFragment(fragment);
                return;
            } else if ("category_tab".equals(target)) {
                navView.setSelectedItemId(R.id.nav_category);
                return;
            } else if ("phone_verification".equals(target)) {
                navView.setSelectedItemId(R.id.nav_cart);
                loadFragment(new PhoneVerificationFragment());
                return;
            } else if ("checkout".equals(target)) {
                List<CartItem> checkoutItems = readCheckoutItems(intent);
                if (checkoutItems != null && !checkoutItems.isEmpty()) {
                    CheckoutFragment fragment = new CheckoutFragment();
                    Bundle args = new Bundle();
                    args.putSerializable("selected_items", (Serializable) checkoutItems);
                    fragment.setArguments(args);
                    loadFragment(fragment);
                } else {
                    navView.setSelectedItemId(R.id.nav_cart);
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

            // Fix Fragment Container: không để content lọt xuống dưới Nav Bar của app
            // Chúng ta không cần padding bottom ở đây vì fragment_container đã được constraint
            // vào TOP của bottom_navigation (đã được dãn chiều cao ở trên).

            if (fabChat != null) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) fabChat.getLayoutParams();
                params.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density) + systemBars.bottom;
                fabChat.setLayoutParams(params);
            }
            return windowInsets;
        });
    }


    private void loadFragment(Fragment fragment) {
        // ✅ QUY TẮC: Ẩn BottomNav và FAB khi vào quy trình mua hàng hoặc các trang con sâu
        boolean hideNavigation = isCheckoutFlow(fragment);

        if (navView != null) {
            navView.setVisibility(hideNavigation ? View.GONE : View.VISIBLE);
        }
        if (fabChat != null) {
            fabChat.setVisibility(hideNavigation ? View.GONE : View.VISIBLE);
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void showCartTab() {
        navView.setSelectedItemId(R.id.nav_cart);
        loadFragment(new CartFragment());
    }

    public void showHomeTab() {
        navView.setSelectedItemId(R.id.nav_home);
        loadFragment(new HomeFragment());
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
