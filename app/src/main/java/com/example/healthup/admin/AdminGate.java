package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.util.StaffRoleHelper;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
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

    public static void verifyAdminFromServer(@NonNull RoleCallback callback) {
        String uid = currentUid();
        if (uid == null) {
            callback.onResult(false, null);
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> callback.onResult(StaffRoleHelper.isAdmin(resolveRole(doc)), resolveRole(doc)))
                .addOnFailureListener(e -> callback.onError(e.getMessage() != null ? e.getMessage() : "Không thể xác thực quyền admin"));
    }

    @Nullable
    private static String resolveRole(@Nullable DocumentSnapshot document) {
        return StaffRoleHelper.resolveRole(document);
    }

    public static Task<Void> requireAdminOrThrow() {
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid())
                .get(Source.SERVER)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw new IllegalStateException("Không thể xác thực quyền admin");
                    }
                    if (!StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(task.getResult()))) {
                        throw new IllegalStateException("Tài khoản không có quyền admin");
                    }
                    return com.google.android.gms.tasks.Tasks.forResult(null);
                });
    }
}
