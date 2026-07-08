package com.example.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.List;

public class Order implements Serializable {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_SHIPPING = "shipping";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_CANCELLED = "cancelled";

    private String id;
    private String orderCode;
    private String userId;
    private List<OrderItem> items;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private double subtotal;
    private double discountAmount;
    private String promoCode;
    private double shippingFee;
    private double totalPrice;
    private Address address;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private java.util.Date deliveredAt;
    private boolean reviewed;
    private boolean returnExpired;
    private boolean reviewExpired;
    private boolean shopConfirmedDelivery;

    private String returnReason;
    private String returnDescription;
    private List<String> returnMediaUris;
    private String returnHandling;
    private int returnStep;
    private java.util.Date returnRequestedAt;

    public Order() {}

    public Order(String orderCode, List<OrderItem> items, String status, String paymentStatus,
                 double totalPrice, com.google.firebase.Timestamp createdAt, String paymentMethod, Address address) {
        this.orderCode = orderCode;
        this.items = items;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt != null ? createdAt.toDate() : null;
        this.updatedAt = this.createdAt;
        this.paymentMethod = paymentMethod;
        this.address = address;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    /** Chat/order lookup alias (Firestore may use buyerId or userId). */
    public String getBuyerId() { return userId; }
    public void setBuyerId(String buyerId) { this.userId = buyerId; }

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

    /** Chat card alias for totalPrice. */
    public double getTotalAmount() { return totalPrice; }
    public void setTotalAmount(double totalAmount) { this.totalPrice = totalAmount; }

    /** Chat card item count derived from line items. */
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void setItemCount(int itemCount) {
        // Legacy chat mapper field; count is derived from items when present.
    }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public java.util.Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.util.Date createdAt) { this.createdAt = createdAt; }

    public java.util.Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.util.Date updatedAt) { this.updatedAt = updatedAt; }

    public java.util.Date getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(java.util.Date deliveredAt) { this.deliveredAt = deliveredAt; }

    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }

    public boolean isReturnExpired() { return returnExpired; }
    public void setReturnExpired(boolean returnExpired) { this.returnExpired = returnExpired; }

    public boolean isReviewExpired() { return reviewExpired; }
    public void setReviewExpired(boolean reviewExpired) { this.reviewExpired = reviewExpired; }

    public boolean isShopConfirmedDelivery() { return shopConfirmedDelivery; }
    public void setShopConfirmedDelivery(boolean shopConfirmedDelivery) {
        this.shopConfirmedDelivery = shopConfirmedDelivery;
    }

    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }

    public String getReturnDescription() { return returnDescription; }
    public void setReturnDescription(String returnDescription) { this.returnDescription = returnDescription; }

    public List<String> getReturnMediaUris() { return returnMediaUris; }
    public void setReturnMediaUris(List<String> returnMediaUris) { this.returnMediaUris = returnMediaUris; }

    public String getReturnHandling() { return returnHandling; }
    public void setReturnHandling(String returnHandling) { this.returnHandling = returnHandling; }

    public int getReturnStep() { return returnStep; }
    public void setReturnStep(int returnStep) { this.returnStep = returnStep; }

    public java.util.Date getReturnRequestedAt() { return returnRequestedAt; }
    public void setReturnRequestedAt(java.util.Date returnRequestedAt) { this.returnRequestedAt = returnRequestedAt; }
}
