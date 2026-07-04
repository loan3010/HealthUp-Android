package com.group.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {
    private String id; // Document ID in Firestore
    private String orderCode;
    private String userId;
    private List<OrderItem> items;
    private String status; // pending, confirmed, shipping, delivered, cancelled, returned
    private String paymentStatus; // unpaid, paid, refunded, reshipped
    private String paymentMethod;
    private double subtotal;
    private double discountAmount;
    private String promoCode;
    private double shippingFee;
    private double totalPrice;
    private Address address;
    private long createdAt;
    private long updatedAt;
    private long deliveredAt;
    private boolean reviewed;
    private boolean returnExpired;
    private boolean reviewExpired;
    private boolean shopConfirmedDelivery;

    // Fields for Return Request data snapshot
    private String returnReason;
    private String returnDescription;
    private List<String> returnMediaUris;
    private String returnHandling;

    public Order() {}

    public Order(String orderCode, List<OrderItem> items, String status, String paymentStatus, double totalPrice, long createdAt, String paymentMethod, Address address) {
        this.orderCode = orderCode;
        this.items = items;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.paymentMethod = paymentMethod;
        this.address = address;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public double getShippingFee() { return shippingFee; }
    public void setShippingFee(double shippingFee) { this.shippingFee = shippingFee; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(long deliveredAt) { this.deliveredAt = deliveredAt; }

    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }

    public boolean isReturnExpired() { return returnExpired; }
    public void setReturnExpired(boolean returnExpired) { this.returnExpired = returnExpired; }

    public boolean isReviewExpired() { return reviewExpired; }
    public void setReviewExpired(boolean reviewExpired) { this.reviewExpired = reviewExpired; }

    public boolean isShopConfirmedDelivery() { return shopConfirmedDelivery; }
    public void setShopConfirmedDelivery(boolean shopConfirmedDelivery) { this.shopConfirmedDelivery = shopConfirmedDelivery; }

    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }

    public String getReturnDescription() { return returnDescription; }
    public void setReturnDescription(String returnDescription) { this.returnDescription = returnDescription; }

    public List<String> getReturnMediaUris() { return returnMediaUris; }
    public void setReturnMediaUris(List<String> returnMediaUris) { this.returnMediaUris = returnMediaUris; }

    public String getReturnHandling() { return returnHandling; }
    public void setReturnHandling(String returnHandling) { this.returnHandling = returnHandling; }
}
