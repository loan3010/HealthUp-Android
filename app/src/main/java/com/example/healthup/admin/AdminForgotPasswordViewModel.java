package com.example.healthup.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.util.AdminEmailLookup;
import com.example.healthup.util.Event;

public class AdminForgotPasswordViewModel extends ViewModel {

    public enum UiState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    public static final class NavigatePayload {
        public final String email;
        public final String adminUid;

        NavigatePayload(String email, String adminUid) {
            this.email = email;
            this.adminUid = adminUid;
        }
    }

    private final AdminPasswordResetRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> generalError = new MutableLiveData<>();
    private final MutableLiveData<Event<NavigatePayload>> navigateToOtpEvent = new MutableLiveData<>();

    public AdminForgotPasswordViewModel() {
        this(new AdminPasswordResetRepository());
    }

    public AdminForgotPasswordViewModel(AdminPasswordResetRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getEmailError() {
        return emailError;
    }

    public LiveData<Event<String>> getGeneralError() {
        return generalError;
    }

    public LiveData<Event<NavigatePayload>> getNavigateToOtpEvent() {
        return navigateToOtpEvent;
    }

    public void sendOtp(String email) {
        emailError.setValue(null);
        String normalized = AdminEmailLookup.normalize(email);
        if (!AdminEmailLookup.isValidEmail(normalized)) {
            emailError.setValue("invalid");
            return;
        }

        uiState.setValue(UiState.LOADING);
        repository.sendOtp(normalized, (result, otpForDebug, adminUid) -> {
            switch (result) {
                case SUCCESS:
                    navigateToOtpEvent.postValue(new Event<>(new NavigatePayload(normalized, adminUid)));
                    uiState.postValue(UiState.SUCCESS);
                    break;
                case EMAIL_NOT_REGISTERED:
                    emailError.postValue("not_registered");
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
