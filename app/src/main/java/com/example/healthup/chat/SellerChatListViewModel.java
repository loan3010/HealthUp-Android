package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.data.repository.ChatRepository;
import com.example.healthup.util.Event;
import com.example.healthup.util.StaffRoleHelper;
import com.example.models.Conversation;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Seller/admin inbox: streams conversations that were handed off to a human
 * (mode == "human") so a seller can pick them up and reply in the same thread.
 */
public class SellerChatListViewModel extends ViewModel {

    private final ChatRepository chatRepository;

    private final MutableLiveData<List<Conversation>> conversations =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Event<String>> accessDenied = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Event<String>> loadError = new MutableLiveData<>();

    private ListenerRegistration registration;
    private boolean initialized;
    private boolean staffConfirmed;

    public SellerChatListViewModel() {
        this(new ChatRepository());
    }

    public SellerChatListViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public LiveData<List<Conversation>> getConversations() {
        return conversations;
    }

    public LiveData<Event<String>> getAccessDenied() {
        return accessDenied;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Event<String>> getLoadError() {
        return loadError;
    }

    public void start() {
        if (initialized) {
            return;
        }
        initialized = true;
        loading.setValue(true);
        @Nullable String uid = chatRepository.currentUid();
        if (uid == null) {
            loading.setValue(false);
            accessDenied.setValue(new Event<>("Vui lòng đăng nhập để mở hộp thư."));
            return;
        }
        chatRepository.fetchUserRoleFromServer(uid, role -> {
            if (StaffRoleHelper.isStaff(role)) {
                staffConfirmed = true;
                attach();
            } else {
                loading.setValue(false);
                accessDenied.setValue(new Event<>(
                        "Chỉ người bán hoặc quản trị viên mới có thể mở hộp thư. "
                                + "Kiểm tra field role=admin trên users/" + uid + " trong Firestore."));
            }
        });
    }

    private void attach() {
        detach();
        registration = chatRepository.listenHumanConversations(
                new ChatRepository.ConversationsListener() {
                    @Override
                    public void onConversations(@NonNull List<Conversation> list) {
                        loading.setValue(false);
                        conversations.setValue(list);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        loading.setValue(false);
                        loadError.setValue(new Event<>(
                                "Không tải được hộp thư: " + e.getMessage()));
                    }
                });
    }

    public void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public void resume() {
        if (registration == null && initialized && staffConfirmed) {
            attach();
        }
    }

    @Override
    protected void onCleared() {
        detach();
        super.onCleared();
    }
}
