package com.example.healthup.forgotpassword;

import android.app.Activity;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.PhoneNumberUtils;
import com.example.healthup.RegisterValidator;
import com.example.healthup.data.repository.PasswordResetRepository;
import com.example.healthup.util.Event;

public class ResetPasswordViewModel extends ViewModel {

    public enum UiState {
        IDLE,
        LOADING,
        WAITING_SMS,
        SUCCESS,
        ERROR
    }

    private final PasswordResetRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> confirmPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> generalError = new MutableLiveData<>();
    private final MutableLiveData<Event<Void>> smsCodeRequiredEvent = new MutableLiveData<>();
    private String phone;

    public ResetPasswordViewModel() {
        this(new PasswordResetRepository());
    }

    public ResetPasswordViewModel(PasswordResetRepository repository) {
        this.repository = repository;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getPasswordError() {
        return passwordError;
    }

    public LiveData<String> getConfirmPasswordError() {
        return confirmPasswordError;
    }

    public LiveData<String> getGeneralError() {
        return generalError;
    }

    public LiveData<Event<Void>> getSmsCodeRequiredEvent() {
        return smsCodeRequiredEvent;
    }

    public boolean isFormValid(String password, String confirmPassword) {
        return RegisterValidator.validatePassword(password) == null
                && RegisterValidator.validateConfirmPassword(password, confirmPassword) == null;
    }

    public void resetPassword(String password, String confirmPassword, Activity activity) {
        if (phone == null) {
            return;
        }

        passwordError.setValue(null);
        confirmPasswordError.setValue(null);
        generalError.setValue(null);

        String passwordValidation = RegisterValidator.validatePassword(password);
        if (passwordValidation != null) {
            passwordError.setValue(passwordValidation);
            return;
        }

        String confirmValidation = RegisterValidator.validateConfirmPassword(password, confirmPassword);
        if (confirmValidation != null) {
            confirmPasswordError.setValue(confirmValidation);
            return;
        }

        uiState.setValue(UiState.LOADING);
        String e164 = PhoneNumberUtils.toE164(phone);
        repository.completePasswordReset(phone, e164, password, activity, new PasswordResetRepository.ResetPasswordCallback() {
            @Override
            public void onSmsCodeRequired() {
                uiState.postValue(UiState.WAITING_SMS);
                smsCodeRequiredEvent.postValue(new Event<>(null));
            }

            @Override
            public void onResult(PasswordResetRepository.ResetPasswordResult result, String errorMessage) {
                switch (result) {
                    case SUCCESS:
                        uiState.postValue(UiState.SUCCESS);
                        break;
                    case SESSION_INVALID:
                        generalError.postValue("session_invalid");
                        uiState.postValue(UiState.ERROR);
                        break;
                    case USER_NOT_FOUND:
                        generalError.postValue("user_not_found");
                        uiState.postValue(UiState.ERROR);
                        break;
                    case ERROR:
                    default:
                        generalError.postValue(errorMessage != null && !errorMessage.isEmpty()
                                ? errorMessage
                                : "generic");
                        uiState.postValue(UiState.ERROR);
                        break;
                }
            }
        });
    }

    public void submitSmsCode(String smsCode) {
        uiState.setValue(UiState.LOADING);
        repository.submitResetSmsCode(smsCode);
    }

    @Override
    protected void onCleared() {
        repository.cancelResetVerification();
        super.onCleared();
    }

    public void resetState() {
        uiState.setValue(UiState.IDLE);
    }

    public void clearPasswordError() {
        passwordError.setValue(null);
    }

    public void clearConfirmPasswordError() {
        confirmPasswordError.setValue(null);
    }

    public void clearGeneralError() {
        generalError.setValue(null);
    }
}
