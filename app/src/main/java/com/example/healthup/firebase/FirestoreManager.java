package com.example.healthup.firebase;

public class FirestoreManager {
    private static FirestoreManager instance;

    private FirestoreManager() {}

    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }
}
