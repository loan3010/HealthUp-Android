package com.example.models;

import java.io.Serializable;

public class OrderItem implements Serializable {
    private String name;
    private String variantLabel;
    private double price;
    private double originalPrice;
    private int quantity;
    private String imageUrl;
    private Review review;

    // Empty constructor for Firestore
    public OrderItem() {
    }

    public OrderItem(String name, String variantLabel, double price, int quantity, String imageUrl) {
        this(name, variantLabel, price, 0, quantity, imageUrl);
    }

    public OrderItem(String name, String variantLabel, double price, double originalPrice, int quantity, String imageUrl) {
        this.name = name;
        this.variantLabel = variantLabel;
        this.price = price;
        this.originalPrice = originalPrice;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    public Review getReview() {
        return review;
    }

    public void setReview(Review review) {
        this.review = review;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public void setVariantLabel(String variantLabel) {
        this.variantLabel = variantLabel;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(double originalPrice) {
        this.originalPrice = originalPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
