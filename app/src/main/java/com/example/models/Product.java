package com.example.models;

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
    private Object cat;          
    private boolean isFlashSale;
    private boolean isNew;
    private boolean isHot;
    private String stars;
    private String starsDisplay;
    private float rating;      
    private int sold;
    private int soldCount;     
    private int stock;
    private String weight;
    private List<Object> weights;
    private List<Object> flavors;
    private List<Object> packagingTypes;
    private Object nutrition;
    private Object reviews;
    private String saving;
    private String sale;
    private String badge;
    private boolean isFavorite;
    private int reviewCount;

    public Product() {}

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

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public void setCat(Object cat) {
        this.cat = cat;
    }

    public String getCat() {
        if (cat instanceof String) return (String) cat;
        if (cat instanceof List && !((List<?>) cat).isEmpty()) {
            return String.valueOf(((List<?>) cat).get(0));
        }
        return "";
    }

    public List<String> getCategories() {
        List<String> list = new ArrayList<>();
        if (cat instanceof String) {
            list.add((String) cat);
        } else if (cat instanceof List) {
            for (Object o : (List<?>) cat) {
                list.add(String.valueOf(o));
            }
        }
        return list;
    }
    public boolean isFlashSale() { return isFlashSale; }
    public void setFlashSale(boolean flashSale) { isFlashSale = flashSale; }
    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }
    public boolean isHot() { return isHot; }
    public void setHot(boolean hot) { isHot = hot; }
    public String getStars() { return stars; }
    public void setStars(String stars) { this.stars = stars; }
    public String getStarsDisplay() { return starsDisplay; }
    public void setStarsDisplay(String starsDisplay) { this.starsDisplay = starsDisplay; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }
    public int getSoldCount() { return soldCount; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public List<Object> getWeights() { return weights; }
    public void setWeights(List<Object> weights) { this.weights = weights; }

    public List<Object> getFlavors() { return flavors; }
    public void setFlavors(List<Object> flavors) { this.flavors = flavors; }

    public List<Object> getPackagingTypes() { return packagingTypes; }
    public void setPackagingTypes(List<Object> packagingTypes) { this.packagingTypes = packagingTypes; }

    public Object getNutrition() { return nutrition; }
    public void setNutrition(Object nutrition) { this.nutrition = nutrition; }

    public List<Review> getReviewsList() {
        if (reviews instanceof List) {
            try {
                return (List<Review>) reviews;
            } catch (ClassCastException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    public void setReviews(Object reviews) {
        this.reviews = reviews;
        if (reviews instanceof Long) {
            this.reviewCount = ((Long) reviews).intValue();
        } else if (reviews instanceof List) {
            this.reviewCount = ((List<?>) reviews).size();
        }
    }

    public String getSaving() { return saving; }
    public void setSaving(String saving) { this.saving = saving; }

    public String getSale() { return sale; }
    public void setSale(String sale) { this.sale = sale; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public int getReviewCount() {
        if (reviewCount > 0) return reviewCount;
        if (reviews instanceof Long) return ((Long) reviews).intValue();
        if (reviews instanceof List) return ((List<?>) reviews).size();
        return 0;
    }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return "";
    }

    public String getCategory() {
        return getCat();
    }

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        // Mockup dữ liệu mẫu nếu Firebase trống
        return list;
    }
}
