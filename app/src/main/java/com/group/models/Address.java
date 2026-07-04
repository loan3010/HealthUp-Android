package com.group.models;

import java.io.Serializable;

public class Address implements Serializable {
    private String recipientName;
    private String phone;
    private String addressDetail;
    private boolean isDefault;

    public Address() {}

    public Address(String recipientName, String phone, String addressDetail, boolean isDefault) {
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressDetail = addressDetail;
        this.isDefault = isDefault;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public void setAddressDetail(String addressDetail) {
        this.addressDetail = addressDetail;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
