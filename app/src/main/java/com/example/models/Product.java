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
    private String cat;          
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
    private List<Object> packagingTypes;
    private Object nutrition;
    private List<Review> reviews;

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
    public String getCat() { return cat; }
    public void setCat(String cat) { this.cat = cat; }
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

    public List<Object> getPackagingTypes() { return packagingTypes; }
    public void setPackagingTypes(List<Object> packagingTypes) { this.packagingTypes = packagingTypes; }

    public Object getNutrition() { return nutrition; }
    public void setNutrition(Object nutrition) { this.nutrition = nutrition; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return "";
    }

    public String getCategory() {
        return cat;
    }

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        // Mockup dữ liệu mẫu nếu Firebase trống
        return list;
    }
}
