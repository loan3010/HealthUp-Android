package com.example.models;

import com.google.firebase.Timestamp;

import java.io.Serializable;

public class NotificationItem implements Serializable {
    private String id;
    private String title;
    private String body;
    private String message;
    private String content;
    private Timestamp createdAt;
    private boolean read;
    private String userId;
    /** Firestore field: ORDER_SHIPPING, PROMO, PAYMENT, etc. */
    private String type;
    /** Related entity id (orderId, productId, promo code, …). */
    private String refId;
    /** True for sample/preview items not stored in Firestore. */
    private boolean mock;

    public NotificationItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public NotificationType getNotificationType() {
        return NotificationType.fromString(type);
    }

    public String getDisplayBody() {
        if (body != null && !body.isEmpty()) {
            return body;
        }
        if (message != null && !message.isEmpty()) {
            return message;
        }
        if (content != null && !content.isEmpty()) {
            return content;
        }
        return "";
    }
}
