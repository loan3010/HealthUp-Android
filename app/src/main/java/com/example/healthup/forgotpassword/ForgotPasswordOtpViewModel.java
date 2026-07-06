package com.example.healthup.forgotpassword;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.healthup.data.repository.PasswordResetRepository;
import com.example.healthup.util.Event;

public class ForgotPasswordOtpViewModel extends ViewModel {

    private static final String KEY_PHONE = "phone";

    public enum UiState {
        IDLE,
        LOADING,
        RESEND_SUCCESS,
        ERROR
    }

    public interface VerifyCallback {
        void onResult(@NonNull PasswordResetRepository.VerifyOtpResult result);
    }

    private final PasswordResetRepository repository;
    private final SavedStateHandle savedStateHandle;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<Event<String>> resendError = new MutableLiveData<>();
    private final MutableLiveData<String> debugOtp = new MutableLiveData<>();
    private int verifyGeneration;

    public ForgotPasswordOtpViewModel(@NonNull SavedStateHandle savedStateHandle) {
        this(savedStateHandle, new PasswordResetRepository());
    }

    public ForgotPasswordOtpViewModel(
            @NonNull SavedStateHandle savedStateHandle,
            PasswordResetRepository repository
    ) {
        this.savedStateHandle = savedStateHandle;
        this.repository = repository;
    }

    public void setPhone(String phone) {
        savedStateHandle.set(KEY_PHONE, phone);
    }

    public String getPhone() {
        return savedStateHandle.get(KEY_PHONE);
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
        String phone = getPhone();
        if (phone == null) {
            return;
        }
        repository.getDebugOtp(phone, debugOtp::setValue);
    }

    /**
     * Verify OTP — does not touch uiState so Activity stays on screen and shows inline errors.
     */
    public void verifyOtp(@NonNull String otp, @NonNull VerifyCallback callback) {
        String phone = getPhone();
        if (phone == null) {
            callback.onResult(PasswordResetRepository.VerifyOtpResult.ERROR);
            return;
        }

        if (otp.length() != 6) {
            callback.onResult(PasswordResetRepository.VerifyOtpResult.WRONG_OTP);
            return;
        }

        final int generation = ++verifyGeneration;
        repository.verifyOtp(phone, otp, result -> {
            if (generation != verifyGeneration) {
                return;
            }
            callback.onResult(result);
        });
    }

    public void resendOtp() {
        String phone = getPhone();
        if (phone == null) {
            return;
        }
        uiState.setValue(UiState.LOADING);

        repository.resendOtp(phone, (result, otpForDebug) -> {
            debugOtp.setValue(otpForDebug);
            switch (result) {
                case SUCCESS:
                    uiState.postValue(UiState.RESEND_SUCCESS);
                    break;
                case PHONE_NOT_REGISTERED:
                case ERROR:
                default:
                    resendError.postValue(new Event<>("generic"));
                    uiState.postValue(UiState.ERROR);
                    break;
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
