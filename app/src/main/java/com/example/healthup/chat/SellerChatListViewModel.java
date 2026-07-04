package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.data.repository.ChatRepository;
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

    private ListenerRegistration registration;
    private boolean initialized;

    public SellerChatListViewModel() {
        this(new ChatRepository());
    }

    public SellerChatListViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public LiveData<List<Conversation>> getConversations() {
        return conversations;
    }

    public void start() {
        if (initialized) {
            return;
        }
        initialized = true;
        attach();
    }

    private void attach() {
        detach();
        registration = chatRepository.listenHumanConversations(
                new ChatRepository.ConversationsListener() {
                    @Override
                    public void onConversations(@NonNull List<Conversation> list) {
                        conversations.setValue(list);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        // Keep the current list on transient errors.
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
        if (registration == null && initialized) {
            attach();
        }
    }

    @Override
    protected void onCleared() {
        detach();
        super.onCleared();
    }
}
