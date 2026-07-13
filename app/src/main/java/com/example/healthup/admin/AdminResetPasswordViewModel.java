package com.example.healthup.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.RegisterValidator;

public class AdminResetPasswordViewModel extends ViewModel {

    public enum UiState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final AdminPasswordResetRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> confirmPasswordError = new MutableLiveData<>();
    private final MutableLiveData<String> generalError = new MutableLiveData<>();
    private String adminUid;

    public AdminResetPasswordViewModel() {
        this(new AdminPasswordResetRepository());
    }

    public AdminResetPasswordViewModel(AdminPasswordResetRepository repository) {
        this.repository = repository;
    }

    public void setAdminUid(String adminUid) {
        this.adminUid = adminUid;
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

    public void resetPassword(String password, String confirmPassword) {
        if (adminUid == null) return;

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
        repository.completePasswordReset(adminUid, password, (result, errorMessage) -> {
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
                case AUTH_SYNC_FAILED:
                    generalError.postValue("auth_sync_failed");
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
        });
    }

    public void resetState() {
        uiState.setValue(UiState.IDLE);
    }
}
