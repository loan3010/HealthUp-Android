package com.group.models;

import java.io.Serializable;

public class CartItem implements Serializable {
    private String id;
    private String productId;
    private Product product;
    private int quantity;
    private String userId;
    private String variantId;
    private String variantName;
    private double price;

    public CartItem() {}

    public CartItem(String productId, Product product, int quantity, String userId) {
        this.productId = productId;
        this.product = product;
        this.quantity = quantity;
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
