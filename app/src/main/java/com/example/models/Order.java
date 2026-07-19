package com.example.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Order implements Serializable {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_SHIPPING = "shipping";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_RETURNED = "returned";
    public static final String STATUS_COMPLETED = "completed";

    public static final String RETURN_NONE = "none";
    public static final String RETURN_REQUESTED = "requested";
    public static final String RETURN_APPROVED = "approved";
    public static final String RETURN_REJECTED = "rejected";
    public static final String RETURN_COMPLETED = "completed";

    public static final int MAX_DELIVERY_ATTEMPTS = 3;

    public static final String FAIL_REFUSED = "Khách từ chối nhận";
    public static final String FAIL_NO_CONTACT = "Không nghe máy / không liên lạc được";
    public static final String FAIL_RESCHEDULE = "Khách hẹn giao lại";
    public static final String FAIL_BAD_ADDRESS = "Sai địa chỉ / không tìm thấy địa chỉ";
    public static final String FAIL_OTHER = "Lý do khác";

    public static final String CANCEL_SOURCE_CUSTOMER = "customer";
    public static final String CANCEL_SOURCE_DELIVERY_REFUSED = "delivery_refused";
    public static final String CANCEL_SOURCE_DELIVERY_MAX = "delivery_max_attempts";
    public static final String CANCEL_SOURCE_ADMIN = "admin";

    public static final String CANCEL_REASON_MAX_ATTEMPTS = "Giao hàng không thành công quá 3 lần.";
    public static final String CANCEL_REASON_REFUSED = "Khách từ chối nhận hàng";

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
    /** Used by chat order cards when {@link #items} is not loaded. */
    private int itemCount;
    private Address address;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;
    private java.util.Date deliveredAt;
    private boolean reviewed;
    private boolean returnExpired;
    private boolean reviewExpired;
    private boolean shopConfirmedDelivery;
    private java.util.Date shopConfirmedAt;

    private int deliveryAttempts;
    private boolean needsRedelivery;
    private List<DeliveryFailure> deliveryFailures;

    private boolean cancelRequested;
    private String cancelReason;
    private String cancelSource;
    private java.util.Date cancelRequestedAt;
    private java.util.Date cancelledAt;

    private String returnStatus;
    private String returnReason;
    private String returnDescription;
    private List<String> returnMediaUris;
    private String returnHandling;
    private int returnStep;
    private java.util.Date returnRequestedAt;
    private String returnRejectReason;
    private List<Map<String, Object>> returnItems;
    /** Amount to refund for this return (selected items), not necessarily full order total. */
    private double refundAmount;

    private boolean stockDeducted;
    private boolean stockRestored;

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
        this.returnStatus = RETURN_NONE;
    }

    public static String[] deliveryFailureReasons() {
        return new String[]{
                FAIL_REFUSED,
                FAIL_NO_CONTACT,
                FAIL_RESCHEDULE,
                FAIL_BAD_ADDRESS,
                FAIL_OTHER
        };
    }

    public boolean hasActiveReturn() {
        String rs = returnStatus != null ? returnStatus : RETURN_NONE;
        return RETURN_REQUESTED.equals(rs)
                || RETURN_APPROVED.equals(rs)
                || RETURN_REJECTED.equals(rs)
                || RETURN_COMPLETED.equals(rs)
                || getReturnHandling() != null
                || STATUS_RETURNED.equalsIgnoreCase(status);
    }

    public boolean isReturnRejected() {
        return RETURN_REJECTED.equalsIgnoreCase(returnStatus);
    }

    public boolean isReturnCompleted() {
        return RETURN_COMPLETED.equalsIgnoreCase(returnStatus)
                || (STATUS_COMPLETED.equalsIgnoreCase(status) && getReturnHandling() != null);
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

    /** Prefer line-items size when loaded; otherwise the denormalized chat field. */
    public int getItemCount() {
        if (items != null && !items.isEmpty()) {
            return items.size();
        }
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
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

    public java.util.Date getShopConfirmedAt() { return shopConfirmedAt; }
    public void setShopConfirmedAt(java.util.Date shopConfirmedAt) { this.shopConfirmedAt = shopConfirmedAt; }

    public int getDeliveryAttempts() { return deliveryAttempts; }
    public void setDeliveryAttempts(int deliveryAttempts) { this.deliveryAttempts = deliveryAttempts; }

    public boolean isNeedsRedelivery() { return needsRedelivery; }
    public void setNeedsRedelivery(boolean needsRedelivery) { this.needsRedelivery = needsRedelivery; }

    public List<DeliveryFailure> getDeliveryFailures() {
        return deliveryFailures != null ? deliveryFailures : new ArrayList<>();
    }

    public void setDeliveryFailures(List<DeliveryFailure> deliveryFailures) {
        this.deliveryFailures = deliveryFailures;
    }

    public boolean isStockDeducted() { return stockDeducted; }
    public void setStockDeducted(boolean stockDeducted) { this.stockDeducted = stockDeducted; }

    public boolean isStockRestored() { return stockRestored; }
    public void setStockRestored(boolean stockRestored) { this.stockRestored = stockRestored; }

    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean cancelRequested) { this.cancelRequested = cancelRequested; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getCancelSource() { return cancelSource; }
    public void setCancelSource(String cancelSource) { this.cancelSource = cancelSource; }

    /** Display text for the red "Lý do hủy" row on client order detail. */
    public String getDisplayCancelReason() {
        if (CANCEL_SOURCE_DELIVERY_MAX.equals(cancelSource)) {
            return CANCEL_REASON_MAX_ATTEMPTS;
        }
        if (CANCEL_SOURCE_DELIVERY_REFUSED.equals(cancelSource)
                || FAIL_REFUSED.equals(cancelReason)
                || (cancelReason != null && cancelReason.startsWith(FAIL_REFUSED))) {
            return CANCEL_REASON_REFUSED;
        }
        if (!CANCEL_SOURCE_CUSTOMER.equals(cancelSource)
                && deliveryAttempts >= MAX_DELIVERY_ATTEMPTS
                && cancelReason != null
                && !cancelReason.isEmpty()) {
            return CANCEL_REASON_MAX_ATTEMPTS;
        }
        return cancelReason != null ? cancelReason : "";
    }

    public String getDisplayCancelledBy() {
        if (CANCEL_SOURCE_CUSTOMER.equals(cancelSource)) {
            return "Khách hàng";
        }
        if (CANCEL_SOURCE_DELIVERY_REFUSED.equals(cancelSource)
                || CANCEL_SOURCE_DELIVERY_MAX.equals(cancelSource)) {
            return "Hệ thống";
        }
        if (CANCEL_SOURCE_ADMIN.equals(cancelSource)) {
            return "Shop";
        }
        if (deliveryAttempts >= MAX_DELIVERY_ATTEMPTS
                || FAIL_REFUSED.equals(cancelReason)
                || (cancelReason != null && cancelReason.startsWith(FAIL_REFUSED))) {
            return "Hệ thống";
        }
        return "Khách hàng";
    }

    public java.util.Date getCancelRequestedAt() { return cancelRequestedAt; }
    public void setCancelRequestedAt(java.util.Date cancelRequestedAt) { this.cancelRequestedAt = cancelRequestedAt; }

    public java.util.Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(java.util.Date cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getReturnStatus() {
        return returnStatus != null ? returnStatus : RETURN_NONE;
    }

    public void setReturnStatus(String returnStatus) { this.returnStatus = returnStatus; }

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

    public String getReturnRejectReason() { return returnRejectReason; }
    public void setReturnRejectReason(String returnRejectReason) { this.returnRejectReason = returnRejectReason; }

    public List<Map<String, Object>> getReturnItems() { return returnItems; }
    public void setReturnItems(List<Map<String, Object>> returnItems) { this.returnItems = returnItems; }

    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    /**
     * Prefer stored {@code refundAmount}; otherwise sum {@code returnItems} (price × qty).
     * Falls back to {@code totalPrice} only when neither is available (legacy full-order returns).
     */
    public double resolveRefundAmount() {
        if (refundAmount > 0) {
            return refundAmount;
        }
        double fromItems = sumReturnItemsAmount();
        if (fromItems > 0) {
            return fromItems;
        }
        return Math.max(0, totalPrice);
    }

    public double sumReturnItemsAmount() {
        if (returnItems == null || returnItems.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map<String, Object> row : returnItems) {
            if (row == null) continue;
            Object priceObj = row.get("price");
            Object qtyObj = row.get("quantity");
            double price = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0;
            int qty = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 0;
            if (price > 0 && qty > 0) {
                sum += price * qty;
            }
        }
        return sum;
    }
}
