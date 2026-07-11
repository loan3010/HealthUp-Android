package com.example.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A single-thread support conversation between a buyer and the shop.
 *
 * <p>The thread starts in {@code mode = "bot"} (handled locally by the keyword
 * bot). When the buyer taps "Chat với người bán" the mode switches to
 * {@code "human"} so a seller/admin can reply in the SAME thread.</p>
 *
 * Firestore path: conversations/{conversationId}
 */
public class Conversation {

    public static final String MODE_BOT = "bot";
    public static final String MODE_HUMAN = "human";

    /** Inbox bucket: active human session awaiting staff. */
    public static final String SESSION_ACTIVE = "active";
    /** Inbox bucket: staff closed the session; history kept for lookup. */
    public static final String SESSION_CLOSED = "closed";

    private String id;
    private List<String> participantIds = new ArrayList<>();
    private String buyerId;
    private String buyerName;
    private String sellerId;
    private String mode = MODE_BOT;
    private String lastMessage;
    private Date lastMessageAt;
    private Date updatedAt;
    private String productId;
    /** Server time when the buyer requested a human (seller) session. */
    private Date humanSessionStartedAt;
    /** Server time when staff closed the human session. */
    private Date lastSessionClosedAt;
    /** Inbox grouping: active / closed; null when buyer never requested human. */
    private String sessionBucket;
    /** True when the buyer sent a message staff has not opened yet. */
    private boolean staffUnread;
    private String buyerUsername;
    private String buyerPhone;

    public Conversation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isHumanMode() {
        return MODE_HUMAN.equals(mode);
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Date getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Date lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Date getHumanSessionStartedAt() {
        return humanSessionStartedAt;
    }

    public void setHumanSessionStartedAt(Date humanSessionStartedAt) {
        this.humanSessionStartedAt = humanSessionStartedAt;
    }

    public Date getLastSessionClosedAt() {
        return lastSessionClosedAt;
    }

    public void setLastSessionClosedAt(Date lastSessionClosedAt) {
        this.lastSessionClosedAt = lastSessionClosedAt;
    }

    public String getSessionBucket() {
        return sessionBucket;
    }

    public void setSessionBucket(String sessionBucket) {
        this.sessionBucket = sessionBucket;
    }

    public boolean isStaffUnread() {
        return staffUnread;
    }

    public void setStaffUnread(boolean staffUnread) {
        this.staffUnread = staffUnread;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public void setBuyerUsername(String buyerUsername) {
        this.buyerUsername = buyerUsername;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone;
    }

    public boolean isActiveSession() {
        return SESSION_ACTIVE.equals(sessionBucket) || isHumanMode();
    }

    public boolean isClosedSession() {
        return SESSION_CLOSED.equals(sessionBucket);
    }
}
