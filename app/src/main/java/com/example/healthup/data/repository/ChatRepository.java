package com.example.healthup.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.ChatMessage;
import com.example.models.Conversation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore-backed repository for the single-thread seller chat.
 *
 * <p>Data model:</p>
 * <ul>
 *   <li>conversations/{conversationId}: participantIds[], buyerId, buyerName,
 *       sellerId, mode ("bot"|"human"), lastMessage, lastMessageAt, updatedAt,
 *       productId(optional)</li>
 *   <li>conversations/{id}/messages/{msgId}: senderId, senderType, text,
 *       type ("text"|"order_card"|"system"), orderId(optional), createdAt
 *       (serverTimestamp), read</li>
 * </ul>
 *
 * <p>All realtime reads use {@code addSnapshotListener} and return the
 * {@link ListenerRegistration} so the caller can detach it (avoids leaks).</p>
 */
public class ChatRepository {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public ChatRepository() {
        this(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance());
    }

    public ChatRepository(FirebaseFirestore firestore, FirebaseAuth auth) {
        this.firestore = firestore;
        this.auth = auth;
    }

    // ---- Callbacks -------------------------------------------------------

    public interface ConversationIdCallback {
        void onReady(@NonNull String conversationId);

        void onError(@NonNull Exception e);
    }

    public interface MessagesListener {
        void onMessages(@NonNull List<ChatMessage> messages);

        void onError(@NonNull Exception e);
    }

    public interface ConversationsListener {
        void onConversations(@NonNull List<Conversation> conversations);

        void onError(@NonNull Exception e);
    }

    public interface ConversationListener {
        void onConversation(@Nullable Conversation conversation);
    }

    public interface SimpleCallback {
        void onComplete(boolean success);
    }

    public interface NameCallback {
        void onName(@Nullable String name);
    }

    public interface RoleCallback {
        void onRole(@Nullable String role);
    }

    // ---- Identity --------------------------------------------------------

    @Nullable
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /** Deterministic id: one support thread per buyer (avoids duplicate threads). */
    public String supportConversationId(@NonNull String buyerId) {
        return "support_" + buyerId;
    }

    public void fetchUserName(@NonNull String uid, @NonNull NameCallback callback) {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc ->
                        callback.onName(doc.exists() ? doc.getString("fullName") : null))
                .addOnFailureListener(e -> callback.onName(null));
    }

    public void fetchUserRole(@NonNull String uid, @NonNull RoleCallback callback) {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc ->
                        callback.onRole(doc.exists() ? doc.getString("role") : null))
                .addOnFailureListener(e -> callback.onRole(null));
    }

    // ---- Conversation ----------------------------------------------------

    /**
     * Ensures a support conversation exists for the buyer, creating it in
     * {@code mode = "bot"} on first use.
     */
    public void getOrCreateConversation(@NonNull String buyerId,
                                        @Nullable String buyerName,
                                        @Nullable String productId,
                                        @NonNull ConversationIdCallback callback) {
        final String convId = supportConversationId(buyerId);
        final DocumentReference ref = firestore.collection("conversations").document(convId);
        ref.get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onReady(convId);
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("buyerId", buyerId);
                    if (buyerName != null) {
                        data.put("buyerName", buyerName);
                    }
                    data.put("participantIds", new ArrayList<>(Arrays.asList(buyerId)));
                    data.put("sellerId", "");
                    data.put("mode", Conversation.MODE_BOT);
                    data.put("lastMessage", "");
                    data.put("lastMessageAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    if (productId != null) {
                        data.put("productId", productId);
                    }
                    ref.set(data)
                            .addOnSuccessListener(unused -> callback.onReady(convId))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listenConversation(@NonNull String conversationId,
                                                   @NonNull ConversationListener listener) {
        return firestore.collection("conversations").document(conversationId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        listener.onConversation(null);
                        return;
                    }
                    listener.onConversation(mapConversation(snapshot));
                });
    }

    /** Switches the thread to human mode so a seller/admin can take over. */
    public void setMode(@NonNull String conversationId, @NonNull String mode,
                        @NonNull SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", mode);
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true))
                .addOnFailureListener(e -> callback.onComplete(false));
    }

    /** Assigns the current seller/admin to the thread when they reply. */
    public void assignSeller(@NonNull String conversationId, @NonNull String sellerId,
                             @NonNull SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("sellerId", sellerId);
        data.put("participantIds", FieldValue.arrayUnion(sellerId));
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true))
                .addOnFailureListener(e -> callback.onComplete(false));
    }

    // ---- Messages --------------------------------------------------------

    public ListenerRegistration listenMessages(@NonNull String conversationId,
                                               @NonNull MessagesListener listener) {
        return firestore.collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<ChatMessage> messages = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            messages.add(mapMessage(doc));
                        }
                    }
                    listener.onMessages(messages);
                });
    }

    public void sendMessage(@NonNull String conversationId, @NonNull ChatMessage message,
                            @Nullable SimpleCallback callback) {
        final DocumentReference convRef =
                firestore.collection("conversations").document(conversationId);

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", message.getSenderId());
        data.put("senderType", message.getSenderType());
        data.put("senderName", message.getSenderName());
        data.put("text", message.getText());
        data.put("type", message.getType());
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        if (message.getOrderId() != null) {
            data.put("orderId", message.getOrderId());
            data.put("orderCode", message.getOrderCode());
            data.put("orderStatus", message.getOrderStatus());
            data.put("orderTotal", message.getOrderTotal());
            data.put("orderItemCount", message.getOrderItemCount());
        }

        convRef.collection("messages").add(data)
                .addOnSuccessListener(ref -> {
                    updateLastMessage(convRef, message.getText());
                    if (callback != null) {
                        callback.onComplete(true);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                });
    }

    private void updateLastMessage(@NonNull DocumentReference convRef, @Nullable String text) {
        Map<String, Object> data = new HashMap<>();
        data.put("lastMessage", text != null ? text : "");
        data.put("lastMessageAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        convRef.set(data, SetOptions.merge());
    }

    // ---- Seller inbox ----------------------------------------------------

    /**
     * Streams conversations that need a human (mode == "human"). Sorted on the
     * client by updatedAt desc to avoid requiring a composite index.
     */
    public ListenerRegistration listenHumanConversations(@NonNull ConversationsListener listener) {
        return firestore.collection("conversations")
                .whereEqualTo("mode", Conversation.MODE_HUMAN)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<Conversation> conversations = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            conversations.add(mapConversation(doc));
                        }
                    }
                    conversations.sort((a, b) -> {
                        long ta = a.getUpdatedAt() != null ? a.getUpdatedAt().getTime() : 0L;
                        long tb = b.getUpdatedAt() != null ? b.getUpdatedAt().getTime() : 0L;
                        return Long.compare(tb, ta);
                    });
                    listener.onConversations(conversations);
                });
    }

    // ---- Mapping ---------------------------------------------------------

    private ChatMessage mapMessage(@NonNull DocumentSnapshot doc) {
        ChatMessage m = new ChatMessage();
        m.setId(doc.getId());
        m.setSenderId(doc.getString("senderId"));
        String senderType = doc.getString("senderType");
        m.setSenderType(senderType != null ? senderType : ChatMessage.SENDER_USER);
        m.setSenderName(doc.getString("senderName"));
        m.setText(doc.getString("text"));
        String type = doc.getString("type");
        m.setType(type != null ? type : ChatMessage.TYPE_TEXT);
        m.setOrderId(doc.getString("orderId"));
        m.setOrderCode(doc.getString("orderCode"));
        m.setOrderStatus(doc.getString("orderStatus"));
        Double orderTotal = doc.getDouble("orderTotal");
        m.setOrderTotal(orderTotal != null ? orderTotal : 0d);
        Long orderItemCount = doc.getLong("orderItemCount");
        m.setOrderItemCount(orderItemCount != null ? orderItemCount.intValue() : 0);
        Boolean read = doc.getBoolean("read");
        m.setRead(read != null && read);

        // Use ESTIMATE so locally-pending writes get a usable timestamp and show
        // instantly (latency compensation) instead of jumping once acknowledged.
        Date createdAt = doc.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        m.setCreatedAt(createdAt);
        m.setSortTime(createdAt != null ? createdAt.getTime() : System.currentTimeMillis());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Conversation mapConversation(@NonNull DocumentSnapshot doc) {
        Conversation c = new Conversation();
        c.setId(doc.getId());
        c.setBuyerId(doc.getString("buyerId"));
        c.setBuyerName(doc.getString("buyerName"));
        c.setSellerId(doc.getString("sellerId"));
        String mode = doc.getString("mode");
        c.setMode(mode != null ? mode : Conversation.MODE_BOT);
        c.setLastMessage(doc.getString("lastMessage"));
        c.setProductId(doc.getString("productId"));
        Object participants = doc.get("participantIds");
        if (participants instanceof List) {
            c.setParticipantIds((List<String>) participants);
        }
        c.setLastMessageAt(doc.getDate("lastMessageAt"));
        c.setUpdatedAt(doc.getDate("updatedAt"));
        return c;
    }
}
