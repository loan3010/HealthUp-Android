package com.example.healthup.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.LoginActivity;
import com.example.healthup.MainActivity;
import com.example.healthup.admin.AdminActivity;
import com.example.healthup.admin.AdminGate;
import com.example.healthup.auth.BuyerAccountGate;
import com.example.healthup.auth.IncompleteSocialSessionCleaner;
import com.example.healthup.auth.SocialCompleteProfileActivity;
import com.example.healthup.auth.UserProfileBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

/**
 * Picks the correct home screen after launch or sign-in.
 * Admin accounts go to {@link AdminActivity}; buyers need a phone-verified
 * Firestore profile — Auth alone (e.g. abandoned Google signup) is not enough.
 */
public final class AppEntryRouter {

    /** When set, {@link com.example.healthup.MainActivity} will not auto-redirect admins to admin home. */
    public static final String EXTRA_SKIP_ADMIN_REDIRECT = "extra_skip_admin_redirect";

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

        final String authUid = user.getUid();
        AdminGate.resolveProfileDocId(authUid)
                .continueWithTask(task -> {
                    String profileId = (task.isSuccessful() && task.getResult() != null)
                            ? task.getResult()
                            : authUid;
                    return FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(profileId)
                            .get(Source.DEFAULT);
                })
                .addOnSuccessListener(doc -> routeAfterProfile(context, user, doc, callback))
                .addOnFailureListener(e -> callback.onReady(buyerHomeIntent(context)));
    }

    private static void routeAfterProfile(
            @NonNull Context context,
            @NonNull FirebaseUser user,
            @Nullable DocumentSnapshot doc,
            @NonNull RouteCallback callback
    ) {
        if (StaffRoleHelper.isAdmin(StaffRoleHelper.resolveRole(doc))) {
            callback.onReady(adminHomeIntent(context));
            return;
        }
        // Staff/seller dashboards still need a real users/{uid} (usually phone-verified).
        if (BuyerAccountGate.isPhoneVerifiedProfile(doc)
                || StaffRoleHelper.isStaff(StaffRoleHelper.resolveRole(doc))) {
            deliverBuyerHome(context, callback);
            return;
        }

        // Auth session without OTP-completed users/{uid} — not a real account yet.
        if (BuyerAccountGate.isSocialAuthUser(user)) {
            callback.onReady(completeProfileIntent(context, user));
            return;
        }

        IncompleteSocialSessionCleaner.cleanup(() ->
                callback.onReady(loginIntent(context)));
    }

    private static void deliverBuyerHome(@NonNull Context context, @NonNull RouteCallback callback) {
        if (context instanceof Activity) {
            callback.onReady(CheckoutIntentHelper.buildPostAuthMainIntent((Activity) context));
        } else {
            callback.onReady(buyerHomeIntent(context));
        }
    }

    @NonNull
    private static Intent completeProfileIntent(@NonNull Context context, @NonNull FirebaseUser user) {
        Intent intent = new Intent(context, SocialCompleteProfileActivity.class);
        String displayName = user.getDisplayName();
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_FULL_NAME,
                displayName == null ? "" : displayName);
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_AUTH_EMAIL,
                user.getEmail() == null ? "" : user.getEmail());
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_AUTH_PROVIDER,
                UserProfileBuilder.AUTH_PROVIDER_GOOGLE);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    @NonNull
    private static Intent loginIntent(@NonNull Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    /** Navigate to the correct home and finish the current screen (login, splash, etc.). */
    public static void navigateHomeAndFinish(@NonNull Activity activity) {
        resolveHomeIntent(activity, intent -> {
            activity.startActivity(intent);
            activity.finish();
        });
    }

    /** Open admin dashboard and clear login screens from the task stack. */
    public static void navigateAdminHomeAndFinish(@NonNull Activity activity) {
        activity.startActivity(adminHomeIntent(activity));
        activity.finish();
    }

    @NonNull
    public static Intent buyerHomeIntentSkippingAdminRedirect(@NonNull Context context) {
        Intent intent = buyerHomeIntent(context);
        intent.putExtra(EXTRA_SKIP_ADMIN_REDIRECT, true);
        return intent;
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
