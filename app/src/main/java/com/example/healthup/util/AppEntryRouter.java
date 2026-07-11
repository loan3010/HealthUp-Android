package com.example.healthup.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.healthup.MainActivity;
import com.example.healthup.admin.AdminActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

/**
 * Picks the correct home screen after launch or sign-in.
 * Admin accounts go to {@link AdminActivity}; everyone else uses the buyer app.
 */
public final class AppEntryRouter {

    public interface RouteCallback {
        void onReady(@NonNull Intent intent);
    }

    private AppEntryRouter() {
    }

    public static void resolveHomeIntent(@NonNull Context context, @NonNull RouteCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onReady(buyerHomeIntent(context));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get(Source.DEFAULT)
                .addOnSuccessListener(doc -> {
                    if (StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(doc))) {
                        callback.onReady(adminHomeIntent(context));
                    } else if (context instanceof Activity) {
                        callback.onReady(
                                CheckoutIntentHelper.buildPostAuthMainIntent((Activity) context));
                    } else {
                        callback.onReady(buyerHomeIntent(context));
                    }
                })
                .addOnFailureListener(e -> callback.onReady(buyerHomeIntent(context)));
    }

    /** Navigate to the correct home and finish the current screen (login, splash, etc.). */
    public static void navigateHomeAndFinish(@NonNull Activity activity) {
        resolveHomeIntent(activity, intent -> {
            activity.startActivity(intent);
            activity.finish();
        });
    }

    @NonNull
    private static Intent buyerHomeIntent(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    @NonNull
    private static Intent adminHomeIntent(@NonNull Context context) {
        Intent intent = new Intent(context, AdminActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}
