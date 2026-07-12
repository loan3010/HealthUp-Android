package com.example.healthup.util;

import com.example.healthup.ProductAdapter;
import com.google.firebase.auth.FirebaseAuth;

public final class GuestWishlistUiHelper {

    private GuestWishlistUiHelper() {
    }

    public static boolean isGuest() {
        return FirebaseAuth.getInstance().getCurrentUser() == null;
    }

    public static void applyTo(ProductAdapter adapter) {
        if (adapter != null) {
            adapter.setWishlistDisabled(isGuest());
        }
    }
}
