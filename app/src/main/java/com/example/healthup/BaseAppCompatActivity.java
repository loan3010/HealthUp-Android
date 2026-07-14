package com.example.healthup;

import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Hides the soft keyboard when the user taps outside a focused text field.
 * Prefer extending this for all screens that may show an input keyboard.
 */
public abstract class BaseAppCompatActivity extends AppCompatActivity {

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        UIUtils.maybeHideKeyboardOnTouchOutside(this, event);
        return super.dispatchTouchEvent(event);
    }
}
