package com.example.healthup.util;

import androidx.annotation.Nullable;

/**
 * Wrapper for one-time LiveData events (navigation, errors, etc.).
 */
public class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    public T peekContent() {
        return content;
    }
}
