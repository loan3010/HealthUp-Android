package com.example.healthup.ui.otp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.healthup.R;

import java.util.ArrayList;
import java.util.List;

public class OtpBoxesHelper {

    public interface Listener {
        void onOtpChanged(String otp, boolean complete);

        void onOtpComplete(String otp);
    }

    private static final int BOX_COUNT = 6;
    private static final int BORDER_ANIMATION_MS = 200;

    private final Context context;
    private final LinearLayout container;
    private final LinearLayout boxesLayout;
    private final List<EditText> boxes = new ArrayList<>();
    private final List<Integer> boxBorderRes = new ArrayList<>();

    private Listener listener;
    private boolean hasError;
    private boolean enabled = true;

    public OtpBoxesHelper(@NonNull Context context, @NonNull View root) {
        this.context = context;
        this.container = root.findViewById(R.id.otpBoxesContainer);
        this.boxesLayout = root.findViewById(R.id.otpBoxesLayout);
        initBoxes();
    }

    private void initBoxes() {
        int[] boxIds = {
                R.id.otpBox1, R.id.otpBox2, R.id.otpBox3,
                R.id.otpBox4, R.id.otpBox5, R.id.otpBox6
        };

        for (int i = 0; i < BOX_COUNT; i++) {
            EditText box = boxesLayout.findViewById(boxIds[i]);
            box.setInputType(InputType.TYPE_CLASS_NUMBER);
            box.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            boxes.add(box);
            boxBorderRes.add(R.drawable.bg_input_default);

            final int index = i;
            box.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!enabled) {
                        return;
                    }

                    if (hasError) {
                        clearError();
                    }

                    if (s.length() == 1 && index < BOX_COUNT - 1) {
                        boxes.get(index + 1).requestFocus();
                    }

                    notifyChange();
                }
            });

            box.setOnKeyListener((v, keyCode, event) -> {
                if (!enabled) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DEL
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && box.getText().length() == 0
                        && index > 0) {
                    EditText previous = boxes.get(index - 1);
                    previous.requestFocus();
                    previous.setText("");
                    return true;
                }
                return false;
            });

            box.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasError) {
                    return;
                }
                applyBoxBorder(index, hasFocus
                        ? R.drawable.bg_input_focused
                        : R.drawable.bg_input_default);
            });

            box.setOnClickListener(v -> box.selectAll());
        }

        boxes.get(0).setOnLongClickListener(v -> {
            pasteFromClipboard();
            return true;
        });
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }

        CharSequence pasted = clip.getItemAt(0).getText();
        if (pasted == null) {
            return;
        }

        String digits = pasted.toString().replaceAll("\\D", "");
        if (digits.length() < BOX_COUNT) {
            return;
        }

        for (int i = 0; i < BOX_COUNT; i++) {
            boxes.get(i).setText(String.valueOf(digits.charAt(i)));
        }
        boxes.get(BOX_COUNT - 1).requestFocus();
        notifyChange();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public String getOtp() {
        StringBuilder builder = new StringBuilder();
        for (EditText box : boxes) {
            Editable text = box.getText();
            builder.append(text == null ? "" : text.toString());
        }
        return builder.toString();
    }

    public void clear() {
        for (EditText box : boxes) {
            box.setText("");
        }
        boxes.get(0).requestFocus();
        notifyChange();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (EditText box : boxes) {
            box.setEnabled(enabled);
        }
    }

    public void showError() {
        hasError = true;
        for (int i = 0; i < BOX_COUNT; i++) {
            applyBoxBorder(i, R.drawable.bg_input_error);
        }
        if (container != null) {
            shakeView(container);
        }
    }

    public void clearError() {
        hasError = false;
        for (int i = 0; i < BOX_COUNT; i++) {
            EditText box = boxes.get(i);
            applyBoxBorder(i, box.hasFocus()
                    ? R.drawable.bg_input_focused
                    : R.drawable.bg_input_default);
        }
    }

    public void requestFocusFirst() {
        boxes.get(0).requestFocus();
    }

    private void notifyChange() {
        String otp = getOtp();
        boolean complete = otp.length() == BOX_COUNT;
        if (listener != null) {
            listener.onOtpChanged(otp, complete);
            if (complete) {
                listener.onOtpComplete(otp);
            }
        }
    }

    private void applyBoxBorder(int index, int borderRes) {
        if (index < 0 || index >= boxes.size()) {
            return;
        }
        EditText box = boxes.get(index);
        int current = boxBorderRes.get(index);
        if (current == borderRes) {
            return;
        }

        View wrapper = box.getParent() instanceof View ? (View) box.getParent() : box;
        if (wrapper == null) {
            return;
        }
        animateBorder(wrapper, current, borderRes);
        boxBorderRes.set(index, borderRes);
    }

    private void animateBorder(View containerView, int fromRes, int toRes) {
        if (containerView == null) {
            return;
        }
        Drawable from = ContextCompat.getDrawable(context, fromRes);
        Drawable to = ContextCompat.getDrawable(context, toRes);
        if (from == null || to == null) {
            containerView.setBackgroundResource(toRes);
            return;
        }
        TransitionDrawable transition = new TransitionDrawable(new Drawable[]{from, to});
        containerView.setBackground(transition);
        transition.startTransition(BORDER_ANIMATION_MS);
    }

    private void shakeView(View view) {
        TranslateAnimation shake = new TranslateAnimation(0, 8, 0, 0);
        shake.setDuration(300);
        shake.setInterpolator(new CycleInterpolator(3));
        view.startAnimation(shake);
    }
}
