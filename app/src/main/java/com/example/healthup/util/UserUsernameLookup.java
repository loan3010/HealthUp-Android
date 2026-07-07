package com.example.healthup.util;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public final class UserUsernameLookup {

    public interface Callback {
        void onResult(boolean takenByOther);

        void onError(@NonNull Exception error);
    }

    private UserUsernameLookup() {
    }

    /**
     * Checks whether {@code username} is already used by another user.
     * Usernames are compared in lowercase for case-insensitive uniqueness.
     */
    public static void checkTakenByOther(
            @NonNull String username,
            @NonNull String excludeUid,
            @NonNull Callback callback
    ) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", username)
                .limit(2)
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean taken = false;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!excludeUid.equals(doc.getId())) {
                            taken = true;
                            break;
                        }
                    }
                    callback.onResult(taken);
                })
                .addOnFailureListener(callback::onError);
    }
}
