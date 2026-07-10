package com.example.healthup;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseException;
import com.google.firebase.functions.FirebaseFunctionsException;

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

        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException fn = (FirebaseFunctionsException) exception;
            String detail = lower;
            switch (fn.getCode()) {
                case NOT_FOUND:
                    if (detail.contains("user") || detail.contains("email")) {
                        return "user_not_found";
                    }
                    return "function_not_deployed";
                case FAILED_PRECONDITION:
                    if (detail.contains("otp") || detail.contains("verified")) {
                        return "session_invalid";
                    }
                    return "session_invalid";
                case INVALID_ARGUMENT:
                    if (detail.contains("password") || detail.contains("short")) {
                        return "weak_password";
                    }
                    return "invalid_request";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "network";
                case INTERNAL:
                    return "generic";
                case PERMISSION_DENIED:
                case UNAUTHENTICATED:
                    return "function_not_deployed";
                default:
                    break;
            }
        }

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
