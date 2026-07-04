package com.example.healthup.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class FirestoreRepository {

    private final FirebaseFirestore firestore;

    public FirestoreRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public FirestoreRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public Task<QuerySnapshot> findUserByPhone(@NonNull String phone) {
        return firestore.collection("users")
                .whereEqualTo("phone", phone)
                .limit(1)
                .get();
    }

    public Task<DocumentSnapshot> getPasswordResetDoc(@NonNull String phone) {
        return firestore.collection("password_reset")
                .document(phone)
                .get();
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }
}
