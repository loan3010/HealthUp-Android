package com.example.healthup.account;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.auth.AppPasswordHelper;
import com.example.healthup.auth.UserProfileBuilder;
import com.example.healthup.util.AccountDisabledWatcher;
import com.example.healthup.util.AppEntryRouter;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.example.healthup.util.PhoneNormalizer;
import com.example.healthup.util.UserProfileResolver;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Quick sign-in for a locally saved account (password-based). Social accounts use provider UI.
 */
public final class AccountQuickLogin {

    public interface Callback {
        void onSuccess();

        void onWrongPassword();

        void onNeedManualLogin(@NonNull String message);

        void onError(@NonNull String message);
    }

    private AccountQuickLogin() {
    }

    public static void signIn(
            @NonNull Context context,
            @NonNull SavedAccount account,
            @NonNull Callback callback
    ) {
        String password = SavedAccountStore.getPassword(context, account.uid);
        if (TextUtils.isEmpty(password)) {
            if (account.isSocialProvider()) {
                callback.onNeedManualLogin("social");
            } else {
                callback.onNeedManualLogin("no_password");
            }
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(account.uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError("profile_missing");
                        return;
                    }
                    completePasswordLogin(context, doc, password, callback);
                })
                .addOnFailureListener(e ->
                        callback.onError(e.getMessage() != null ? e.getMessage() : "network"));
    }

    private static void completePasswordLogin(
            @NonNull Context context,
            @NonNull DocumentSnapshot profileDoc,
            @NonNull String password,
            @NonNull Callback callback
    ) {
        String phone = profileDoc.getString("phone");
        String authEmail = AppPasswordHelper.authEmailFromProfile(profileDoc);
        if (TextUtils.isEmpty(authEmail)) {
            String displayEmail = profileDoc.getString("displayEmail");
            if (UserProfileBuilder.isRealEmail(displayEmail)) {
                authEmail = displayEmail;
            }
        }
        if (TextUtils.isEmpty(authEmail) || TextUtils.isEmpty(phone)) {
            callback.onError("invalid_profile");
            return;
        }

        String normalizedPhone = PhoneNormalizer.normalize(phone);
        if (AppPasswordHelper.isAppPasswordMode(profileDoc)) {
            if (!AppPasswordHelper.matchesUserPassword(
                    password, profileDoc.getString(AppPasswordHelper.FIELD_PASSWORD_HASH))) {
                callback.onWrongPassword();
                return;
            }
            String authSecret = AppPasswordHelper.authSecretForPhone(normalizedPhone);
            signInAuthAndFinish(context, authEmail, authSecret, profileDoc, callback);
            return;
        }

        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(authEmail, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        if (ex instanceof FirebaseAuthInvalidCredentialsException
                                || ex instanceof FirebaseAuthInvalidUserException) {
                            callback.onWrongPassword();
                        } else {
                            callback.onError("login_failed");
                        }
                        return;
                    }
                    finishLogin(context, profileDoc, callback);
                });
    }

    private static void signInAuthAndFinish(
            @NonNull Context context,
            @NonNull String authEmail,
            @NonNull String authSecret,
            @NonNull DocumentSnapshot profileDoc,
            @NonNull Callback callback
    ) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(authEmail, authSecret)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onWrongPassword();
                        return;
                    }
                    finishLogin(context, profileDoc, callback);
                });
    }

    private static void finishLogin(
            @NonNull Context context,
            @NonNull DocumentSnapshot profileDoc,
            @NonNull Callback callback
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("no_user");
            return;
        }
        UserProfileResolver.syncProfileAfterLogin(user.getUid(), profileDoc);
        AccountSessionRecorder.recordFromProfile(context, user.getUid(), profileDoc, null);
        if (!(context instanceof android.app.Activity)) {
            callback.onSuccess();
            return;
        }
        android.app.Activity activity = (android.app.Activity) context;
        GuestCartManager.getInstance(context).mergeToFirestore(user.getUid(), () ->
                activity.runOnUiThread(() ->
                        AccountDisabledWatcher.checkBeforeEnterApp(activity, callback::onSuccess)));
    }

    public static void openMainAfterSwitch(@NonNull android.app.Activity activity) {
        if (activity.isFinishing()) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Runnable goMain = () -> {
            if (activity.isFinishing()) {
                return;
            }
            AppEntryRouter.navigateHomeAndFinish(activity);
        };
        if (user == null) {
            goMain.run();
            return;
        }
        GuestCartManager.getInstance(activity).mergeToFirestore(user.getUid(), () ->
                activity.runOnUiThread(() ->
                        AccountDisabledWatcher.checkBeforeEnterApp(activity, goMain)));
    }
}
