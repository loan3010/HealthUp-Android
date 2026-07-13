package com.example.healthup.admin;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.util.StaffRoleHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public final class AdminGate {

    public interface RoleCallback {
        void onResult(boolean isAdmin, @Nullable String role);

        void onError(@NonNull String message);
    }

    private AdminGate() {
    }

    @Nullable
    public static String currentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    /**
     * Admin may sign in with a synthetic Auth UID while the profile lives at
     * {@code users/{profileDocId}} — resolve via {@code user_sessions} first.
     */
    public static void verifyAdminFromServer(@NonNull RoleCallback callback) {
        String authUid = currentUid();
        if (authUid == null) {
            callback.onResult(false, null);
            return;
        }
        resolveProfileDocId(authUid)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forException(task.getException() != null
                                ? task.getException()
                                : new IllegalStateException("Không thể xác định hồ sơ admin"));
                    }
                    return FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(task.getResult())
                            .get(Source.SERVER);
                })
                .addOnSuccessListener(doc -> {
                    String role = StaffRoleHelper.resolveRole(doc);
                    callback.onResult(StaffRoleHelper.isAdmin(role), role);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Không thể xác thực quyền admin"));
    }

    public static Task<Void> requireAdminOrThrow() {
        String authUid = currentUid();
        if (authUid == null) {
            return Tasks.forException(new IllegalStateException("Chưa đăng nhập"));
        }
        return resolveProfileDocId(authUid)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forException(new IllegalStateException("Không thể xác thực quyền admin"));
                    }
                    return FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(task.getResult())
                            .get(Source.SERVER);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forException(new IllegalStateException("Không thể xác thực quyền admin"));
                    }
                    if (!StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(task.getResult()))) {
                        return Tasks.forException(
                                new IllegalStateException("Tài khoản không có quyền admin"));
                    }
                    return Tasks.forResult(null);
                });
    }

    @NonNull
    public static Task<String> resolveProfileDocId(@NonNull String authUid) {
        return FirebaseFirestore.getInstance()
                .collection("user_sessions")
                .document(authUid)
                .get(Source.SERVER)
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        String linked = task.getResult().getString("profileDocId");
                        if (!TextUtils.isEmpty(linked)) {
                            return Tasks.forResult(linked);
                        }
                    }
                    return Tasks.forResult(authUid);
                });
    }
}
