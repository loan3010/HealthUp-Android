package com.example.healthup.ui.notify;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.example.healthup.R;
import com.google.android.material.button.MaterialButton;

public class NotifyPermissionDialogFragment extends DialogFragment {

    public static final String TAG = "NotifyPermissionDialog";

    public interface Listener {
        void onAllow();

        void onDecline();
    }

    private Listener listener;

    public static void show(@NonNull FragmentManager fragmentManager, @NonNull Listener listener) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }
        NotifyPermissionDialogFragment dialog = new NotifyPermissionDialogFragment();
        dialog.listener = listener;
        dialog.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_HealthUp_Dialog_Transparent);
        setCancelable(false);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.dialog_notify_permission, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton allowButton = view.findViewById(R.id.notifyPermissionAllowButton);
        View laterButton = view.findViewById(R.id.notifyPermissionLaterButton);

        allowButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAllow();
            }
            dismissAllowingStateLoss();
        });

        laterButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDecline();
            }
            dismissAllowingStateLoss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) {
            return;
        }
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }
}
