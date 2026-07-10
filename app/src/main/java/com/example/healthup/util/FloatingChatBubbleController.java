package com.example.healthup.util;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.healthup.R;

/**
 * Messenger-style draggable chat bubble: snap to screen corners;
 * drag to the center zone to dismiss.
 */
public final class FloatingChatBubbleController {

    private static final String PREFS = "floating_chat_bubble";
    private static final String KEY_DISMISSED = "dismissed";
    private static final String KEY_CORNER = "corner";

    private static final float DISMISS_ZONE_WIDTH_RATIO = 0.42f;
    private static final float DISMISS_ZONE_HEIGHT_RATIO = 0.42f;
    private static final int DRAG_THRESHOLD_PX = 10;

    public enum Corner {
        TOP_START, TOP_END, BOTTOM_START, BOTTOM_END
    }

    private final View bubble;
    private final ViewGroup parent;
    private final Runnable onOpenChat;
    private final SharedPreferences prefs;

    private float touchStartRawX;
    private float touchStartRawY;
    private float bubbleStartX;
    private float bubbleStartY;
    private boolean dragging;
    private int bottomReservedPx;
    private int edgeMarginPx;

    public FloatingChatBubbleController(@NonNull Context context,
                                        @NonNull View bubble,
                                        @NonNull ViewGroup parent,
                                        @NonNull Runnable onOpenChat) {
        this.bubble = bubble;
        this.parent = parent;
        this.onOpenChat = onOpenChat;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.edgeMarginPx = dp(context, 12);
    }

    public void attach() {
        if (bubble.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams params =
                    (android.widget.FrameLayout.LayoutParams) bubble.getLayoutParams();
            params.gravity = android.view.Gravity.NO_GRAVITY;
            bubble.setLayoutParams(params);
        }
        bubble.setClickable(true);
        bubble.setFocusable(true);
        bubble.setOnTouchListener(this::handleTouch);
        if (isDismissed()) {
            bubble.setVisibility(View.GONE);
        } else {
            bubble.post(this::placeSavedCorner);
        }
    }

    public void updateBottomReservedPx(int px) {
        bottomReservedPx = Math.max(0, px);
        if (bubble.getVisibility() == View.VISIBLE) {
            bubble.post(this::snapToSavedOrNearestCorner);
        }
    }

    public void markDismissed(boolean dismissed) {
        prefs.edit().putBoolean(KEY_DISMISSED, dismissed).apply();
        if (!dismissed) {
            bubble.setVisibility(View.VISIBLE);
            bubble.setAlpha(1f);
            bubble.setScaleX(1f);
            bubble.setScaleY(1f);
            bubble.post(this::placeSavedCorner);
        }
    }

    public boolean isDismissed() {
        return prefs.getBoolean(KEY_DISMISSED, false);
    }

    public void restoreAfterChatEntry() {
        markDismissed(false);
    }

    private boolean handleTouch(@NonNull View v, @NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartRawX = event.getRawX();
                touchStartRawY = event.getRawY();
                bubbleStartX = bubble.getX();
                bubbleStartY = bubble.getY();
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchStartRawX;
                float dy = event.getRawY() - touchStartRawY;
                if (!dragging && (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX)) {
                    dragging = true;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    moveBubble(bubbleStartX + dx, bubbleStartY + dy);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    finishDrag();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void finishDrag() {
        float centerX = bubble.getX() + bubble.getWidth() / 2f;
        float centerY = bubble.getY() + bubble.getHeight() / 2f;
        if (isInDismissZone(centerX, centerY)) {
            dismissBubble();
            return;
        }
        snapToNearestCorner(centerX, centerY);
    }

    private void dismissBubble() {
        bubble.animate()
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        bubble.setVisibility(View.GONE);
                        markDismissed(true);
                        Toast.makeText(bubble.getContext(),
                                R.string.chat_bubble_dismissed, Toast.LENGTH_SHORT).show();
                    }
                })
                .start();
    }

    private boolean isInDismissZone(float centerX, float centerY) {
        float parentWidth = parent.getWidth();
        float parentHeight = getDraggableHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            return false;
        }
        float zoneWidth = parentWidth * DISMISS_ZONE_WIDTH_RATIO;
        float zoneHeight = parentHeight * DISMISS_ZONE_HEIGHT_RATIO;
        float zoneLeft = (parentWidth - zoneWidth) / 2f;
        float zoneTop = (parentHeight - zoneHeight) / 2f;
        return centerX >= zoneLeft
                && centerX <= zoneLeft + zoneWidth
                && centerY >= zoneTop
                && centerY <= zoneTop + zoneHeight;
    }

    private void moveBubble(float x, float y) {
        float maxX = Math.max(0f, parent.getWidth() - bubble.getWidth());
        float maxY = Math.max(0f, getDraggableHeight() - bubble.getHeight());
        bubble.setX(clamp(x, 0f, maxX));
        bubble.setY(clamp(y, 0f, maxY));
    }

    private void snapToNearestCorner(float centerX, float centerY) {
        Corner corner = resolveNearestCorner(centerX, centerY);
        saveCorner(corner);
        animateToCorner(corner);
    }

    private void snapToSavedOrNearestCorner() {
        Corner corner = readSavedCorner();
        animateToCorner(corner);
    }

    private void placeSavedCorner() {
        Corner corner = readSavedCorner();
        float[] pos = cornerPosition(corner);
        bubble.setX(pos[0]);
        bubble.setY(pos[1]);
    }

    private Corner resolveNearestCorner(float centerX, float centerY) {
        float parentWidth = parent.getWidth();
        float parentHeight = getDraggableHeight();
        boolean toStart = centerX < parentWidth / 2f;
        boolean toTop = centerY < parentHeight / 2f;
        if (toTop && toStart) return Corner.TOP_START;
        if (toTop) return Corner.TOP_END;
        if (toStart) return Corner.BOTTOM_START;
        return Corner.BOTTOM_END;
    }

    private void animateToCorner(@NonNull Corner corner) {
        float[] pos = cornerPosition(corner);
        bubble.animate()
                .x(pos[0])
                .y(pos[1])
                .setDuration(220)
                .start();
    }

    private float[] cornerPosition(@NonNull Corner corner) {
        float maxX = Math.max(0f, parent.getWidth() - bubble.getWidth());
        float maxY = Math.max(0f, getDraggableHeight() - bubble.getHeight());
        float x;
        float y;
        switch (corner) {
            case TOP_START:
                x = edgeMarginPx;
                y = edgeMarginPx;
                break;
            case TOP_END:
                x = maxX - edgeMarginPx;
                y = edgeMarginPx;
                break;
            case BOTTOM_START:
                x = edgeMarginPx;
                y = maxY - edgeMarginPx;
                break;
            case BOTTOM_END:
            default:
                x = maxX - edgeMarginPx;
                y = maxY - edgeMarginPx;
                break;
        }
        return new float[] {clamp(x, 0f, maxX), clamp(y, 0f, maxY)};
    }

    private float getDraggableHeight() {
        return Math.max(0f, parent.getHeight() - bottomReservedPx);
    }

    private void saveCorner(@NonNull Corner corner) {
        prefs.edit().putString(KEY_CORNER, corner.name()).apply();
    }

    @NonNull
    private Corner readSavedCorner() {
        String value = prefs.getString(KEY_CORNER, Corner.BOTTOM_END.name());
        try {
            return Corner.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Corner.BOTTOM_END;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int dp(@NonNull Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
