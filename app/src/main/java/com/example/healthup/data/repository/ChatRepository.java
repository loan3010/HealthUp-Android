package com.example.healthup.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.admin.AdminGate;
import com.example.healthup.util.StaffRoleHelper;
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
import com.google.firebase.firestore.Source;

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
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onName(null);
                        return;
                    }
                    String name = doc.getString("fullName");
                    if (name == null || name.trim().isEmpty()) {
                        name = doc.getString("name");
                    }
                    if (name == null || name.trim().isEmpty()) {
                        name = doc.getString("displayName");
                    }
                    callback.onName(name);
                })
                .addOnFailureListener(e -> callback.onName(null));
    }

    public void fetchUserRole(@NonNull String uid, @NonNull RoleCallback callback) {
        fetchUserRole(uid, Source.DEFAULT, callback);
    }

    /** Prefer server when gating staff-only screens so role changes apply immediately. */
    public void fetchUserRoleFromServer(@NonNull String uid, @NonNull RoleCallback callback) {
        fetchUserRole(uid, Source.SERVER, callback);
    }

    private void fetchUserRole(@NonNull String uid, @NonNull Source source,
                               @NonNull RoleCallback callback) {
        // Admin app-password login uses a synthetic Auth UID; role lives on
        // users/{profileDocId} linked via user_sessions — same as AdminGate.
        AdminGate.resolveProfileDocId(uid)
                .continueWithTask(task -> {
                    String profileDocId = (task.isSuccessful() && task.getResult() != null)
                            ? task.getResult()
                            : uid;
                    return firestore.collection("users").document(profileDocId).get(source);
                })
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        callback.onRole(null);
                        return;
                    }
                    callback.onRole(StaffRoleHelper.resolveRole(doc));
                })
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
                        syncBuyerProfile(buyerId, ref, () -> callback.onReady(convId));
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
                            .addOnSuccessListener(unused ->
                                    syncBuyerProfile(buyerId, ref, () -> callback.onReady(convId)))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private void syncBuyerProfile(@NonNull String buyerId,
                                  @NonNull DocumentReference ref,
                                  @Nullable Runnable after) {
        firestore.collection("users").document(buyerId).get()
                .addOnSuccessListener(userDoc -> {
                    Map<String, Object> patch = new HashMap<>();
                    // Always keep buyerId so staff UI can show UID even when profile is empty.
                    patch.put("buyerId", buyerId);

                    if (userDoc.exists()) {
                        String username = userDoc.getString("username");
                        String phone = userDoc.getString("phone");
                        if (username != null && !username.trim().isEmpty()) {
                            patch.put("buyerUsername", username.trim());
                        }
                        if (phone != null && !phone.trim().isEmpty()) {
                            patch.put("buyerPhone", phone.trim());
                        }
                        String name = firstNonEmpty(
                                userDoc.getString("fullName"),
                                userDoc.getString("name"),
                                userDoc.getString("displayName"));
                        if (name != null) {
                            patch.put("buyerName", name);
                        } else if (phone != null && !phone.trim().isEmpty()) {
                            patch.put("buyerName", phone.trim());
                        } else {
                            String email = firstNonEmpty(
                                    userDoc.getString("displayEmail"),
                                    userDoc.getString("email"));
                            if (email != null
                                    && !email.toLowerCase(java.util.Locale.ROOT)
                                    .endsWith("@healthup.app")) {
                                patch.put("buyerName", email);
                            }
                        }
                    }
                    ref.set(patch, SetOptions.merge())
                            .addOnCompleteListener(task -> {
                                if (after != null) {
                                    after.run();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (after != null) {
                        after.run();
                    }
                });
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
        if (Conversation.MODE_HUMAN.equals(mode)) {
            data.put("humanSessionStartedAt", FieldValue.serverTimestamp());
            data.put("sessionBucket", Conversation.SESSION_ACTIVE);
            data.put("lastSessionClosedAt", FieldValue.delete());
            data.put("staffUnread", true);
        }
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true))
                .addOnFailureListener(e -> callback.onComplete(false));
    }

    /** Ends the human session so the local bot can reply again. */
    public void closeHumanSession(@NonNull String conversationId,
                                  @NonNull String systemMessage,
                                  @NonNull SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", Conversation.MODE_BOT);
        data.put("sessionBucket", Conversation.SESSION_CLOSED);
        data.put("lastSessionClosedAt", FieldValue.serverTimestamp());
        data.put("humanSessionStartedAt", FieldValue.delete());
        data.put("staffUnread", false);
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    ChatMessage system = ChatMessage.system(systemMessage);
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        system.setSenderId(user.getUid());
                    }
                    sendMessage(conversationId, system, callback);
                })
                .addOnFailureListener(e -> callback.onComplete(false));
    }

    public void markStaffUnread(@NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("staffUnread", true);
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge());
    }

    public void markStaffRead(@NonNull String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("staffUnread", false);
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge());
    }

    /** Reopens a closed session so staff can reply again. */
    public void reopenHumanSession(@NonNull String conversationId,
                                   @NonNull String systemMessage,
                                   @NonNull SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", Conversation.MODE_HUMAN);
        data.put("sessionBucket", Conversation.SESSION_ACTIVE);
        data.put("humanSessionStartedAt", FieldValue.serverTimestamp());
        data.put("lastSessionClosedAt", FieldValue.delete());
        data.put("staffUnread", false);
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    ChatMessage system = ChatMessage.system(systemMessage);
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        system.setSenderId(user.getUid());
                    }
                    sendMessage(conversationId, system, callback);
                })
                .addOnFailureListener(e -> callback.onComplete(false));
    }
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
        if (message.getImageUrl() != null) {
            data.put("imageUrl", message.getImageUrl());
        }
        if (message.getOrderId() != null) {
            data.put("orderId", message.getOrderId());
            data.put("orderCode", message.getOrderCode());
            data.put("orderStatus", message.getOrderStatus());
            data.put("orderTotal", message.getOrderTotal());
            data.put("orderItemCount", message.getOrderItemCount());
        }
        if (message.getProductId() != null) {
            data.put("productId", message.getProductId());
            data.put("productName", message.getProductName());
            data.put("productImageUrl", message.getProductImageUrl());
            data.put("productPrice", message.getProductPrice());
            data.put("productVariant", message.getProductVariant());
        }

        String preview = ChatMessage.TYPE_IMAGE.equals(message.getType())
                ? "[Hình ảnh]"
                : (ChatMessage.TYPE_PRODUCT_CARD.equals(message.getType())
                ? ("[Sản phẩm] " + (message.getProductName() != null ? message.getProductName() : ""))
                : message.getText());
        convRef.collection("messages").add(data)
                .addOnSuccessListener(ref -> {
                    updateLastMessage(convRef, preview);
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

    /**
     * Stores the product the buyer opened chat about so staff inbox/thread can show context.
     */
    public void attachProductContext(@NonNull String conversationId,
                                     @Nullable String productId,
                                     @Nullable String productName,
                                     @Nullable String productImageUrl,
                                     @Nullable String productVariant) {
        Map<String, Object> data = new HashMap<>();
        if (productId != null && !productId.trim().isEmpty()) {
            data.put("productId", productId.trim());
        }
        if (productName != null && !productName.trim().isEmpty()) {
            data.put("productName", productName.trim());
        }
        if (productImageUrl != null && !productImageUrl.trim().isEmpty()) {
            data.put("productImageUrl", productImageUrl.trim());
        }
        if (productVariant != null && !productVariant.trim().isEmpty()) {
            data.put("productVariant", productVariant.trim());
        }
        if (data.isEmpty()) {
            return;
        }
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("conversations").document(conversationId)
                .set(data, SetOptions.merge());
    }

    public void deleteConversationHistory(@NonNull String conversationId, @NonNull SimpleCallback callback) {
        firestore.collection("conversations").document(conversationId)
                .collection("messages")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onComplete(true);
                        return;
                    }
                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onComplete(true))
                            .addOnFailureListener(e -> callback.onComplete(false));
                })
                .addOnFailureListener(e -> callback.onComplete(false));
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
     * Streams conversations in the given inbox bucket (active or closed).
     */
    public ListenerRegistration listenConversationsByBucket(@NonNull String sessionBucket,
                                                            @NonNull ConversationsListener listener) {
        return firestore.collection("conversations")
                .whereEqualTo("sessionBucket", sessionBucket)
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
                    sortConversations(conversations);
                    listener.onConversations(conversations);
                });
    }

    /**
     * Active human sessions awaiting staff (mode == human).
     */
    public ListenerRegistration listenActiveSessions(@NonNull ConversationsListener listener) {
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
                    sortConversations(conversations);
                    listener.onConversations(conversations);
                });
    }

    /**
     * Closed human sessions kept for staff lookup (sessionBucket == closed).
     */
    public ListenerRegistration listenClosedSessions(@NonNull ConversationsListener listener) {
        return listenConversationsByBucket(Conversation.SESSION_CLOSED, listener);
    }

    private void sortConversations(@NonNull List<Conversation> conversations) {
        conversations.sort((a, b) -> {
                        long ta = a.getUpdatedAt() != null ? a.getUpdatedAt().getTime() : 0L;
                        long tb = b.getUpdatedAt() != null ? b.getUpdatedAt().getTime() : 0L;
                        return Long.compare(tb, ta);
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
        m.setImageUrl(doc.getString("imageUrl"));
        m.setOrderId(doc.getString("orderId"));
        m.setOrderCode(doc.getString("orderCode"));
        m.setOrderStatus(doc.getString("orderStatus"));
        Object orderTotalVal = doc.get("orderTotal");
        m.setOrderTotal(orderTotalVal instanceof Number
                ? ((Number) orderTotalVal).doubleValue() : 0d);
        Object orderItemCountVal = doc.get("orderItemCount");
        m.setOrderItemCount(orderItemCountVal instanceof Number
                ? ((Number) orderItemCountVal).intValue() : 0);
        m.setProductId(doc.getString("productId"));
        m.setProductName(doc.getString("productName"));
        m.setProductImageUrl(doc.getString("productImageUrl"));
        Double productPrice = doc.getDouble("productPrice");
        m.setProductPrice(productPrice != null ? productPrice : 0d);
        m.setProductVariant(doc.getString("productVariant"));
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
        c.setProductName(doc.getString("productName"));
        c.setProductImageUrl(doc.getString("productImageUrl"));
        c.setProductVariant(doc.getString("productVariant"));
        c.setHumanSessionStartedAt(doc.getDate("humanSessionStartedAt"));
        c.setLastSessionClosedAt(doc.getDate("lastSessionClosedAt"));
        c.setSessionBucket(doc.getString("sessionBucket"));
        Boolean staffUnread = doc.getBoolean("staffUnread");
        c.setStaffUnread(staffUnread != null && staffUnread);
        c.setBuyerUsername(doc.getString("buyerUsername"));
        c.setBuyerPhone(doc.getString("buyerPhone"));
        Object participants = doc.get("participantIds");
        if (participants instanceof List) {
            c.setParticipantIds((List<String>) participants);
        }
        c.setLastMessageAt(doc.getDate("lastMessageAt"));
        c.setUpdatedAt(doc.getDate("updatedAt"));
        return c;
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
