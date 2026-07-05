package com.example.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private String id;
    private String name;
    private String description;
    private String shortDesc;
    private double price;
    private double oldPrice;
    private double originalPrice;
    private List<String> images;
    
    @PropertyName("cat")
    private Object cat; // Can be String or List<String> from Firestore
    
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
    private int stock;
    private Object reviews;
    private Timestamp createdAt;

    private String starsDisplay;
    private int sold;
    private String weight;
    private Object stars;
    private String saving;
    private String badge;

    private boolean isFlashSale;
    private boolean isNew;
    private boolean isHot;

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

    public String getShortDesc() { return shortDesc; }
    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOldPrice() { return oldPrice; }
    public void setOldPrice(double oldPrice) { this.oldPrice = oldPrice; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    @PropertyName("cat")
    public Object getCat() { return cat; }
    
    @PropertyName("cat")
    public void setCat(Object cat) { this.cat = cat; }

    public String getCategory() {
        if (cat instanceof String) return (String) cat;
        if (cat instanceof List && !((List<?>) cat).isEmpty()) {
            return String.valueOf(((List<?>) cat).get(0));
        }
        return "";
    }

    public float getRating() {
        float starsValue = getStarsValue();
        if (starsValue > 0) return starsValue;
        return rating;
    }
    public void setRating(float rating) { this.rating = rating; }

    public int getReviewCount() {
        if (reviewCount > 0) return reviewCount;
        if (reviews instanceof Long) return ((Long) reviews).intValue();
        if (reviews instanceof List) return ((List<?>) reviews).size();
        return 0;
    }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public int getSoldCount() {
        if (sold > 0) return sold;
        return soldCount;
    }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public int getStockCount() { return stockCount > 0 ? stockCount : stock; }
    public void setStockCount(int stockCount) { this.stockCount = stockCount; }

    @PropertyName("favorite")
    public boolean isFavorite() { return favorite; }
    
    @PropertyName("favorite")
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public boolean isFlashSale() { return isFlashSale; }
    public void setFlashSale(boolean flashSale) { isFlashSale = flashSale; }

    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }

    public boolean isHot() { return isHot; }
    public void setHot(boolean hot) { isHot = hot; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return null;
    }

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

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        // Mockup data
        return list;
    }

    // Remaining getters and setters
    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }
    public List<NutritionItem> getNutrition() { return nutrition; }
    public void setNutrition(List<NutritionItem> nutrition) { this.nutrition = nutrition; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public boolean isHasVariants() { return hasVariants; }
    public void setHasVariants(boolean hasVariants) { this.hasVariants = hasVariants; }
    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

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
