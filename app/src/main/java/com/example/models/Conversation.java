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
}
