package com.example.healthup.forgotpassword;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.RegisterValidator;
import com.example.healthup.data.repository.PasswordResetRepository;
import com.example.healthup.util.Event;

public class ForgotPasswordViewModel extends ViewModel {

    public enum UiState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final PasswordResetRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String> phoneError = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> generalError = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> navigateToOtpEvent = new MutableLiveData<>();
    private String lastGeneratedOtp = "";

    public ForgotPasswordViewModel() {
        this(new PasswordResetRepository());
    }

    public ForgotPasswordViewModel(PasswordResetRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getPhoneError() {
        return phoneError;
    }

    public LiveData<Event<String>> getGeneralError() {
        return generalError;
    }

    public LiveData<Event<String>> getNavigateToOtpEvent() {
        return navigateToOtpEvent;
    }

    public String getLastGeneratedOtp() {
        return lastGeneratedOtp;
    }

    public void sendOtp(String phone) {
        phoneError.setValue(null);

        String validationError = RegisterValidator.validatePhone(phone);
        if (validationError != null) {
            phoneError.setValue(validationError);
            return;
        }

        uiState.setValue(UiState.LOADING);
        repository.sendOtp(phone, (result, otpForDebug) -> {
            lastGeneratedOtp = otpForDebug;
            switch (result) {
                case SUCCESS:
                    navigateToOtpEvent.postValue(new Event<>(phone));
                    uiState.postValue(UiState.SUCCESS);
                    break;
                case PHONE_NOT_REGISTERED:
                    phoneError.postValue("not_registered");
                    uiState.postValue(UiState.ERROR);
                    break;
                case ERROR:
                default:
                    generalError.postValue(new Event<>("generic"));
                    uiState.postValue(UiState.ERROR);
                    break;
            }
        });
    }

    public void resetState() {
        uiState.setValue(UiState.IDLE);
    }
}
