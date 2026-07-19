package com.example.healthup.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.healthup.FirebaseManager;
import com.example.healthup.data.repository.ChatRepository;
import com.example.healthup.data.repository.OrderRepository;
import com.example.healthup.util.Event;
import com.example.healthup.chat.TextNormalizer;
import com.example.healthup.util.StaffRoleHelper;
import com.example.models.ChatMessage;
import com.example.models.Conversation;
import com.example.models.Order;
import com.example.models.Product;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Drives the single-thread chat screen for both the buyer (bot + handoff) and
 * the seller (human reply) sides.
 *
 * <p>The message list shown to the buyer is a merge of:</p>
 * <ul>
 *   <li>locally-generated items shown immediately (greeting, suggestion card, bot
 *       replies, order/product cards), and</li>
 *   <li>persisted messages streamed from Firestore (buyer texts, bot replies when
 *       signed-in, system handoff, seller replies, images).</li>
 * </ul>
 * Items are ordered by {@code sortTime}; greeting/suggestion pin to the top.
 * Suggestion chips and login CTAs stay local-only.
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
    private boolean buyerUiBootstrapped;
    private boolean remoteHistoryReady;
    private String uid;
    private String conversationId;
    private String buyerId;
    private long localSortCursor = 1000L;

    private boolean pendingInquiry;
    private String pendingOrderCode;
    private String pendingProductName;
    private String pendingProductVariant;
    private String pendingProductId;
    /** {@link ChatMessage#ORDER_PURPOSE_STATUS} or {@link ChatMessage#ORDER_PURPOSE_CANCEL}. */
    private String pendingOrderPurpose = ChatMessage.ORDER_PURPOSE_STATUS;

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

    public LiveData<Boolean> getSellerAccess() {
        return sellerAccess;
    }

    public boolean isSellerMode() {
        return sellerMode;
    }

    public boolean isGuest() {
        return uid == null;
    }

    public void setInquiryContext(@Nullable String orderCode,
                                  @Nullable String productName,
                                  @Nullable String productVariant,
                                  @Nullable String productId) {
        pendingProductId = productId != null ? productId.trim() : null;
        pendingProductName = productName != null ? productName.trim() : null;
        pendingProductVariant = productVariant != null ? productVariant.trim() : null;
        if (orderCode == null || orderCode.trim().isEmpty()) {
            pendingInquiry = (pendingProductName != null && !pendingProductName.isEmpty())
                    || (pendingProductId != null && !pendingProductId.isEmpty());
            pendingOrderCode = null;
            return;
        }
        pendingOrderCode = orderCode.trim();
        pendingInquiry = true;
    }

    public void dispatchPendingInquiry() {
        if (!pendingInquiry || !initialized || sellerMode) {
            return;
        }
        // Wait until conversation exists so product card is written to Firestore for staff.
        if (conversationId == null || uid == null) {
            return;
        }
        pendingInquiry = false;

        if (pendingProductId != null && !pendingProductId.isEmpty()) {
            loadAndShowProductCard(pendingProductId);
            return;
        }

        if (pendingProductName != null && !pendingProductName.isEmpty()) {
            String variantPart = (pendingProductVariant != null && !pendingProductVariant.isEmpty())
                    ? " (" + pendingProductVariant + ")" : "";
            String orderPart = (pendingOrderCode != null && !pendingOrderCode.isEmpty())
                    ? " thuộc đơn hàng " + pendingOrderCode : "";

            String botMsg = "Chào bạn! Mình thấy bạn đang cần hỗ trợ về sản phẩm "
                    + pendingProductName + variantPart + orderPart
                    + ". Bạn cần mình tư vấn thêm gì về sản phẩm này không?";
            showAndPersistBotText(botMsg);
            chatRepository.attachProductContext(conversationId, null, pendingProductName,
                    null, pendingProductVariant);
        } else if (pendingOrderCode != null && !pendingOrderCode.isEmpty()) {
            String botMsg = "Chào bạn! Mình đã nhận được yêu cầu hỗ trợ cho đơn hàng "
                    + pendingOrderCode + ". Bạn đang gặp vấn đề gì với đơn hàng này (vận chuyển, thanh toán, đổi trả...) để mình giúp nhé?";
            showAndPersistBotText(botMsg);
        }
        recompute();
    }

    private void loadAndShowProductCard(@NonNull String productId) {
        FirebaseFirestore.getInstance().collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    Product product = Product.fromDocument(doc);
                    if (product != null) {
                        product.setId(doc.getId());
                        long sortHint = nextLocalSort();
                        ChatMessage hint = ChatMessage.text(
                                ChatMessage.SENDER_BOT,
                                ChatMessage.SENDER_BOT,
                                "Bạn đang xem sản phẩm này. Hỏi mình về thành phần, cách dùng hoặc bấm Mua ngay / Thêm nhé.");
                        // In human mode keep card for staff; skip buyer-only bot copy.
                        if (!isHumanMode()) {
                            addLocal(hint, sortHint);
                            persistBotMessage(hint);
                        }
                        ChatMessage card = buildProductCard(product, pendingProductVariant);
                        addLocal(card, nextLocalSort());
                        persistBotMessage(card);
                        if (conversationId != null) {
                            chatRepository.attachProductContext(
                                    conversationId,
                                    product.getId(),
                                    product.getName(),
                                    product.getImageUrl(),
                                    pendingProductVariant);
                            if (isHumanMode()) {
                                chatRepository.markStaffUnread(conversationId);
                            }
                        }
                    } else if (pendingProductName != null) {
                        showAndPersistBotText("Chào bạn! Mình thấy bạn cần hỗ trợ về sản phẩm "
                                + pendingProductName + ".");
                        if (conversationId != null) {
                            chatRepository.attachProductContext(conversationId, productId,
                                    pendingProductName, null, pendingProductVariant);
                        }
                    }
                    recompute();
                })
                .addOnFailureListener(e -> {
                    if (pendingProductName != null) {
                        showAndPersistBotText("Chào bạn! Mình thấy bạn cần hỗ trợ về sản phẩm "
                                + pendingProductName + ".");
                        if (conversationId != null) {
                            chatRepository.attachProductContext(conversationId, productId,
                                    pendingProductName, null, pendingProductVariant);
                        }
                        recompute();
                    }
                });
    }

    private ChatMessage buildProductCard(@NonNull Product product, @Nullable String variant) {
        ChatMessage card = new ChatMessage();
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_PRODUCT_CARD);
        card.setProductId(product.getId());
        card.setProductName(product.getName());
        card.setProductImageUrl(product.getImageUrl());
        card.setProductPrice(product.getDisplayPrice());
        card.setProductVariant(variant);
        card.setText(product.getName());
        return card;
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
            chatRepository.fetchUserRoleFromServer(uid, role ->
                    sellerAccess.setValue(StaffRoleHelper.isStaff(role)));
        }

        if (sellerMode) {
            this.conversationId = existingConversationId;
            this.buyerId = buyerIdArg;
            if (conversationId != null) {
                chatRepository.markStaffRead(conversationId);
                startListening(conversationId);
            }
            return;
        }

        // Buyer mode — preserve active human sessions; only bootstrap bot UI in bot mode.
        this.buyerId = uid;

        if (uid == null) {
            addLocal(botEngine.greeting(), SORT_GREETING);
            addSuggestionCard();
            recompute();
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
                                addLocal(botEngine.greeting(), SORT_GREETING);
                                addSuggestionCard();
                                recompute();
                            }
                        }));
    }

    private void bootstrapBuyerUi() {
        if (sellerMode || buyerUiBootstrapped || uid == null) {
            return;
        }
        buyerUiBootstrapped = true;

        if (!remoteMessages.isEmpty()) {
            removeLocalGreetingAndSuggestion();
        }

        Conversation conv = conversation.getValue();
        if (conv != null && conv.isHumanMode()) {
            clearLocalBotContent();
            recompute();
            dispatchPendingInquiry();
            return;
        }

        if (remoteMessages.isEmpty() && !hasLocalGreeting()) {
            ChatMessage greeting = botEngine.greeting();
            addLocal(greeting, SORT_GREETING);
            persistBotMessage(greeting);
            addSuggestionCard();
        }
        recompute();
        dispatchPendingInquiry();
    }

    private void removeLocalGreetingAndSuggestion() {
        for (int i = localMessages.size() - 1; i >= 0; i--) {
            ChatMessage local = localMessages.get(i);
            if (local.getSortTime() == SORT_GREETING
                    || local.getSortTime() == SORT_SUGGESTION
                    || "local_suggestion".equals(local.getId())) {
                localMessages.remove(i);
            }
        }
    }

    private boolean hasLocalGreeting() {
        for (ChatMessage local : localMessages) {
            if (ChatMessage.SENDER_BOT.equals(local.getSenderType())
                    && local.getSortTime() == SORT_GREETING) {
                return true;
            }
        }
        return false;
    }

    private void startListening(@NonNull String id) {
        detachListeners();
        messagesRegistration = chatRepository.listenMessages(id, new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(@NonNull List<ChatMessage> msgs) {
                remoteMessages.clear();
                remoteMessages.addAll(msgs);
                if (!msgs.isEmpty()) {
                    removeLocalGreetingAndSuggestion();
                }
                if (!remoteHistoryReady) {
                    remoteHistoryReady = true;
                    bootstrapBuyerUi();
                }
                recompute();
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Keep whatever we already have; nothing else to do.
            }
        });
        conversationRegistration = chatRepository.listenConversation(id, conv -> {
            Conversation previous = conversation.getValue();
            conversation.setValue(conv);
            if (!sellerMode && conv != null && conv.isBuyerUnread()) {
                chatRepository.markBuyerRead(id);
            }
            if (!sellerMode && conv != null && conv.isHumanMode()) {
                clearLocalBotContent();
                recompute();
            } else if (!sellerMode && previous != null && previous.isHumanMode()
                    && conv != null && !conv.isHumanMode()) {
                if (remoteMessages.isEmpty() && !hasLocalGreeting()) {
                    addLocal(botEngine.greeting(), SORT_GREETING);
                    addSuggestionCard();
                }
                recompute();
            }
        });
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
        if (!isHumanMode()) {
            runBot(text);
        }
    }

    /** Buyer/staff sends an image (data URI or http URL) in the support thread. */
    public void sendUserImage(@Nullable String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        if (uid == null) {
            uid = chatRepository.currentUid();
        }
        if (uid == null) {
            toast.setValue(new Event<>("Vui lòng đăng nhập để gửi ảnh."));
            return;
        }
        if (sellerMode) {
            sendSellerImage(imageUrl.trim());
            return;
        }
        if (conversationId == null) {
            toast.setValue(new Event<>("Đang kết nối hội thoại, vui lòng thử lại."));
            return;
        }
        // Image is for staff — switch to human mode so admin inbox picks it up.
        if (!isHumanMode()) {
            chatRepository.setMode(conversationId, Conversation.MODE_HUMAN, success -> { /* best effort */ });
            ChatMessage system = ChatMessage.system("Khách đã gửi ảnh — đang chờ nhân viên phản hồi.");
            system.setSenderId(uid);
            chatRepository.sendMessage(conversationId, system, null);
        }
        ChatMessage msg = ChatMessage.image(ChatMessage.SENDER_USER, uid, imageUrl.trim());
        addLocal(msg, nextLocalSort());
        chatRepository.sendMessage(conversationId, msg, null);
        chatRepository.markStaffUnread(conversationId);
        clearLocalBotContent();
        recompute();
    }

    public void onSuggestedQuestionTapped(@NonNull String question) {
        if (isHumanMode()) {
            toast.setValue(new Event<>("Bạn đang chat với nhân viên. Vui lòng gửi tin nhắn trực tiếp."));
            return;
        }
        sendUserText(question);
    }

    /** Cycles the suggestion card to the next question set without removing it. */
    public void rotateSuggestions() {
        suggestionSetIndex = (suggestionSetIndex + 1) % SuggestionProvider.setCount();
        suggestionItems.setValue(SuggestionProvider.getSet(suggestionSetIndex));
    }

    /** Buyer taps "Chat với người bán" -> switch the same thread to a human. */
    public void requestHumanHandoff() {
        if (sellerMode) {
            return;
        }
        if (uid == null) {
            removeLocalById("local_login_login_seller");
            addLoginPrompt("login_seller");
            recompute();
            toast.setValue(new Event<>("Vui lòng đăng nhập để chat với nhân viên HealthUp."));
            return;
        }
        if (conversationId == null) {
            toast.setValue(new Event<>("Đang kết nối hội thoại, vui lòng thử lại sau vài giây."));
            ensureConversationThenHandoff();
            return;
        }
        if (isHumanMode()) {
            toast.setValue(new Event<>("Bạn đang được kết nối với người bán."));
            return;
        }
        performHumanHandoff();
    }

    private void ensureConversationThenHandoff() {
        chatRepository.fetchUserName(uid, name ->
                chatRepository.getOrCreateConversation(uid, name, null,
                        new ChatRepository.ConversationIdCallback() {
                            @Override
                            public void onReady(@NonNull String id) {
                                conversationId = id;
                                startListening(id);
                                if (!isHumanMode()) {
                                    performHumanHandoff();
                                }
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                                toast.setValue(new Event<>("Không thể kết nối. Vui lòng thử lại."));
                            }
                        }));
    }

    private void performHumanHandoff() {
        if (conversationId == null || uid == null) {
            return;
        }
        clearLocalBotContent();
        recompute();
        chatRepository.setMode(conversationId, Conversation.MODE_HUMAN, success -> {
            if (!success) {
                toast.setValue(new Event<>("Không thể kết nối. Vui lòng thử lại."));
            }
        });
        ChatMessage system = ChatMessage.system("Đang kết nối bạn với nhân viên HealthUp...");
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
            if (isHumanMode()) {
                chatRepository.markStaffUnread(conversationId);
            }
        }
        recompute();
    }

    private void runBot(@NonNull String text) {
        ChatBotEngine.BotResponse response = botEngine.process(text);

        if ((response.needsOrderLookup || response.needsMembershipLookup) && uid == null) {
            String loginCopy = response.needsMembershipLookup
                    ? "Bạn vui lòng đăng nhập để HealthUp xem hạng thành viên giúp bạn nhé!"
                    : "Bạn vui lòng đăng nhập để HealthUp kiểm tra đơn hàng giúp bạn nhé!";
            showAndPersistBotText(loginCopy);
            addLoginPrompt(response.needsMembershipLookup ? "login_membership" : "login_orders");
            recompute();
            return;
        }

        if (response.intent == ChatBotEngine.Intent.CANCEL_ORDER) {
            pendingOrderPurpose = ChatMessage.ORDER_PURPOSE_CANCEL;
        } else if (response.needsOrderLookup) {
            pendingOrderPurpose = ChatMessage.ORDER_PURPOSE_STATUS;
        }

        for (ChatMessage m : response.messages) {
            addLocal(m, nextLocalSort());
            persistBotMessage(m);
        }
        recompute();

        if (response.needsOrderLookup) {
            lookupOrders();
        } else if (response.needsMembershipLookup) {
            lookupMembership();
        } else if (response.needsCategoryLookup) {
            showCategoryPicker();
        }
    }

    private void showAndPersistBotText(@NonNull String text) {
        ChatMessage msg = ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT, text);
        addLocal(msg, nextLocalSort());
        persistBotMessage(msg);
    }

    private void showAndPersistBotHtml(@NonNull String html) {
        ChatMessage msg = ChatMessage.text(ChatMessage.SENDER_BOT, ChatMessage.SENDER_BOT, html);
        addLocal(msg, nextLocalSort());
        persistBotMessage(msg);
    }

    /**
     * Persists bot content for signed-in buyers. Firestore rules require
     * {@code senderId == auth.uid}; {@code senderType} stays {@code bot}.
     */
    private void persistBotMessage(@NonNull ChatMessage message) {
        if (sellerMode || conversationId == null || uid == null) {
            return;
        }
        String type = message.getType();
        if (!ChatMessage.TYPE_TEXT.equals(type)
                && !ChatMessage.TYPE_ORDER_CARD.equals(type)
                && !ChatMessage.TYPE_PRODUCT_CARD.equals(type)) {
            return;
        }
        message.setSenderId(uid);
        chatRepository.sendMessage(conversationId, message, null);
    }

    private void addLoginPrompt(@NonNull String reason) {
        ChatMessage card = new ChatMessage();
        card.setId("local_login_" + reason);
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_LOGIN_ACTION);
        if ("login_orders".equals(reason)) {
            card.setText("Bạn vui lòng Đăng nhập để kiểm tra đơn hàng nhé!");
        } else if ("login_membership".equals(reason)) {
            card.setText("Bạn vui lòng Đăng nhập để xem Khách hàng thân thiết HealthUp nhé!");
        } else if ("login_seller".equals(reason)) {
            card.setText("Bạn vui lòng Đăng nhập để chat với nhân viên HealthUp nhé!");
        } else {
            card.setText("Đăng nhập để HealthUp hỗ trợ bạn tốt hơn với đơn hàng và tài khoản của bạn.");
        }
        addLocal(card, nextLocalSort());
    }

    private void lookupOrders() {
        if (uid == null) {
            showAndPersistBotText("Bạn vui lòng Đăng nhập để kiểm tra đơn hàng nhé!");
            addLoginPrompt("login_orders");
            recompute();
            return;
        }
        final String purpose = pendingOrderPurpose != null
                ? pendingOrderPurpose
                : ChatMessage.ORDER_PURPOSE_STATUS;
        orderRepository.getOrdersForBuyer(uid, orders -> {
            if (orders.isEmpty()) {
                showAndPersistBotText(
                        "Mình chưa thấy đơn gần đây nào trong tài khoản HealthUp của bạn. "
                                + "Nếu vừa đặt hàng, thử lại sau ít phút hoặc xem toàn bộ đơn nhé.");
                addBrowseOrdersPrompt();
            } else {
                ChatMessage carousel = buildOrderCarousel(orders, purpose);
                addLocal(carousel, nextLocalSort());
                showAndPersistBotText(
                        "Vuốt ngang để xem thêm đơn gần đây (tối đa "
                                + OrderRepository.MAX_ORDERS
                                + " đơn), rồi chạm Chọn đơn hàng nhé.");
            }
            recompute();
        });
    }

    private void addBrowseOrdersPrompt() {
        ChatMessage card = new ChatMessage();
        card.setId("local_browse_orders_" + System.currentTimeMillis());
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_ACTION_PROMPT);
        card.setText("Xem danh sách đơn hàng của bạn trên HealthUp:");
        card.setActionId(ChatMessage.ACTION_BROWSE_ORDERS);
        card.setActionLabel("Xem đơn hàng");
        addLocal(card, nextLocalSort());
    }

    private ChatMessage buildOrderCarousel(@NonNull List<Order> orders, @NonNull String purpose) {
        ChatMessage carousel = new ChatMessage();
        carousel.setId("local_order_carousel_" + System.currentTimeMillis());
        carousel.setSenderType(ChatMessage.SENDER_BOT);
        carousel.setSenderId(ChatMessage.SENDER_BOT);
        carousel.setType(ChatMessage.TYPE_ORDER_CAROUSEL);
        carousel.setOrderPurpose(purpose);
        carousel.setText("Chọn đơn hàng");
        List<ChatMessage> choices = new ArrayList<>();
        for (Order order : orders) {
            choices.add(buildOrderCard(order, purpose));
        }
        carousel.setOrderChoices(choices);
        return carousel;
    }

    private ChatMessage buildOrderCard(@NonNull Order order, @NonNull String purpose) {
        ChatMessage card = new ChatMessage();
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_ORDER_CARD);
        card.setOrderId(order.getId());
        card.setOrderCode(order.getOrderCode());
        card.setOrderStatus(order.getStatus());
        card.setOrderTotal(order.getTotalAmount());
        card.setOrderItemCount(order.getItemCount());
        card.setOrderPurpose(purpose);
        card.setText("Đơn hàng " + order.getOrderCode());
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            com.example.models.OrderItem first = order.getItems().get(0);
            card.setProductName(first.getName());
            card.setProductImageUrl(first.getImageUrl());
        }
        if (ChatMessage.ORDER_PURPOSE_DETAIL.equals(purpose)) {
            card.setActionLabel("Trạng thái đơn hàng");
        } else {
            card.setActionLabel("Chọn đơn hàng");
        }
        return card;
    }

    /** Buyer tapped “Chọn đơn hàng” on a carousel card. */
    public void onOrderSelected(@NonNull ChatMessage selected) {
        String purpose = selected.getOrderPurpose() != null
                ? selected.getOrderPurpose()
                : ChatMessage.ORDER_PURPOSE_STATUS;
        String code = selected.getOrderCode() != null ? selected.getOrderCode() : "";

        showAndPersistBotText("Bạn đã chọn đơn #" + code + ". HealthUp kiểm tra ngay nhé!");

        if (ChatMessage.ORDER_PURPOSE_CANCEL.equals(purpose)) {
            showAndPersistBotHtml(OrderStatusCopy.explainCancelHtml(code, selected.getOrderStatus()));
            if (selected.getOrderStatus() != null
                    && !Order.STATUS_PENDING.equals(selected.getOrderStatus())) {
                // Offer human handoff path via existing toast/copy — keep a detail CTA.
            }
        } else {
            showAndPersistBotHtml(OrderStatusCopy.explainStatusHtml(code, selected.getOrderStatus()));
        }

        ChatMessage detailCard = new ChatMessage();
        detailCard.setSenderType(ChatMessage.SENDER_BOT);
        detailCard.setSenderId(ChatMessage.SENDER_BOT);
        detailCard.setType(ChatMessage.TYPE_ORDER_CARD);
        detailCard.setOrderId(selected.getOrderId());
        detailCard.setOrderCode(selected.getOrderCode());
        detailCard.setOrderStatus(selected.getOrderStatus());
        detailCard.setOrderTotal(selected.getOrderTotal());
        detailCard.setOrderItemCount(selected.getOrderItemCount());
        detailCard.setProductName(selected.getProductName());
        detailCard.setProductImageUrl(selected.getProductImageUrl());
        detailCard.setOrderPurpose(ChatMessage.ORDER_PURPOSE_DETAIL);
        detailCard.setActionLabel("Trạng thái đơn hàng");
        detailCard.setText("Đơn hàng " + code);
        addLocal(detailCard, nextLocalSort());
        persistBotMessage(detailCard);
        recompute();
    }

    /**
     * Same spend rule as {@link com.example.healthup.MemberTierFragment}:
     * sum {@code totalPrice} of delivered orders for this user.
     */
    private void lookupMembership() {
        if (uid == null) {
            showAndPersistBotText("Bạn vui lòng Đăng nhập để xem Khách hàng thân thiết HealthUp nhé!");
            addLoginPrompt("login_membership");
            recompute();
            return;
        }
        FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "delivered")
                .get()
                .addOnSuccessListener(snap -> {
                    double spent = 0d;
                    if (snap != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                            Object raw = doc.get("totalPrice");
                            if (!(raw instanceof Number)) {
                                raw = doc.get("totalAmount");
                            }
                            Double total = MembershipRules.numberOrNull(raw);
                            if (total != null) {
                                spent += total;
                            }
                        }
                    }
                    long spentLong = (long) spent;
                    showAndPersistBotHtml(MembershipRules.buildStatusHtml(spentLong));
                    addMembershipPrompt();
                    recompute();
                })
                .addOnFailureListener(e -> {
                    showAndPersistBotText(
                            "Mình chưa tải được hạng khách hàng thân thiết lúc này. Bạn thử mở "
                                    + "Khách hàng thân thiết trong hồ sơ, hoặc hỏi lại sau ít phút nhé.");
                    addMembershipPrompt();
                    recompute();
                });
    }

    private void addMembershipPrompt() {
        ChatMessage card = new ChatMessage();
        card.setId("local_membership_" + System.currentTimeMillis());
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_ACTION_PROMPT);
        card.setText("Xem quyền lợi và tiến độ lên hạng trên HealthUp:");
        card.setActionId(ChatMessage.ACTION_OPEN_MEMBERSHIP);
        card.setActionLabel("Khách hàng thân thiết");
        addLocal(card, nextLocalSort());
    }

    private static final List<String> DEFAULT_CHAT_CATEGORIES = Arrays.asList(
            "Hạt dinh dưỡng", "Granola", "Trái cây sấy",
            "Đồ ăn vặt", "Trà thảo mộc", "Combo"
    );

    private void showCategoryPicker() {
        removeLocalById("local_category_pick");
        ChatMessage card = new ChatMessage();
        card.setId("local_category_pick");
        card.setSenderType(ChatMessage.SENDER_BOT);
        card.setSenderId(ChatMessage.SENDER_BOT);
        card.setType(ChatMessage.TYPE_CATEGORY_PICK);
        card.setText("Chọn danh mục bạn muốn tư vấn:");
        card.setCategoryChoices(new ArrayList<>(DEFAULT_CHAT_CATEGORIES));
        addLocal(card, nextLocalSort());
        recompute();
    }

    /** Buyer tapped a category chip inside the category-pick bubble. */
    public void onCategorySelected(@NonNull String category) {
        if (category.trim().isEmpty()) {
            return;
        }
        persistUserMessage(category.trim());
        removeLocalById("local_category_pick");
        showAndPersistBotText("HealthUp đang tìm gợi ý trong danh mục “" + category.trim() + "”…");
        recompute();
        suggestProductsForCategory(category.trim());
    }

    private void suggestProductsForCategory(@NonNull String category) {
        FirebaseFirestore.getInstance().collection("products")
                .get()
                .addOnSuccessListener(snap -> {
                    List<Product> matches = new ArrayList<>();
                    if (snap != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                            Product p = Product.fromDocument(doc);
                            if (p == null || p.isHidden() || p.isDraft()) {
                                continue;
                            }
                            String cat = p.getCategory();
                            if (cat != null && cat.equalsIgnoreCase(category)) {
                                matches.add(p);
                            }
                        }
                    }
                    matches.sort((a, b) -> Integer.compare(b.getSoldCount(), a.getSoldCount()));
                    int limit = Math.min(4, matches.size());
                    if (limit == 0) {
                        showAndPersistBotText(
                                "Hiện chưa có sản phẩm phù hợp trong “" + category + "”. "
                                        + "Bạn thử danh mục khác hoặc Chat với người bán nhé.");
                        showCategoryPicker();
                        return;
                    }
                    showAndPersistBotText(
                            "Gợi ý một số sản phẩm “" + category + "” được quan tâm nhiều:");
                    for (int i = 0; i < limit; i++) {
                        ChatMessage card = buildProductCard(matches.get(i), null);
                        addLocal(card, nextLocalSort());
                    }
                    recompute();
                })
                .addOnFailureListener(e -> {
                    showAndPersistBotText(
                            "Mình chưa tải được sản phẩm lúc này. Bạn thử lại sau ít phút nhé.");
                    recompute();
                });
    }

    // ---- Seller actions --------------------------------------------------

    private void sendSellerReply(@NonNull String text) {
        if (conversationId == null || uid == null) {
            return;
        }
        chatRepository.assignSeller(conversationId, uid, success -> { /* best effort */ });
        chatRepository.fetchUserName(uid, name -> {
            ChatMessage msg = ChatMessage.text(ChatMessage.SENDER_SELLER, uid, text);
            msg.setSenderName(name != null && !name.trim().isEmpty() ? name.trim() : "Nhân viên");
            chatRepository.sendMessage(conversationId, msg, null);
        });
    }

    private void sendSellerImage(@NonNull String imageUrl) {
        if (conversationId == null || uid == null) {
            return;
        }
        chatRepository.assignSeller(conversationId, uid, success -> { /* best effort */ });
        chatRepository.fetchUserName(uid, name -> {
            ChatMessage msg = ChatMessage.image(ChatMessage.SENDER_SELLER, uid, imageUrl);
            msg.setSenderName(name != null && !name.trim().isEmpty() ? name.trim() : "Nhân viên");
            chatRepository.sendMessage(conversationId, msg, null);
        });
    }

    /** Staff/admin ends the human session; bot can reply again for the buyer. */
    public void closeHumanSession() {
        if (!sellerMode || conversationId == null) {
            return;
        }
        chatRepository.closeHumanSession(
                conversationId,
                "Phiên chat với nhân viên đã kết thúc. Bạn có thể tiếp tục hỏi trợ lý ảo.",
                success -> {
                    if (!success) {
                        toast.setValue(new Event<>("Không thể kết thúc phiên. Vui lòng thử lại."));
                    }
                });
    }

    /** Reopens a closed session from the staff inbox history tab. */
    public void reopenHumanSession() {
        if (!sellerMode || conversationId == null) {
            return;
        }
        chatRepository.reopenHumanSession(
                conversationId,
                "Nhân viên đã mở lại phiên hỗ trợ.",
                success -> {
                    if (!success) {
                        toast.setValue(new Event<>("Không thể mở lại phiên. Vui lòng thử lại."));
                    }
                });
    }

    public boolean isClosedSessionView() {
        Conversation c = conversation.getValue();
        return sellerMode && c != null && c.isClosedSession() && !c.isHumanMode();
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

    private void clearLocalBotContent() {
        removeLocalById("local_suggestion");
        for (int i = localMessages.size() - 1; i >= 0; i--) {
            ChatMessage local = localMessages.get(i);
            if (isBotLocalContent(local)) {
                localMessages.remove(i);
            }
        }
    }

    private boolean isBotLocalContent(@NonNull ChatMessage message) {
        if (ChatMessage.TYPE_SUGGESTION.equals(message.getType())) {
            return true;
        }
        if (ChatMessage.TYPE_ORDER_CARD.equals(message.getType())) {
            return true;
        }
        if (ChatMessage.TYPE_ORDER_CAROUSEL.equals(message.getType())) {
            return true;
        }
        if (ChatMessage.TYPE_ACTION_PROMPT.equals(message.getType())) {
            return true;
        }
        if (ChatMessage.TYPE_PRODUCT_CARD.equals(message.getType())) {
            return false;
        }
        if (ChatMessage.TYPE_LOGIN_ACTION.equals(message.getType())) {
            return false;
        }
        if (ChatMessage.TYPE_CATEGORY_PICK.equals(message.getType())) {
            return true;
        }
        return ChatMessage.SENDER_BOT.equals(message.getSenderType());
    }

    private void recompute() {
        boolean humanMode = isHumanMode();
        List<ChatMessage> merged = new ArrayList<>(localMessages.size() + remoteMessages.size());
        for (ChatMessage local : localMessages) {
            if (humanMode && isBotLocalContent(local)) {
                continue;
            }
            merged.add(local);
        }
        for (ChatMessage remote : remoteMessages) {
            if (!isDuplicateOfLocalMessage(remote)) {
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

    /** Skips Firestore echo when we already showed the same user/bot message locally. */
    private boolean isDuplicateOfLocalMessage(@NonNull ChatMessage remote) {
        String remoteType = remote.getSenderType();
        if (!ChatMessage.SENDER_USER.equals(remoteType)
                && !ChatMessage.SENDER_BOT.equals(remoteType)) {
            return false;
        }
        String remoteText = remote.getText();
        String remoteImage = remote.getImageUrl();
        String remoteOrderId = remote.getOrderId();
        String remoteProductId = remote.getProductId();
        for (ChatMessage local : localMessages) {
            if (!remoteType.equals(local.getSenderType())) {
                continue;
            }
            if (Math.abs(local.getSortTime() - remote.getSortTime()) >= 60_000L
                    && local.getCreatedAt() != null) {
                continue;
            }
            if (ChatMessage.TYPE_IMAGE.equals(remote.getType())
                    || ChatMessage.TYPE_IMAGE.equals(local.getType())) {
                if (remoteImage != null && remoteImage.equals(local.getImageUrl())) {
                    return true;
                }
                continue;
            }
            if (ChatMessage.TYPE_ORDER_CARD.equals(remote.getType())
                    || ChatMessage.TYPE_ORDER_CARD.equals(local.getType())) {
                if (remoteOrderId != null && remoteOrderId.equals(local.getOrderId())) {
                    return true;
                }
                continue;
            }
            if (ChatMessage.TYPE_PRODUCT_CARD.equals(remote.getType())
                    || ChatMessage.TYPE_PRODUCT_CARD.equals(local.getType())) {
                if (remoteProductId != null && remoteProductId.equals(local.getProductId())) {
                    return true;
                }
                continue;
            }
            if (remoteText != null && remoteText.equals(local.getText())) {
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
