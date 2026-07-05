package com.example.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.List;

public class Product implements Serializable {
    private String id;
    private String name;
    private String description;
    private double price;
    private double originalPrice;
    private List<String> images;
    
    @PropertyName("cat")
    private String category;
    
    private float rating;
    private int reviewCount;
    private int soldCount;
    private int stockCount;
    @PropertyName("favorite")
    private boolean favorite;
    private boolean isSelected = false;
    
    private String ingredients;
    private List<NutritionItem> nutrition;
    private String usage;
    private String origin;

    private Object weights;
    private List<String> packagingTypes;
    private Object sale;
    private String shortDesc;
    private int stock;
    private Object reviews;
    private Timestamp createdAt;

    private String starsDisplay;
    private int sold;
    private String weight;
    private Object stars;
    private String saving;
    private double oldPrice;
    private String badge;

    private boolean hasVariants;
    private List<ProductVariant> variants;

    public Product() {
        // Required for Firestore
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    @PropertyName("cat")
    public String getCategory() { return category; }
    
    @PropertyName("cat")
    public void setCategory(String category) { this.category = category; }

    public float getRating() {
        float starsValue = getStarsValue();
        if (starsValue > 0) return starsValue;
        return rating;
    }
    public void setRating(float rating) { this.rating = rating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public int getSoldCount() {
        if (sold > 0) return sold;
        return soldCount;
    }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public int getStockCount() { return stockCount; }
    public void setStockCount(int stockCount) { this.stockCount = stockCount; }

    @PropertyName("favorite")
    public boolean isFavorite() { return favorite; }
    
    @PropertyName("favorite")
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public boolean isNew() { return true; }
    public boolean isHot() { return false; }

    public static List<Product> getDummyProducts() {
        List<Product> list = new java.util.ArrayList<>();
        
        Product p1 = new Product();
        p1.setId("p1");
        p1.setName("Hạt Mix Dinh Dưỡng Cao Cấp");
        p1.setPrice(150000);
        p1.setCategory("Hạt dinh dưỡng");
        p1.setRating(4.8f);
        p1.setSoldCount(1200);
        p1.setImages(java.util.Arrays.asList("almond-roasted-natural.png"));
        list.add(p1);

        Product p2 = new Product();
        p2.setId("p2");
        p2.setName("Granola Siêu Hạt & Trái Cây");
        p2.setPrice(120000);
        p2.setCategory("Granola");
        p2.setRating(4.9f);
        p2.setSoldCount(850);
        p2.setImages(java.util.Arrays.asList("granola-truyen-thong.png"));
        list.add(p2);

        Product p3 = new Product();
        p3.setId("p3");
        p3.setName("Xoài Sấy Dẻo Loại 1");
        p3.setPrice(85000);
        p3.setCategory("Trái cây sấy");
        p3.setRating(4.7f);
        p3.setSoldCount(2000);
        p3.setImages(java.util.Arrays.asList("xoai-say-deo.png"));
        list.add(p3);

        return list;
    }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }

    public List<NutritionItem> getNutrition() { return nutrition; }
    public void setNutrition(List<NutritionItem> nutrition) { this.nutrition = nutrition; }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public Object getWeights() { return weights; }
    public void setWeights(Object weights) { this.weights = weights; }

    public List<ProductWeight> getWeightsList() {
        if (weights instanceof List) {
            List<?> rawList = (List<?>) weights;
            List<ProductWeight> result = new java.util.ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof ProductWeight) {
                    result.add((ProductWeight) item);
                } else if (item instanceof java.util.Map) {
                    // This is for when Firestore gives us a Map instead of the object
                    try {
                        java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
                        ProductWeight pw = new ProductWeight();
                        if (map.containsKey("value")) pw.setValue(String.valueOf(map.get("value")));
                        if (map.containsKey("price")) {
                            Object p = map.get("price");
                            if (p instanceof Number) pw.setPrice(((Number) p).doubleValue());
                        }
                        if (map.containsKey("outOfStock")) {
                            Object oos = map.get("outOfStock");
                            if (oos instanceof Boolean) pw.setOutOfStock((Boolean) oos);
                        }
                        if (map.containsKey("label")) pw.setLabel(String.valueOf(map.get("label")));
                        result.add(pw);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return result;
        }
        return null;
    }

    public String getShortDesc() { return shortDesc; }
    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public List<String> getPackagingTypes() { return packagingTypes; }
    public void setPackagingTypes(List<String> packagingTypes) { this.packagingTypes = packagingTypes; }

    public boolean isSale() {
        if (sale instanceof Boolean) return (Boolean) sale;
        if (sale instanceof String) return Boolean.parseBoolean((String) sale);
        return false;
    }
    public void setSale(Object sale) { this.sale = sale; }

    public Object getReviews() { return reviews; }
    public void setReviews(Object reviews) { this.reviews = reviews; }

    public String getStarsDisplay() { return starsDisplay; }
    public void setStarsDisplay(String starsDisplay) { this.starsDisplay = starsDisplay; }

    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public float getStarsValue() {
        if (stars instanceof Number) {
            return ((Number) stars).floatValue();
        } else if (stars instanceof String) {
            try {
                return Float.parseFloat((String) stars);
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    public Object getStars() { return stars; }
    public void setStars(Object stars) { this.stars = stars; }

    public String getSaving() { return saving; }
    public void setSaving(String saving) { this.saving = saving; }

    public double getOldPrice() { return oldPrice; }
    public void setOldPrice(double oldPrice) { this.oldPrice = oldPrice; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isHasVariants() { return hasVariants; }
    public void setHasVariants(boolean hasVariants) { this.hasVariants = hasVariants; }

    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return null;
    }

    public int getDiscountPercent() {
        double comparePrice = oldPrice > 0 ? oldPrice : originalPrice;
        if (comparePrice > price && comparePrice > 0) {
            return (int) (((comparePrice - price) / comparePrice) * 100);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Double.compare(product.price, price) == 0 &&
                Float.compare(product.rating, rating) == 0 &&
                favorite == product.favorite &&
                isSelected == product.isSelected &&
                java.util.Objects.equals(id, product.id) &&
                java.util.Objects.equals(name, product.name) &&
                java.util.Objects.equals(category, product.category);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, price, category, rating, favorite, isSelected);
    }

    public static class ProductWeight implements Serializable {
        private String value;
        private double price;
        private boolean outOfStock;
        private String label;

        public ProductWeight() {}

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        
        @PropertyName("outOfStock")
        public boolean isOutOfStock() { return outOfStock; }
        @PropertyName("outOfStock")
        public void setOutOfStock(boolean outOfStock) { this.outOfStock = outOfStock; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class ProductVariant implements Serializable {
        private String id;
        private String name;
        private double price;
        private int stock;

        public ProductVariant() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }

    public static class NutritionItem implements Serializable {
        private String name;
        private String value;
        private int percent;

        public NutritionItem() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public int getPercent() { return percent; }
        public void setPercent(int percent) { this.percent = percent; }
    }
}
