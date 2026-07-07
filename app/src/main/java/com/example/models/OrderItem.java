package com.example.models;

import java.io.Serializable;

public class OrderItem implements Serializable {
    private String productId;
    private String variantId;
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
        this(null, null, name, variantLabel, price, 0, quantity, imageUrl);
    }

    public OrderItem(String name, String variantLabel, double price, double originalPrice, int quantity, String imageUrl) {
        this(null, null, name, variantLabel, price, originalPrice, quantity, imageUrl);
    }

    public OrderItem(String productId, String variantId, String name, String variantLabel, double price, double originalPrice, int quantity, String imageUrl) {
        this.productId = productId;
        this.variantId = variantId;
        this.name = name;
        this.variantLabel = variantLabel;
        this.price = price;
        this.originalPrice = originalPrice;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getVariantId() {
        return variantId;
    }

    public void setVariantId(String variantId) {
        this.variantId = variantId;
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
