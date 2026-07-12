package com.example.models;

import java.io.Serializable;

public class PaymentAccount implements Serializable {
    public static final String TYPE_MOMO = "momo";
    public static final String TYPE_ZALOPAY = "zalopay";
    public static final String TYPE_VNPAY = "vnpay";
    public static final String TYPE_ATM = "atm";
    public static final String TYPE_CARD = "card"; // Credit/Debit
    public static final String TYPE_LINKED_BANK = "linked_bank";

    private String id;
    private String type; // TYPE_*
    private String providerName; // e.g., "Vietcombank", "MoMo"
    private String accountIdentifier; // Masked info e.g., "**** 1234" or phone number
    private boolean linked;

    // Optional details for cards
    private String cardNumber;
    private String cardType; // Visa, Mastercard
    private String cvv;
    private String expiryDate;

    public PaymentAccount() {}

    public PaymentAccount(String type, String providerName, String accountIdentifier) {
        this.type = type;
        this.providerName = providerName;
        this.accountIdentifier = accountIdentifier;
        this.linked = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getAccountIdentifier() { return accountIdentifier; }
    public void setAccountIdentifier(String accountIdentifier) { this.accountIdentifier = accountIdentifier; }

    public boolean isLinked() { return linked; }
    public void setLinked(boolean linked) { this.linked = linked; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
}
