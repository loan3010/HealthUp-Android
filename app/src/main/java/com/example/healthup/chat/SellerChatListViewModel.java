package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Locale;

/**
 * Seller/admin inbox with active vs closed session tabs, search and unread state.
 */
public class SellerChatListViewModel extends ViewModel {

    public enum InboxTab {
        ACTIVE,
        CLOSED
    }

    private final ChatRepository chatRepository;

    private final MutableLiveData<List<Conversation>> conversations =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Event<String>> accessDenied = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<Event<String>> loadError = new MutableLiveData<>();
    private final MutableLiveData<InboxTab> currentTab = new MutableLiveData<>(InboxTab.ACTIVE);

    private final List<Conversation> sourceList = new ArrayList<>();
    private ListenerRegistration registration;
    private boolean initialized;
    private boolean staffConfirmed;
    @Nullable
    private String searchQuery = "";

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

    public LiveData<InboxTab> getCurrentTab() {
        return currentTab;
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
                attachForTab(InboxTab.ACTIVE);
            } else {
                loading.setValue(false);
                accessDenied.setValue(new Event<>(
                        "Chỉ người bán hoặc quản trị viên mới có thể mở hộp thư. "
                                + "Kiểm tra field role=admin trên users/" + uid + " trong Firestore."));
            }
        });
    }

    public void setTab(@NonNull InboxTab tab) {
        if (currentTab.getValue() == tab) {
            return;
        }
        currentTab.setValue(tab);
        loading.setValue(true);
        attachForTab(tab);
    }

    public void setSearchQuery(@Nullable String query) {
        searchQuery = query != null ? query.trim() : "";
        applyFilter();
    }

    private void attachForTab(@NonNull InboxTab tab) {
        detach();
        ChatRepository.ConversationsListener listener = new ChatRepository.ConversationsListener() {
            @Override
            public void onConversations(@NonNull List<Conversation> list) {
                loading.setValue(false);
                sourceList.clear();
                sourceList.addAll(list);
                applyFilter();
            }

            @Override
            public void onError(@NonNull Exception e) {
                loading.setValue(false);
                loadError.setValue(new Event<>(
                        "Không tải được hộp thư: " + e.getMessage()));
            }
        };
        if (tab == InboxTab.CLOSED) {
            registration = chatRepository.listenClosedSessions(listener);
        } else {
            registration = chatRepository.listenActiveSessions(listener);
        }
    }

    private void applyFilter() {
        String q = searchQuery != null ? searchQuery.toLowerCase(Locale.getDefault()) : "";
        if (q.isEmpty()) {
            conversations.setValue(new ArrayList<>(sourceList));
            return;
        }
        List<Conversation> filtered = new ArrayList<>();
        for (Conversation c : sourceList) {
            if (matchesSearch(c, q)) {
                filtered.add(c);
            }
        }
        conversations.setValue(filtered);
    }

    private boolean matchesSearch(@NonNull Conversation conversation, @NonNull String query) {
        if (matchesField(conversation.getBuyerUsername(), query)) {
            return true;
        }
        if (matchesField(conversation.getBuyerPhone(), query)) {
            return true;
        }
        return matchesField(conversation.getBuyerName(), query);
    }

    private boolean matchesField(@Nullable String value, @NonNull String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    public void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public void resume() {
        InboxTab tab = currentTab.getValue();
        if (registration == null && initialized && staffConfirmed && tab != null) {
            attachForTab(tab);
        }
    }

    @Override
    protected void onCleared() {
        detach();
        super.onCleared();
    }
}
