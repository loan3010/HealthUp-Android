package com.example.healthup.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Locale;

public final class AdminEmailLookup {

    private AdminEmailLookup() {
    }

    @NonNull
    public static String normalize(@NonNull String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(@NonNull String email) {
        return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static Task<QueryDocumentSnapshot> findAdminByEmail(@NonNull String email) {
        String normalized = normalize(email);
        if (!isValidEmail(normalized)) {
            return Tasks.forResult(null);
        }

        return queryField("email", normalized)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return Tasks.forException(task.getException());
                    }
                    QueryDocumentSnapshot found = task.getResult();
                    if (found != null) {
                        return Tasks.forResult(found);
                    }
                    return queryField("displayEmail", normalized);
                });
    }

    private static Task<QueryDocumentSnapshot> queryField(@NonNull String field, @NonNull String value) {
        return FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo(field, value)
                .limit(10)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return null;
                    }
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        if (doc instanceof QueryDocumentSnapshot && isAdminDoc(doc)) {
                            return (QueryDocumentSnapshot) doc;
                        }
                    }
                    return null;
                });
    }

    private static boolean isAdminDoc(@NonNull DocumentSnapshot doc) {
        return StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(doc));
    }

    @Nullable
    public static String resolveAuthEmail(@NonNull DocumentSnapshot doc, @NonNull String fallbackEmail) {
        String email = doc.getString("email");
        if (!TextUtils.isEmpty(email)) {
            return normalize(email);
        }
        String displayEmail = doc.getString("displayEmail");
        if (!TextUtils.isEmpty(displayEmail)) {
            return normalize(displayEmail);
        }
        return normalize(fallbackEmail);
    }
}
