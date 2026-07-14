package com.example.models;

import java.util.Date;

/**
 * A single message inside a chat thread (Shopee-style single thread).
 *
 * <p>The same thread can contain messages produced by the local bot, the buyer,
 * a human seller/admin and the system. {@link #senderType} distinguishes them.</p>
 *
 * Firestore path: conversations/{conversationId}/messages/{messageId}
 */
public class ChatMessage {

    // senderType values
    public static final String SENDER_USER = "user";
    public static final String SENDER_BOT = "bot";
    public static final String SENDER_SELLER = "seller";
    public static final String SENDER_SYSTEM = "system";

    // type values
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_ORDER_CARD = "order_card";
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_SUGGESTION = "suggestion";
    /** Local card prompting guest to sign in. */
    public static final String TYPE_LOGIN_ACTION = "login_action";
    /** Local product context card from product detail. */
    public static final String TYPE_PRODUCT_CARD = "product_card";

    private String id;
    private String senderId;
    private String senderType = SENDER_BOT;
    private String senderName;
    private String text;
    private String type = TYPE_TEXT;
    /** Image payload for {@link #TYPE_IMAGE} (http URL or data URI). */
    private String imageUrl;
    private Date createdAt;
    private boolean read;

    // Denormalized order-card payload (present when type == order_card).
    private String orderId;
    private String orderCode;
    private String orderStatus;
    private double orderTotal;
    private int orderItemCount;

    // Denormalized product-card payload (present when type == product_card).
    private String productId;
    private String productName;
    private String productImageUrl;
    private double productPrice;
    private String productVariant;

    /**
     * Client-side ordering key in millis. Persisted messages use their server
     * timestamp; locally-generated bot messages get an assigned value so that
     * the merged list keeps a stable, chronological order.
     */
    private long sortTime;

    public ChatMessage() {
    }

    public static ChatMessage text(String senderType, String senderId, String text) {
        ChatMessage m = new ChatMessage();
        m.senderType = senderType;
        m.senderId = senderId;
        m.text = text;
        m.type = TYPE_TEXT;
        return m;
    }

    public static ChatMessage system(String text) {
        ChatMessage m = new ChatMessage();
        m.senderType = SENDER_SYSTEM;
        m.text = text;
        m.type = TYPE_SYSTEM;
        return m;
    }

    public static ChatMessage image(String senderType, String senderId, String imageUrl) {
        ChatMessage m = new ChatMessage();
        m.senderType = senderType;
        m.senderId = senderId;
        m.imageUrl = imageUrl;
        m.type = TYPE_IMAGE;
        m.text = "[Hình ảnh]";
        return m;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public double getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(double orderTotal) {
        this.orderTotal = orderTotal;
    }

    public int getOrderItemCount() {
        return orderItemCount;
    }

    public void setOrderItemCount(int orderItemCount) {
        this.orderItemCount = orderItemCount;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImageUrl() {
        return productImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        this.productImageUrl = productImageUrl;
    }

    public double getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(double productPrice) {
        this.productPrice = productPrice;
    }

    public String getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(String productVariant) {
        this.productVariant = productVariant;
    }

    public long getSortTime() {
        return sortTime;
    }

    public void setSortTime(long sortTime) {
        this.sortTime = sortTime;
    }
}
