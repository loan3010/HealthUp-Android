package com.example.healthup.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Host for ViewPager2 inside NestedScrollView / CoordinatorLayout.
 * On horizontal drag, walks the parent chain and disallows intercept so swipe works.
 */
public class NestedScrollableHost extends FrameLayout {

    private float initialX;
    private float initialY;
    private boolean horizontalGesture;
    private final int touchSlop;

    public NestedScrollableHost(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        handleTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleTouch(ev);
        return super.onInterceptTouchEvent(ev);
    }

    private void handleTouch(MotionEvent e) {
        ViewPager2 pager = findChildViewPager();
        if (pager == null || !pager.isUserInputEnabled()) {
            return;
        }
        int itemCount = pager.getAdapter() != null ? pager.getAdapter().getItemCount() : 0;
        if (itemCount <= 1) {
            return;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = e.getX();
                initialY = e.getY();
                horizontalGesture = false;
                // Claim touches until we know direction — stops AppBar/NestedScroll stealing the DOWN.
                requestAllParentsDisallowIntercept(true);
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - initialX;
                float dy = e.getY() - initialY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                if (!horizontalGesture && absDx > touchSlop && absDx > absDy) {
                    horizontalGesture = true;
                    requestAllParentsDisallowIntercept(true);
                } else if (!horizontalGesture && absDy > touchSlop && absDy > absDx) {
                    // Vertical scroll — let NestedScrollView / AppBar handle it.
                    requestAllParentsDisallowIntercept(false);
                } else if (horizontalGesture) {
                    requestAllParentsDisallowIntercept(true);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                horizontalGesture = false;
                requestAllParentsDisallowIntercept(false);
                break;
            default:
                break;
        }
    }

    private void requestAllParentsDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    @Nullable
    private ViewPager2 findChildViewPager() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ViewPager2) {
                return (ViewPager2) child;
            }
        }
        return null;
    }
}
