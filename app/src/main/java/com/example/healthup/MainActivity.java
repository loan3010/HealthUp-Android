package com.example.healthup;




import android.content.Intent;
import android.graphics.Rect;
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
import com.example.healthup.util.NotificationPermissionHelper;
import com.example.models.CartItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.Serializable;
import java.util.List;




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
    private FloatingActionButton fabChat;
    private View rootLayout;
    private boolean isKeyboardShowing = false;


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
                updateFabVisibility(false);
                return true;
            } else if (id == R.id.nav_category) {
                loadFragment(new ProductListFragment());
                updateFabVisibility(false);
                return true;
            } else if (id == R.id.nav_cart) {
                loadFragment(new CartFragment());
                updateFabVisibility(true);
                return true;
            } else if (id == R.id.nav_notifications) {
                loadFragment(new NotificationsFragment());
                updateFabVisibility(false);
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                updateFabVisibility(false);
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


    @SuppressWarnings("unchecked")
    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("navigate_to")) {
            String target = intent.getStringExtra("navigate_to");


            // FIX: điều hướng nhanh sang tab Giỏ hàng (sau khi "Mua ngay") hoặc tab Danh mục
            // (sau khi bấm "Xem tất cả"), không cần tạo OrderHistoryFragment cho các case này.
            if ("cart_tab".equals(target)) {
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
            } else if ("checkout".equals(target)) {
                // FIX (yêu cầu #2): mở thẳng CheckoutFragment với đúng 1 sản phẩm (kèm phân
                // loại/số lượng) vừa được chọn ở Popup "Mua ngay" từ trang Chi tiết sản phẩm.
                // Dùng cùng "khe" args ("selected_items") mà CheckoutFragment vốn đã đọc khi
                // được mở từ CartFragment, nên không cần sửa gì thêm ở CheckoutFragment.
                Serializable data = intent.getSerializableExtra("checkout_items");
                if (data instanceof List) {
                    List<CartItem> checkoutItems = (List<CartItem>) data;
                    CheckoutFragment fragment = new CheckoutFragment();
                    Bundle args = new Bundle();
                    args.putSerializable("selected_items", (Serializable) checkoutItems);
                    fragment.setArguments(args);
                    loadFragment(fragment);
                    updateFabVisibility(false);
                } else {
                    // An toàn: nếu vì lý do gì đó dữ liệu bị thiếu, không mở trang Thanh toán
                    // trống mà quay về Giỏ hàng để người dùng không bị kẹt ở màn hình lỗi.
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
                updateFabVisibility(false);
                return;
            } else if ("policy".equals(target)) {
                loadFragment(new PolicyFragment());
                updateFabVisibility(false);
                return;
            }


            fragment.setArguments(args);
            loadFragment(fragment);
            updateFabVisibility(false);
        } else {
            loadFragment(new HomeFragment());
            updateFabVisibility(false);
        }
    }


    private void applySystemBarInsets() {
        View root = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            navView.setPadding(0, 0, 0, systemBars.bottom);
            if (fabChat != null) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) fabChat.getLayoutParams();
                params.bottomMargin = 16 + systemBars.bottom;
                fabChat.setLayoutParams(params);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }


    private void updateFabVisibility(boolean hideOnCart) {
        if (fabChat == null) {
            return;
        }
        fabChat.setVisibility(hideOnCart ? View.GONE : View.VISIBLE);
    }


    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}