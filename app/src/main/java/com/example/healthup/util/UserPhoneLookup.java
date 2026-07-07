package com.example.healthup.util;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

public final class UserPhoneLookup {

    public interface Callback {
        void onResult(boolean exists);

        void onError(Exception error);
    }

    private UserPhoneLookup() {
    }

    public static void checkExists(String phone, Callback callback) {
        queryUsers(phone)
                .addOnSuccessListener(snapshot -> callback.onResult(!snapshot.isEmpty()))
                .addOnFailureListener(callback::onError);
    }

    public static Task<QuerySnapshot> queryUsers(String phone) {
        List<String> variants = PhoneNormalizer.getLookupVariants(phone);
        if (variants.isEmpty()) {
            return FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("phone", "__invalid__")
                    .limit(1)
                    .get();
        }

        if (variants.size() == 1) {
            return FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("phone", variants.get(0))
                    .limit(1)
                    .get();
        }

        return FirebaseFirestore.getInstance().collection("users")
                .whereIn("phone", variants)
                .limit(1)
                .get();
    }
}
