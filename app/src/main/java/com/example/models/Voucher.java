package com.example.models;

import java.io.Serializable;

public class Voucher implements Serializable {
    public enum Type { SHIPPING, DISCOUNT, CASHBACK }

    private String id;
    private String code;
    private String title;
    private String description;
    private double discountAmount;
    private double minOrderAmount;
    private String expiryDate;
    private Type type;
    private boolean selected;
    private String requiredTier; // "Member", "VIP", null/empty for all

    public Voucher() {}

    public Voucher(String id, String title, String description, double discountAmount, String expiryDate, Type type) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.discountAmount = discountAmount;
        this.expiryDate = expiryDate;
        this.type = type;
        this.selected = false;
    }

    public String getRequiredTier() { return requiredTier; }
    public void setRequiredTier(String requiredTier) { this.requiredTier = requiredTier; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    public double getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(double minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
