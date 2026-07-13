package com.example.healthup.admin;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Map;

final class AdminAuthSyncHelper {

    private AdminAuthSyncHelper() {
    }

    static Task<Void> syncAfterPasswordReset(@NonNull String adminUid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("adminUid", adminUid);
        return FirebaseFunctions.getInstance()
                .getHttpsCallable("syncAdminAuthAfterReset")
                .call(payload)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return Tasks.forException(task.getException() != null
                                ? task.getException()
                                : new IllegalStateException("Auth sync failed"));
                    }
                    HttpsCallableResult result = task.getResult();
                    if (result == null || result.getData() == null) {
                        return Tasks.forException(new IllegalStateException("Auth sync empty response"));
                    }
                    return Tasks.forResult(null);
                });
    }
}
