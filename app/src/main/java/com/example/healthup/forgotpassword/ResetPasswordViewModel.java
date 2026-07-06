package com.example.healthup.forgotpassword;

import android.app.Activity;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.PhoneNumberUtils;
import com.example.healthup.RegisterValidator;
import com.example.healthup.data.repository.PasswordResetRepository;

public class ResetPasswordViewModel extends ViewModel {

    public enum UiState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final PasswordResetRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> confirmPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> generalError = new MutableLiveData<>();
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
        repository.completePasswordReset(phone, e164, password, activity, (result, errorMessage) -> {
            switch (result) {
                case SUCCESS:
                    uiState.setValue(UiState.SUCCESS);
                    break;
                case SESSION_INVALID:
                    generalError.setValue("session_invalid");
                    uiState.setValue(UiState.ERROR);
                    break;
                case USER_NOT_FOUND:
                    generalError.setValue("user_not_found");
                    uiState.setValue(UiState.ERROR);
                    break;
                case ERROR:
                default:
                    generalError.setValue(errorMessage != null && !errorMessage.isEmpty()
                            ? errorMessage
                            : "generic");
                    uiState.setValue(UiState.ERROR);
                    break;
            }
        });
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
