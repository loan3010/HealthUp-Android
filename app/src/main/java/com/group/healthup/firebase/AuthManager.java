package com.example.healthup.firebase;

public class AuthManager {
    private static AuthManager instance;

    private AuthManager() {}

    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }
}
