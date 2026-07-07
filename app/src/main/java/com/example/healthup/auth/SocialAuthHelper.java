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

import com.example.healthup.BuildConfig;
import com.example.healthup.R;
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

        setLoading(true);
        googleSignInClient.signOut().addOnCompleteListener(task ->
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
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
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                setLoading(false);
                notifyError(withDebugDetail(
                        activity.getString(R.string.social_auth_facebook_failed),
                        error.getMessage()
                ));
            }
        });
    }

    private void handleGoogleSignInResult(@NonNull Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || TextUtils.isEmpty(account.getIdToken())) {
                setLoading(false);
                logDebug("Google account selected but idToken is empty. webClientId configured="
                        + GoogleWebClientIdResolver.isValidClientId(webClientId));
                notifyError(activity.getString(R.string.social_auth_google_no_id_token));
                return;
            }
            signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null));
        } catch (ApiException e) {
            setLoading(false);
            logDebug("Google Sign-In ApiException", e);
            notifyError(formatGoogleApiError(e));
        }
    }

    private void signInWithCredential(@NonNull AuthCredential credential) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        Exception exception = task.getException();
                        logDebug("Firebase signInWithCredential failed", exception);
                        notifyError(withDebugDetail(
                                activity.getString(R.string.social_auth_failed),
                                exception == null ? null : exception.getMessage()
                        ));
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        notifyError(activity.getString(R.string.social_auth_failed));
                        return;
                    }
                    routeAfterSocialAuth(user);
                });
    }

    private void routeAfterSocialAuth(@NonNull FirebaseUser user) {
        String authProvider = resolveAuthProvider(user);
        firebaseFirestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> handleProfileCheck(user, authProvider, doc))
                .addOnFailureListener(e -> {
                    setLoading(false);
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
        setLoading(false);

        String phone = doc.exists() ? doc.getString("phone") : null;
        if (doc.exists() && !TextUtils.isEmpty(phone)) {
            Toast.makeText(activity, R.string.login_success, Toast.LENGTH_SHORT).show();
            GuestCartManager.getInstance(activity).mergeToFirestore(user.getUid(), () ->
                    activity.runOnUiThread(() -> {
                        activity.startActivity(CheckoutIntentHelper.buildPostAuthMainIntent(activity));
                        activity.finish();
                    }));
            return;
        }

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
