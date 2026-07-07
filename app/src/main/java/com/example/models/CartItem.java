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

    private String weight;
    private String flavor;
    private String packageType;

    private boolean selected;

    // Backward-compatible fields used by tngan product detail / wishlist flows
    private Product product;
    private String userId;
    private String variantId;
    private String variantName;

    public CartItem() {
    }

    public CartItem(String productId, Product product, int quantity, String userId) {
        this.productId = productId;
        this.product = product;
        this.quantity = quantity;
        this.userId = userId;
        if (product != null) {
            this.name = product.getName();
            this.imageUrl = product.getImageUrl();
            this.price = product.getPrice();
            this.originalPrice = product.getOriginalPrice();
        }
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
    public void setWeight(String weight) { this.weight = extractLabel(weight); }

    public String getFlavor() { return flavor; }
    public void setFlavor(String flavor) { this.flavor = extractLabel(flavor); }

    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = extractLabel(packageType); }

    private String extractLabel(String input) {
        if (input == null) return null;
        if (input.contains("label=")) {
            try {
                int start = input.indexOf("label=") + 6;
                int end = input.indexOf(",", start);
                if (end == -1) end = input.indexOf("}", start);
                if (end != -1) return input.substring(start, end).trim();
            } catch (Exception ignored) {}
        }
        return input;
    }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public String getVariantLabel() {
        // Ưu tiên hiển thị các thành phần đã chọn lẻ trước để đảm bảo cập nhật tức thì
        StringBuilder sb = new StringBuilder();
        if (weight != null && !weight.isEmpty()) sb.append(weight);
        if (flavor != null && !flavor.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(flavor);
        }
        if (packageType != null && !packageType.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(packageType);
        }
        
        String label = sb.toString();
        if (!label.isEmpty()) return label;

        // Nếu không có các trường trên mới dùng variantName cũ
        if (variantName != null && !variantName.isEmpty()) {
            return extractLabel(variantName);
        }
        
        return "";
    }
}
