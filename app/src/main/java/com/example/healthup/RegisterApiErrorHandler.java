package com.example.healthup;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

final class RegisterApiErrorHandler {

    enum ErrorType {
        PHONE_ALREADY_EXISTS,
        EMAIL_ALREADY_EXISTS,
        ACCOUNT_ALREADY_EXISTS,
        INVALID_REQUEST,
        NETWORK_ERROR,
        TIMEOUT
    }

    private RegisterApiErrorHandler() {
    }

    static ErrorType mapException(Exception exception) {
        if (exception == null) {
            return ErrorType.INVALID_REQUEST;
        }

        if (exception instanceof FirebaseNetworkException
                || isCausedBy(exception, FirebaseNetworkException.class)
                || isCausedBy(exception, java.net.UnknownHostException.class)
                || isCausedBy(exception, java.io.IOException.class)) {
            return ErrorType.NETWORK_ERROR;
        }

        if (isCausedBy(exception, SocketTimeoutException.class)
                || isCausedBy(exception, TimeoutException.class)) {
            return ErrorType.TIMEOUT;
        }

        if (exception instanceof FirebaseAuthUserCollisionException) {
            return ErrorType.EMAIL_ALREADY_EXISTS;
        }

        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) exception;
            switch (firestoreException.getCode()) {
                case UNAVAILABLE:
                    return ErrorType.NETWORK_ERROR;
                case DEADLINE_EXCEEDED:
                    return ErrorType.TIMEOUT;
                default:
                    return ErrorType.INVALID_REQUEST;
            }
        }

        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("email") && lowerMessage.contains("already")) {
                return ErrorType.EMAIL_ALREADY_EXISTS;
            }
            if (lowerMessage.contains("network")
                    || lowerMessage.contains("unable to resolve host")
                    || lowerMessage.contains("connection")) {
                return ErrorType.NETWORK_ERROR;
            }
            if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
                return ErrorType.TIMEOUT;
            }
        }

        return ErrorType.INVALID_REQUEST;
    }

    private static boolean isCausedBy(Exception exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
