package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.data.repository.ChatRepository;
import com.example.healthup.data.repository.OrderRepository;
import com.example.healthup.util.Event;
import com.example.models.ChatMessage;
import com.example.models.Conversation;
import com.example.models.Order;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Drives the single-thread chat screen for both the buyer (bot + handoff) and
 * the seller (human reply) sides.
 *
 * <p>The message list shown to the buyer is a merge of:</p>
 * <ul>
 *   <li>locally-generated items (greeting, suggestion card, bot replies and
 *       order cards) — deterministic and free, never written to Firestore, and</li>
 *   <li>persisted messages streamed from Firestore (buyer texts, system handoff
 *       and seller replies).</li>
 * </ul>
 * Items are ordered by {@code sortTime}; greeting/suggestion pin to the top.
 */
public class ChatViewModel extends ViewModel {

    private static final long SORT_GREETING = 1L;
    private static final long SORT_SUGGESTION = 2L;

    private final ChatRepository chatRepository;
    private final OrderRepository orderRepository;
    private final ChatBotEngine botEngine;

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Conversation> conversation = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> toast = new MutableLiveData<>();
    private final MutableLiveData<List<SuggestionProvider.Item>> suggestionItems =
            new MutableLiveData<>(SuggestionProvider.getSet(0));
    private final MutableLiveData<Boolean> sellerAccess = new MutableLiveData<>(false);

    private int suggestionSetIndex;

    private final List<ChatMessage> localMessages = new ArrayList<>();
    private final List<ChatMessage> remoteMessages = new ArrayList<>();

    private ListenerRegistration messagesRegistration;
    private ListenerRegistration conversationRegistration;

    private boolean initialized;
    private boolean sellerMode;
    private String uid;
    private String conversationId;
    private String buyerId;
    private long localSortCursor = 1000L;

    public ChatViewModel() {
        this(new ChatRepository(), new OrderRepository(), new ChatBotEngine());
    }

    public ChatViewModel(ChatRepository chatRepository, OrderRepository orderRepository,
                         ChatBotEngine botEngine) {
        this.chatRepository = chatRepository;
        this.orderRepository = orderRepository;
        this.botEngine = botEngine;
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<Conversation> getConversation() {
        return conversation;
    }

    public LiveData<Event<String>> getToast() {
        return toast;
    }

    public LiveData<List<SuggestionProvider.Item>> getSuggestionItems() {
        return suggestionItems;
    }

    /** True when the signed-in user is a seller/admin (can open the inbox). */
    public LiveData<Boolean> getSellerAccess() {
        return sellerAccess;
    }

    public boolean isSellerMode() {
        return sellerMode;
    }

    public boolean isHumanMode() {
        Conversation c = conversation.getValue();
        return c != null && c.isHumanMode();
    }

    /**
     * @param sellerMode           true when opened by a seller/admin from the inbox
     * @param existingConversationId conversation id (required for seller mode)
     * @param buyerIdArg           buyer uid (for seller mode); ignored for buyers
     */
    public void init(boolean sellerMode, @Nullable String existingConversationId,
                     @Nullable String buyerIdArg) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.sellerMode = sellerMode;
        this.uid = chatRepository.currentUid();

        if (uid != null) {
            chatRepository.fetchUserRole(uid, role -> {
                boolean allowed = "seller".equals(role) || "admin".equals(role);
                sellerAccess.setValue(allowed);
            });
        }

        if (sellerMode) {
            this.conversationId = existingConversationId;
            this.buyerId = buyerIdArg;
            if (conversationId != null) {
                startListening(conversationId);
            }
            return;
        }

        // Buyer mode
        this.buyerId = uid;
        addLocal(botEngine.greeting(), SORT_GREETING);
        addSuggestionCard();
        recompute();

        if (uid == null) {
            return;
        }
        chatRepository.fetchUserName(uid, name ->
                chatRepository.getOrCreateConversation(uid, name, null,
                        new ChatRepository.ConversationIdCallback() {
                            @Override
                            public void onReady(@NonNull String id) {
                                conversationId = id;
                                startListening(id);
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                                // Bot still works locally; seller handoff unavailable.
                            }
                        }));
    }

    private void startListening(@NonNull String id) {
        detachListeners();
        messagesRegistration = chatRepository.listenMessages(id, new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(@NonNull List<ChatMessage> msgs) {
                remoteMessages.clear();
                remoteMessages.addAll(msgs);
                recompute();
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Keep whatever we already have; nothing else to do.
            }
        });
        conversationRegistration = chatRepository.listenConversation(id, conversation::setValue);
    }

    // ---- Buyer actions ---------------------------------------------------

    public void sendUserText(@Nullable String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return;
        }
        String text = rawText.trim();

        // Auth may complete after init (e.g. debug chat link before login).
        if (uid == null) {
            uid = chatRepository.currentUid();
        }

        if (sellerMode) {
            if (uid == null) {
                toast.setValue(new Event<>("Vui lòng đăng nhập để trả lời khách hàng."));
                return;
            }
            sendSellerReply(text);
            return;
        }

        persistUserMessage(text);

        if (isHumanMode()) {
            // A human is handling the thread; the bot stays silent.
            return;
        }
        runBot(text);
    }

    public void onSuggestedQuestionTapped(@NonNull String question) {
        sendUserText(question);
    }

    /** Cycles the suggestion card to the next question set without removing it. */
    public void rotateSuggestions() {
        suggestionSetIndex = (suggestionSetIndex + 1) % SuggestionProvider.setCount();
        suggestionItems.setValue(SuggestionProvider.getSet(suggestionSetIndex));
    }

    /** Buyer taps "Chat với người bán" -> switch the same thread to a human. */
    public void requestHumanHandoff() {
        if (sellerMode || conversationId == null || uid == null) {
            return;
        }
        if (isHumanMode()) {
            toast.setValue(new Event<>("Bạn đang được kết nối với người bán."));
            return;
        }
        chatRepository.setMode(conversationId, Conversation.MODE_HUMAN, success -> {
            if (!success) {
                toast.setValue(new Event<>("Không thể kết nối. Vui lòng thử lại."));
            }
        });
        ChatMessage system = ChatMessage.system("Đang kết nối bạn với người bán của HealthUp...");
        system.setSenderId(uid);
        chatRepository.sendMessage(conversationId, system, null);
    }

    private void persistUserMessage(@NonNull String text) {
        String senderId = uid != null ? uid : "guest";
        ChatMessage msg = ChatMessage.text(ChatMessage.SENDER_USER, senderId, text);
        // Echo locally so the user sees their message immediately.
        addLocal(msg, nextLocalSort());
        if (conversationId != null && uid != null) {
            chatRepository.sendMessage(conversationId, msg, null);
        }
        recompute();
    }

    private void runBot(@NonNull String text) {
        ChatBotEngine.BotResponse response = botEngine.process(text);
        for (ChatMessage m : response.messages) {
            addLocal(m, nextLocalSort());
        }
        recompute();

        if (response.needsOrderLookup) {
            lookupOrders();
        }
    }

    private void lookupOrders() {
        if (uid == null) {
            ChatMessage loginHint = ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT,
                    "Bạn cần đăng nhập để mình tra cứu đơn hàng. "
                            + "Vui lòng quay lại màn Đăng nhập rồi mở chat nhé.");
            addLocal(loginHint, nextLocalSort());
            recompute();
            return;
        }
        orderRepository.getOrdersForBuyer(uid, orders -> {
            if (orders.isEmpty()) {
                ChatMessage empty = ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT,
                        "Mình chưa tìm thấy đơn hàng nào trong tài khoản của bạn. "
                                + "Nếu bạn vừa đặt hàng, vui lòng thử lại sau ít phút nhé.");
                addLocal(empty, nextLocalSort());
            } else {
                for (Order order : orders) {
                    addLocal(buildOrderCard(order), nextLocalSort());
                }
                ChatMessage hint = ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT,
                        "Chạm vào một đơn hàng để xem chi tiết. Bạn cần hỗ trợ thêm gì không?");
                addLocal(hint, nextLocalSort());
            }
            recompute();
        });
    }

    private ChatMessage buildOrderCard(@NonNull Order order) {
        ChatMessage card = new ChatMessage();
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_ORDER_CARD);
        card.setOrderId(order.getId());
        card.setOrderCode(order.getOrderCode());
        card.setOrderStatus(order.getStatus());
        card.setOrderTotal(order.getTotalAmount());
        card.setOrderItemCount(order.getItemCount());
        card.setText("Đơn hàng " + order.getOrderCode());
        return card;
    }

    // ---- Seller actions --------------------------------------------------

    private void sendSellerReply(@NonNull String text) {
        if (conversationId == null || uid == null) {
            return;
        }
        chatRepository.assignSeller(conversationId, uid, success -> { /* best effort */ });
        ChatMessage msg = ChatMessage.text(ChatMessage.SENDER_SELLER, uid, text);
        msg.setSenderName("Người bán");
        chatRepository.sendMessage(conversationId, msg, null);
    }

    // ---- Local list helpers ---------------------------------------------

    private void addSuggestionCard() {
        ChatMessage card = new ChatMessage();
        card.setId("local_suggestion");
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_SUGGESTION);
        card.setSortTime(SORT_SUGGESTION);
        localMessages.add(card);
    }

    private void addLocal(@NonNull ChatMessage message, long sortTime) {
        message.setSortTime(sortTime);
        localMessages.add(message);
    }

    private void removeLocalById(@NonNull String id) {
        for (int i = localMessages.size() - 1; i >= 0; i--) {
            if (id.equals(localMessages.get(i).getId())) {
                localMessages.remove(i);
            }
        }
    }

    private long nextLocalSort() {
        long now = System.currentTimeMillis();
        localSortCursor = Math.max(now, localSortCursor + 1);
        return localSortCursor;
    }

    private void recompute() {
        List<ChatMessage> merged = new ArrayList<>(localMessages.size() + remoteMessages.size());
        merged.addAll(localMessages);
        for (ChatMessage remote : remoteMessages) {
            if (!isDuplicateOfLocalUserMessage(remote)) {
                merged.add(remote);
            }
        }
        Collections.sort(merged, new Comparator<ChatMessage>() {
            @Override
            public int compare(ChatMessage a, ChatMessage b) {
                return Long.compare(a.getSortTime(), b.getSortTime());
            }
        });
        messages.setValue(merged);
    }

    /** Skips Firestore echo when we already showed the same user text locally. */
    private boolean isDuplicateOfLocalUserMessage(@NonNull ChatMessage remote) {
        if (!ChatMessage.SENDER_USER.equals(remote.getSenderType())) {
            return false;
        }
        String remoteText = remote.getText();
        if (remoteText == null) {
            return false;
        }
        for (ChatMessage local : localMessages) {
            if (!ChatMessage.SENDER_USER.equals(local.getSenderType())) {
                continue;
            }
            if (remoteText.equals(local.getText())
                    && Math.abs(local.getSortTime() - remote.getSortTime()) < 60_000L) {
                return true;
            }
        }
        return false;
    }

    // ---- Lifecycle -------------------------------------------------------

    /** Call from Fragment#onDestroyView to avoid leaking Firestore listeners. */
    public void detachListeners() {
        if (messagesRegistration != null) {
            messagesRegistration.remove();
            messagesRegistration = null;
        }
        if (conversationRegistration != null) {
            conversationRegistration.remove();
            conversationRegistration = null;
        }
    }

    /** Re-attaches listeners after the view is recreated (config change). */
    public void resumeListening() {
        if (conversationId != null && messagesRegistration == null) {
            startListening(conversationId);
        }
    }

    @Override
    protected void onCleared() {
        detachListeners();
        super.onCleared();
    }
}
