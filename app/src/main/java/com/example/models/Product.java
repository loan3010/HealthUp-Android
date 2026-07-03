package com.example.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private String id;
    private String name;
    private String description;
    private double price;
    private double originalPrice;
    private List<String> images; 
    private String cat;          
    private boolean isFlashSale;
    private boolean isNew;
    private boolean isHot;
    private float rating;      // Giữ lại để không lỗi code cũ
    private int soldCount;     // Giữ lại để không lỗi code cũ

    public Product() {}

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
    public String getCat() { return cat; }
    public void setCat(String cat) { this.cat = cat; }
    public boolean isFlashSale() { return isFlashSale; }
    public void setFlashSale(boolean flashSale) { isFlashSale = flashSale; }
    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }
    public boolean isHot() { return isHot; }
    public void setHot(boolean hot) { isHot = hot; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    public int getSoldCount() { return soldCount; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        // Mockup dữ liệu mẫu nếu Firebase trống
        return list;
    }
}
