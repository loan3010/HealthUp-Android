package com.example.healthup;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseException;

public final class FirebaseAuthErrorMapper {

    private FirebaseAuthErrorMapper() {
    }

    @NonNull
    public static String map(@Nullable Exception exception) {
        if (exception == null) {
            return "generic";
        }

        String message = exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        if (lower.contains("not_found")
                || lower.contains("not-found")
                || lower.contains("does not exist")
                || lower.contains("404")) {
            return "function_not_deployed";
        }

        if (lower.contains("sign-up provider is disabled")
                || lower.contains("sign-in provider is disabled")) {
            return "provider_disabled";
        }
        if (lower.contains("requires recent authentication")) {
            return "recent_auth_required";
        }
        if (lower.contains("weak password")) {
            return "weak_password";
        }
        if (lower.contains("user not found") || lower.contains("no user record")) {
            return "user_not_found";
        }
        if (lower.contains("network")) {
            return "network";
        }

        if (exception instanceof FirebaseException && !TextUtils.isEmpty(message)) {
            return message;
        }

        return "generic";
    }
}
