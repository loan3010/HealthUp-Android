package com.example.models;

import java.io.Serializable;

public class CartItem implements Serializable {
    private String id;
    private String productId;
    private String name;
    private String imageUrl;
    private double price;
    private double originalPrice;
    private int quantity;
    private int stock;

    // Thuộc tính phân loại (nếu sản phẩm có biến thể)
    private String weight;
    private String flavor;
    private String packageType;

    private boolean selected;

    public CartItem() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public String getFlavor() { return flavor; }
    public void setFlavor(String flavor) { this.flavor = flavor; }

    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    /** Nhãn hiển thị biến thể, vd: "400g, Túi zip" */
    public String getVariantLabel() {
        StringBuilder sb = new StringBuilder();
        if (weight != null && !weight.isEmpty()) sb.append(weight);
        if (packageType != null && !packageType.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(packageType);
        }
        return sb.toString();
    }
}