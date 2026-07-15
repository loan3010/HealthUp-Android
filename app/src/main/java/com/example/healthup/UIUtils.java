package com.example.healthup;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UIUtils {
    /** Áp dụng khoảng trống thanh trạng thái (Status Bar) cho view để không bị che content. */
    public static void applyStatusBarInsets(View view) {
        if (view == null) return;
        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(baseLeft, baseTop + systemBars.top, baseRight, baseBottom);
            return windowInsets;
        });
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        }
    }

    /**
     * Keeps Login/Register form and the floating home button clear of status/nav bars
     * across notch and different screen densities.
     */
    public static void applyAuthScreenInsets(Activity activity,
                                             @Nullable View scrollContent,
                                             @Nullable View btnHome) {
        if (activity == null) {
            return;
        }
        final View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        final int contentLeft = scrollContent != null ? scrollContent.getPaddingLeft() : 0;
        final int contentTop = scrollContent != null ? scrollContent.getPaddingTop() : 0;
        final int contentRight = scrollContent != null ? scrollContent.getPaddingRight() : 0;
        final int contentBottom = scrollContent != null ? scrollContent.getPaddingBottom() : 0;
        final int homeBaseTop;
        if (btnHome != null && btnHome.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            homeBaseTop = ((ViewGroup.MarginLayoutParams) btnHome.getLayoutParams()).topMargin;
        } else {
            homeBaseTop = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8f, activity.getResources().getDisplayMetrics());
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (scrollContent != null) {
                scrollContent.setPadding(
                        contentLeft,
                        contentTop + bars.top,
                        contentRight,
                        contentBottom + bars.bottom);
            }
            if (btnHome != null
                    && btnHome.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) btnHome.getLayoutParams();
                lp.topMargin = homeBaseTop + bars.top;
                btnHome.setLayoutParams(lp);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Login-style border: grey by default, green when the search field is focused.
     * Background must be on the container around the EditText (pill search bar).
     */
    public static void bindFocusBorder(View container, EditText editText) {
        if (container == null || editText == null) {
            return;
        }
        container.setBackgroundResource(R.drawable.bg_search_bar);
        editText.setOnFocusChangeListener((v, hasFocus) ->
                container.setBackgroundResource(
                        hasFocus ? R.drawable.bg_search_bar_focused : R.drawable.bg_search_bar));
    }

    public static void hideKeyboard(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        View focused = activity.getCurrentFocus();
        View tokenView = focused != null ? focused : decor;

        // Modern IME hide (Android 11+ path via AndroidX) — more reliable than IMM alone.
        WindowInsetsControllerCompat insetsController = ViewCompat.getWindowInsetsController(decor);
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.ime());
        }

        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && tokenView != null) {
            imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
            imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }

        if (focused != null) {
            focused.clearFocus();
        }
        // Move focus off EditText so the IME does not reopen / stick.
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setFocusable(true);
            content.setFocusableInTouchMode(true);
            content.requestFocus();
        }
    }

    /** Dismisses the keyboard when the user taps outside the focused text field. */
    public static void maybeHideKeyboardOnTouchOutside(Activity activity, MotionEvent event) {
        if (activity == null || event.getAction() != MotionEvent.ACTION_DOWN) {
            return;
        }

        View focused = activity.getCurrentFocus();
        if (focused instanceof AutoCompleteTextView) {
            AutoCompleteTextView autoComplete = (AutoCompleteTextView) focused;
            if (autoComplete.isPopupShowing()) {
                return;
            }
        }

        if (focused instanceof EditText) {
            if (isTouchInsideView(focused, event)) {
                return;
            }
            hideKeyboard(activity);
            return;
        }

        // Keyboard can stay open even after focus was lost — still dismiss on empty tap.
        if (isImeVisible(activity)) {
            hideKeyboard(activity);
        }
    }

    private static boolean isImeVisible(Activity activity) {
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) {
            return false;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    /** Screen coordinates: raw event vs getLocationOnScreen (canonical for Activity.dispatchTouchEvent). */
    private static boolean isTouchInsideView(View view, MotionEvent event) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= loc[0]
                && x <= loc[0] + view.getWidth()
                && y >= loc[1]
                && y <= loc[1] + view.getHeight();
    }

    public static void showSuccessDialog(Activity activity, String message, Runnable onConfirm) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_success);

        if (message != null) {
            TextView tvMessage = dialog.findViewById(R.id.tvMessage);
            if (tvMessage != null) tvMessage.setText(message);
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.6f);
            window.getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        }

        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing() && !activity.isFinishing()) {
                dialog.dismiss();
                if (onConfirm != null) {
                    onConfirm.run();
                }
            }
        }, 1500);
    }

    public static void showSuccessDialog(Activity activity, Runnable onConfirm) {
        showSuccessDialog(activity, null, onConfirm);
    }
}
