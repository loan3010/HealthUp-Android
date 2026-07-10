package com.example.healthup.admin;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.healthup.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class AdminDisableDialogHelper {

    public interface ConfirmCallback {
        void onConfirmed(@NonNull String reason);
    }

    private AdminDisableDialogHelper() {
    }

    public static void showLockReasonDialog(@NonNull Context context,
                                            @NonNull ConfirmCallback onConfirm,
                                            @NonNull Runnable onCancel) {
        showReasonDialog(
                context,
                R.string.admin_disable_reason_title,
                R.string.admin_disable_reason_message,
                R.string.admin_disable_reason_hint,
                R.string.admin_confirm_lock,
                R.string.admin_disable_reason_required,
                onConfirm,
                onCancel
        );
    }

    public static void showUnlockReasonDialog(@NonNull Context context,
                                              @NonNull ConfirmCallback onConfirm,
                                              @NonNull Runnable onCancel) {
        showReasonDialog(
                context,
                R.string.admin_unlock_reason_title,
                R.string.admin_unlock_reason_message,
                R.string.admin_unlock_reason_hint,
                R.string.admin_confirm_unlock,
                R.string.admin_unlock_reason_required,
                onConfirm,
                onCancel
        );
    }

    private static void showReasonDialog(@NonNull Context context,
                                         int titleRes,
                                         int messageRes,
                                         int hintRes,
                                         int confirmRes,
                                         int requiredToastRes,
                                         @NonNull ConfirmCallback onConfirm,
                                         @NonNull Runnable onCancel) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_admin_disable_reason, null);
        TextInputEditText etReason = view.findViewById(R.id.etDisableReason);
        TextInputLayout layoutReason = view.findViewById(R.id.layoutDisableReason);
        if (layoutReason != null) {
            layoutReason.setHint(context.getString(hintRes));
        }
        TextView tvMessage = view.findViewById(R.id.tvDisableReasonMessage);
        if (tvMessage != null) {
            tvMessage.setText(messageRes);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(confirmRes, null)
                .setNegativeButton(android.R.string.cancel, (d, w) -> onCancel.run())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = etReason.getText() != null ? etReason.getText().toString().trim() : "";
            if (TextUtils.isEmpty(reason)) {
                Toast.makeText(context, requiredToastRes, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            onConfirm.onConfirmed(reason);
        }));
        dialog.show();
    }
}
