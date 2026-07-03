package com.example.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Product implements Serializable {
    private String id;
    private String name;
    private double price;
    private float rating;
    private int soldCount;
    private String imageUrl;

    public Product() {}

    public Product(String id, String name, double price, float rating, int soldCount, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.rating = rating;
        this.soldCount = soldCount;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getSoldCount() { return soldCount; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        list.add(new Product("1", "Hạt macca nứt vỏ Đắk Lắk", 180000, 4.9f, 3400, "https://via.placeholder.com/150"));
        list.add(new Product("2", "Mật ong hoa nhãn nguyên chất", 225000, 5.0f, 1100, "https://via.placeholder.com/150"));
        list.add(new Product("3", "Bột ngũ cốc dinh dưỡng 22 loại hạt", 150000, 4.8f, 2500, "https://via.placeholder.com/150"));
        list.add(new Product("4", "Trà gạo lứt đậu đen xạ đen", 95000, 4.7f, 5600, "https://via.placeholder.com/150"));
        list.add(new Product("5", "Hạt điều vị tỏi ớt hũ 500g", 135000, 4.9f, 1200, "https://via.placeholder.com/150"));
        list.add(new Product("6", "Tinh bột nghệ nguyên chất", 210000, 5.0f, 800, "https://via.placeholder.com/150"));
        return list;
    }
}
