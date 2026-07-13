package com.example.healthup.auth;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.healthup.account.AccountQuickLogin;
import com.example.healthup.account.AccountSessionRecorder;
import com.example.healthup.BuildConfig;
import com.example.healthup.R;
import com.example.healthup.util.AppEntryRouter;
import com.example.healthup.util.CheckoutIntentHelper;
import com.example.healthup.util.GuestCartManager;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;

public class SocialAuthHelper {

    private static final String TAG = "SocialAuthHelper";

    public interface Listener {
        void onLoadingChanged(boolean loading);

        void onError(@NonNull String message);
    }

    private final AppCompatActivity activity;
    private final Listener listener;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firebaseFirestore;
    private final CallbackManager facebookCallbackManager;
    private final ActivityResultLauncher<Intent> googleSignInLauncher;
    @Nullable
    private final GoogleSignInClient googleSignInClient;
    @Nullable
    private final String webClientId;
    @Nullable
    private String expectedSwitchUid;
    @Nullable
    private String preferredGoogleEmail;

    public SocialAuthHelper(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firebaseFirestore = FirebaseFirestore.getInstance();
        this.facebookCallbackManager = CallbackManager.Factory.create();
        this.webClientId = GoogleWebClientIdResolver.resolve(activity);

        if (webClientId != null) {
            GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            this.googleSignInClient = GoogleSignIn.getClient(activity, googleSignInOptions);
        } else {
            this.googleSignInClient = null;
            logDebug("Google Sign-In disabled: no valid Web client ID (check SHA-1 + google-services.json)");
        }

        this.googleSignInLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        setLoading(false);
                        clearSwitchTarget();
                        // Google often returns RESULT_CANCELED for config errors (SHA-1 mismatch).
                        if (result.getData() != null && tryReportGoogleIntentError(result.getData())) {
                            return;
                        }
                        if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            notifyError(activity.getString(R.string.social_auth_google_cancelled));
                        } else {
                            notifyError(withDebugDetail(
                                    activity.getString(R.string.social_auth_google_failed),
                                    "resultCode=" + result.getResultCode()
                            ));
                        }
                        return;
                    }
                    if (result.getData() == null) {
                        setLoading(false);
                        clearSwitchTarget();
                        notifyError(withDebugDetail(
                                activity.getString(R.string.social_auth_google_failed),
                                "sign-in intent returned null data"
                        ));
                        return;
                    }
                    handleGoogleSignInResult(result.getData());
                }
        );

        registerFacebookCallback();
    }

    public void signInWithGoogle() {
        if (googleSignInClient == null || webClientId == null) {
            notifyError(activity.getString(R.string.social_auth_google_not_configured));
            return;
        }

        clearSwitchTarget();
        setLoading(true);
        googleSignInClient.signOut().addOnCompleteListener(task ->
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    /**
     * Quick account switch: try silent Google sign-in for the saved email before showing UI.
     */
    public void signInWithGoogleForSwitch(
            @Nullable String preferredEmail,
            @NonNull String expectedUid
    ) {
        if (googleSignInClient == null || webClientId == null) {
            notifyError(activity.getString(R.string.social_auth_google_not_configured));
            return;
        }

        expectedSwitchUid = expectedUid;
        preferredGoogleEmail = preferredEmail;
        GoogleSignInClient switchClient = buildGoogleSignInClient(preferredEmail);
        setLoading(true);
        switchClient.silentSignIn().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                GoogleSignInAccount account = task.getResult();
                if (account != null && !TextUtils.isEmpty(account.getIdToken())) {
                    PendingGoogleLink.set(account.getIdToken(), account.getEmail());
                    signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null));
                    return;
                }
            }
            googleSignInLauncher.launch(switchClient.getSignInIntent());
        });
    }

    @NonNull
    private GoogleSignInClient buildGoogleSignInClient(@Nullable String accountEmail) {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail();
        if (!TextUtils.isEmpty(accountEmail)) {
            builder.setAccountName(accountEmail);
        }
        return GoogleSignIn.getClient(activity, builder.build());
    }

    private void clearSwitchTarget() {
        expectedSwitchUid = null;
        preferredGoogleEmail = null;
    }

    public void signInWithFacebook() {
        setLoading(true);
        LoginManager.getInstance().logInWithReadPermissions(
                activity,
                Arrays.asList("email", "public_profile")
        );
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    private void registerFacebookCallback() {
        LoginManager.getInstance().registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                AccessToken token = loginResult.getAccessToken();
                if (token == null || TextUtils.isEmpty(token.getToken())) {
                    setLoading(false);
                    notifyError(activity.getString(R.string.social_auth_failed));
                    return;
                }
                signInWithCredential(FacebookAuthProvider.getCredential(token.getToken()));
            }

            @Override
            public void onCancel() {
                setLoading(false);
                clearSwitchTarget();
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                setLoading(false);
                clearSwitchTarget();
                notifyError(withDebugDetail(
                        activity.getString(R.string.social_auth_facebook_failed),
                        error.getMessage()
                ));
            }
        });
    }

    /** @return true if an ApiException was reported to the user */
    private boolean tryReportGoogleIntentError(@NonNull Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            task.getResult(ApiException.class);
            return false;
        } catch (ApiException e) {
            logDebug("Google Sign-In ApiException from non-OK result", e);
            notifyError(formatGoogleApiError(e));
            return true;
        }
    }

    private void handleGoogleSignInResult(@NonNull Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || TextUtils.isEmpty(account.getIdToken())) {
                setLoading(false);
                clearSwitchTarget();
                logDebug("Google account selected but idToken is empty. webClientId configured="
                        + GoogleWebClientIdResolver.isValidClientId(webClientId));
                notifyError(activity.getString(R.string.social_auth_google_no_id_token));
                return;
            }
            PendingGoogleLink.set(account.getIdToken(), account.getEmail());
            signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null));
        } catch (ApiException e) {
            setLoading(false);
            clearSwitchTarget();
            logDebug("Google Sign-In ApiException", e);
            notifyError(formatGoogleApiError(e));
        }
    }

    private void signInWithCredential(@NonNull AuthCredential credential) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        clearSwitchTarget();
                        Exception exception = task.getException();
                        logDebug("Firebase signInWithCredential failed", exception);
                        if (isAccountExistsWithDifferentCredential(exception)) {
                            PendingGoogleLink.clear();
                            notifyError(activity.getString(R.string.social_auth_email_exists_use_password));
                            return;
                        }
                        notifyError(withDebugDetail(
                                activity.getString(R.string.social_auth_failed),
                                exception == null ? null : exception.getMessage()
                        ));
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        clearSwitchTarget();
                        notifyError(activity.getString(R.string.social_auth_failed));
                        return;
                    }
                    routeAfterSocialAuth(user);
                });
    }

    private boolean isAccountExistsWithDifferentCredential(@Nullable Exception exception) {
        if (exception == null) {
            return false;
        }
        if (exception instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            return true;
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("account-exists-with-different-credential")
                || lower.contains("already in use")
                || lower.contains("email address is already");
    }

    private void routeAfterSocialAuth(@NonNull FirebaseUser user) {
        String authProvider = resolveAuthProvider(user);
        firebaseFirestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> handleProfileCheck(user, authProvider, doc))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    clearSwitchTarget();
                    logDebug("Firestore profile check failed", e);
                    notifyError(withDebugDetail(
                            activity.getString(R.string.social_auth_profile_check_failed),
                            e.getMessage()
                    ));
                });
    }

    private void handleProfileCheck(
            @NonNull FirebaseUser user,
            @NonNull String authProvider,
            @NonNull DocumentSnapshot doc
    ) {
        String phone = doc.exists() ? doc.getString("phone") : null;
        if (doc.exists() && !TextUtils.isEmpty(phone)) {
            completeAuthenticatedSession(user);
            return;
        }
        if (expectedSwitchUid != null && doc.exists() && expectedSwitchUid.equals(user.getUid())) {
            completeAuthenticatedSession(user);
            return;
        }

        // Case 7 (revised): Gmail matches existing phone/password profile → OTP phone → password → link.
        maybeLinkExistingAccountByEmail(user, authProvider, doc);
    }

    private void completeAuthenticatedSession(@NonNull FirebaseUser user) {
        if (expectedSwitchUid != null && !expectedSwitchUid.equals(user.getUid())) {
            setLoading(false);
            clearSwitchTarget();
            firebaseAuth.signOut();
            notifyError(activity.getString(R.string.account_management_switch_wrong_account));
            return;
        }

        final boolean switchingAccount = expectedSwitchUid != null;
        clearSwitchTarget();
        PendingGoogleLink.clear();
        AccountSessionRecorder.fetchAndRecord(activity, user.getUid(), null,
                new AccountSessionRecorder.Listener() {
                    @Override
                    public void onRecorded() {
                        setLoading(false);
                        Toast.makeText(activity, R.string.login_success, Toast.LENGTH_SHORT).show();
                        GuestCartManager.getInstance(activity).mergeToFirestore(user.getUid(), () ->
                                activity.runOnUiThread(() -> {
                                    if (switchingAccount) {
                                        AccountQuickLogin.openMainAfterSwitch(activity);
                                    } else if (activity instanceof android.app.Activity) {
                                        AppEntryRouter.navigateHomeAndFinish(
                                                (android.app.Activity) activity);
                                    } else {
                                        activity.startActivity(
                                                CheckoutIntentHelper.buildPostAuthMainIntent(activity));
                                        activity.finish();
                                    }
                                }));
                    }

                    @Override
                    public void onRejectedAccountLimit() {
                        setLoading(false);
                        firebaseAuth.signOut();
                        Toast.makeText(activity, R.string.account_management_full_blocked,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void maybeLinkExistingAccountByEmail(
            @NonNull FirebaseUser user,
            @NonNull String authProvider,
            @NonNull DocumentSnapshot currentDoc
    ) {
        String socialEmail = user.getEmail();
        if (TextUtils.isEmpty(socialEmail)) {
            socialEmail = PendingGoogleLink.getEmail();
        }
        if (TextUtils.isEmpty(socialEmail) || UserProfileBuilder.isSyntheticAuthEmail(socialEmail)) {
            openCompleteProfile(user, authProvider, currentDoc);
            return;
        }

        final String normalized = socialEmail.trim().toLowerCase(java.util.Locale.ROOT);
        final String currentUid = user.getUid();

        firebaseFirestore.collection("users")
                .whereEqualTo("displayEmail", normalized)
                .limit(1)
                .get()
                .addOnSuccessListener(byDisplay -> {
                    if (!byDisplay.isEmpty()) {
                        DocumentSnapshot existing = byDisplay.getDocuments().get(0);
                        if (!currentUid.equals(existing.getId())) {
                            handleEmailMatchForLink(user, authProvider, existing, normalized);
                            return;
                        }
                    }
                    firebaseFirestore.collection("users")
                            .whereEqualTo("email", normalized)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(byEmail -> {
                                if (!byEmail.isEmpty()) {
                                    DocumentSnapshot existing = byEmail.getDocuments().get(0);
                                    if (!currentUid.equals(existing.getId())
                                            && UserProfileBuilder.isRealEmail(existing.getString("email"))) {
                                        handleEmailMatchForLink(user, authProvider, existing, normalized);
                                        return;
                                    }
                                }
                                openCompleteProfile(user, authProvider, currentDoc);
                            })
                            .addOnFailureListener(e -> openCompleteProfile(user, authProvider, currentDoc));
                })
                .addOnFailureListener(e -> openCompleteProfile(user, authProvider, currentDoc));
    }

    private void handleEmailMatchForLink(
            @NonNull FirebaseUser tempGoogleUser,
            @NonNull String authProvider,
            @NonNull DocumentSnapshot existing,
            @NonNull String googleEmail
    ) {
        Boolean linked = existing.getBoolean("googleLinked");
        if (linked != null && linked) {
            // Already Google-linked under another Auth UID — cannot attach a second Google here.
            rejectSocialEmailConflict(tempGoogleUser);
            return;
        }

        String phone = existing.getString("phone");
        String existingAuthEmail = existing.getString("email");
        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(existingAuthEmail)) {
            rejectSocialEmailConflict(tempGoogleUser);
            return;
        }
        if (!PendingGoogleLink.hasPending()) {
            setLoading(false);
            notifyError(activity.getString(R.string.social_link_session_invalid));
            return;
        }

        String fullName = existing.getString("fullName");
        if (TextUtils.isEmpty(fullName)) {
            fullName = tempGoogleUser.getDisplayName();
        }
        if (TextUtils.isEmpty(fullName)) {
            fullName = "";
        }

        setLoading(false);
        Toast.makeText(activity, R.string.social_link_email_match_continue, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(activity, com.example.healthup.OTPActivity.class);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_FULL_NAME, fullName);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_PHONE, phone);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_EMAIL, googleEmail);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_PASSWORD, "");
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_IS_SOCIAL_AUTH, true);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_AUTH_PROVIDER, authProvider);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_LINK_EXISTING_ACCOUNT, true);
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_EXISTING_UID, existing.getId());
        intent.putExtra(com.example.healthup.OTPActivity.EXTRA_EXISTING_AUTH_EMAIL, existingAuthEmail);
        activity.startActivity(intent);
        activity.finish();
    }

    /** Conflict when email/phone already tied to another Google-linked account. */
    private void rejectSocialEmailConflict(@NonNull FirebaseUser tempGoogleUser) {
        PendingGoogleLink.clear();
        String message = activity.getString(R.string.social_auth_email_exists_use_password);
        tempGoogleUser.delete()
                .addOnCompleteListener(task -> {
                    firebaseAuth.signOut();
                    setLoading(false);
                    notifyError(message);
                });
    }

    private void openCompleteProfile(
            @NonNull FirebaseUser user,
            @NonNull String authProvider,
            @NonNull DocumentSnapshot doc
    ) {
        setLoading(false);

        String displayName = user.getDisplayName();
        if (TextUtils.isEmpty(displayName) && doc.exists()) {
            displayName = doc.getString("fullName");
        }

        Intent intent = new Intent(activity, SocialCompleteProfileActivity.class);
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_FULL_NAME, displayName == null ? "" : displayName);
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_AUTH_EMAIL, user.getEmail() == null ? "" : user.getEmail());
        intent.putExtra(SocialCompleteProfileActivity.EXTRA_AUTH_PROVIDER, authProvider);
        activity.startActivity(intent);
        activity.finish();
    }

    @NonNull
    private String resolveAuthProvider(@NonNull FirebaseUser user) {
        if (user.getProviderData().isEmpty()) {
            return UserProfileBuilder.AUTH_PROVIDER_GOOGLE;
        }
        String providerId = user.getProviderData().get(user.getProviderData().size() - 1).getProviderId();
        if (FacebookAuthProvider.PROVIDER_ID.equals(providerId)) {
            return UserProfileBuilder.AUTH_PROVIDER_FACEBOOK;
        }
        return UserProfileBuilder.AUTH_PROVIDER_GOOGLE;
    }

    @NonNull
    private String formatGoogleApiError(@NonNull ApiException e) {
        if (e.getStatusCode() == CommonStatusCodes.DEVELOPER_ERROR) {
            return activity.getString(R.string.social_auth_google_developer_error);
        }
        if (e.getStatusCode() == 12501) {
            return activity.getString(R.string.social_auth_google_cancelled);
        }

        String base = activity.getString(R.string.social_auth_google_failed);
        if (!BuildConfig.DEBUG) {
            return base;
        }

        String statusLabel = googleStatusLabel(e.getStatusCode());
        String detail = e.getStatusCode() + " " + statusLabel;
        if (!TextUtils.isEmpty(e.getMessage())) {
            detail += ": " + e.getMessage();
        }
        return base + " (" + detail + ")";
    }

    @NonNull
    private String googleStatusLabel(int statusCode) {
        switch (statusCode) {
            case CommonStatusCodes.NETWORK_ERROR:
                return "NETWORK_ERROR";
            case CommonStatusCodes.INTERNAL_ERROR:
                return "INTERNAL_ERROR";
            case CommonStatusCodes.INVALID_ACCOUNT:
                return "INVALID_ACCOUNT";
            case CommonStatusCodes.SIGN_IN_REQUIRED:
                return "SIGN_IN_REQUIRED";
            case CommonStatusCodes.DEVELOPER_ERROR:
                return "DEVELOPER_ERROR";
            case 12501:
                return "SIGN_IN_CANCELLED";
            case 12500:
                return "SIGN_IN_FAILED";
            default:
                return "status";
        }
    }

    @NonNull
    private String withDebugDetail(@NonNull String base, @Nullable String detail) {
        if (!BuildConfig.DEBUG || TextUtils.isEmpty(detail)) {
            return base;
        }
        return base + " (" + detail + ")";
    }

    private void logDebug(@NonNull String message) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message);
        }
    }

    private void logDebug(@NonNull String message, @Nullable Throwable throwable) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) {
                Log.w(TAG, message);
            } else {
                Log.w(TAG, message, throwable);
            }
        }
    }

    private void setLoading(boolean loading) {
        listener.onLoadingChanged(loading);
    }

    private void notifyError(@NonNull String message) {
        listener.onError(message);
    }
}
