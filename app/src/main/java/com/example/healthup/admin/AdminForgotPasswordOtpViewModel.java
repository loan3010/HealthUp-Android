package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.healthup.util.Event;

public class AdminForgotPasswordOtpViewModel extends ViewModel {

    private static final String KEY_EMAIL = "email";
    private static final String KEY_ADMIN_UID = "admin_uid";

    public enum UiState {
        IDLE,
        LOADING,
        RESEND_SUCCESS,
        ERROR
    }

    public interface VerifyCallback {
        void onResult(@NonNull AdminPasswordResetRepository.VerifyOtpResult result);
    }

    private final AdminPasswordResetRepository repository;
    private final SavedStateHandle savedStateHandle;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<Event<String>> resendError = new MutableLiveData<>();
    private final MutableLiveData<String> debugOtp = new MutableLiveData<>();
    private int verifyGeneration;

    public AdminForgotPasswordOtpViewModel(@NonNull SavedStateHandle savedStateHandle) {
        this(savedStateHandle, new AdminPasswordResetRepository());
    }

    public AdminForgotPasswordOtpViewModel(
            @NonNull SavedStateHandle savedStateHandle,
            AdminPasswordResetRepository repository
    ) {
        this.savedStateHandle = savedStateHandle;
        this.repository = repository;
    }

    public void setEmail(String email) {
        savedStateHandle.set(KEY_EMAIL, email);
    }

    public String getEmail() {
        return savedStateHandle.get(KEY_EMAIL);
    }

    public void setAdminUid(String adminUid) {
        savedStateHandle.set(KEY_ADMIN_UID, adminUid);
    }

    public String getAdminUid() {
        return savedStateHandle.get(KEY_ADMIN_UID);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<Event<String>> getResendError() {
        return resendError;
    }

    public LiveData<String> getDebugOtp() {
        return debugOtp;
    }

    public void loadDebugOtp() {
        String adminUid = getAdminUid();
        if (adminUid == null) return;
        repository.getDebugOtp(adminUid, debugOtp::setValue);
    }

    public void verifyOtp(@NonNull String otp, @NonNull VerifyCallback callback) {
        String adminUid = getAdminUid();
        if (adminUid == null) {
            callback.onResult(AdminPasswordResetRepository.VerifyOtpResult.ERROR);
            return;
        }
        if (otp.length() != 6) {
            callback.onResult(AdminPasswordResetRepository.VerifyOtpResult.WRONG_OTP);
            return;
        }
        final int generation = ++verifyGeneration;
        repository.verifyOtp(adminUid, otp, result -> {
            if (generation != verifyGeneration) return;
            callback.onResult(result);
        });
    }

    public void resendOtp() {
        String email = getEmail();
        if (email == null) return;
        uiState.setValue(UiState.LOADING);
        repository.resendOtp(email, (result, otpForDebug, adminUid) -> {
            debugOtp.setValue(otpForDebug);
            if (result == AdminPasswordResetRepository.SendOtpResult.SUCCESS) {
                if (adminUid != null && !adminUid.isEmpty()) {
                    setAdminUid(adminUid);
                }
                uiState.postValue(UiState.RESEND_SUCCESS);
            } else {
                resendError.postValue(new Event<>("generic"));
                uiState.postValue(UiState.ERROR);
            }
        });
    }

    public void cancelPendingVerify() {
        verifyGeneration++;
    }

    public void resetToIdle() {
        uiState.setValue(UiState.IDLE);
    }
}
