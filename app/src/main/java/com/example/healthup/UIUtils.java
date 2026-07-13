package com.example.healthup;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UIUtils {
    /** Áp dụng khoảng trống thanh trạng thái (Status Bar) cho view để không bị che content. */
    public static void applyStatusBarInsets(View view) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        }
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        View view = activity.getCurrentFocus();
        if (view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    /** Dismisses the keyboard when the user taps outside the focused text field. */
    public static void maybeHideKeyboardOnTouchOutside(Activity activity, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return;
        }
        View focused = activity.getCurrentFocus();
        if (!(focused instanceof EditText)) {
            return;
        }
        if (focused instanceof AutoCompleteTextView) {
            AutoCompleteTextView autoComplete = (AutoCompleteTextView) focused;
            if (autoComplete.isPopupShowing()) {
                return;
            }
        }
        Rect rect = new Rect();
        focused.getGlobalVisibleRect(rect);
        if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
            focused.clearFocus();
            hideKeyboard(activity);
        }
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

        // Tự động đóng sau 1.5 giây
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
