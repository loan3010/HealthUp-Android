package com.example.models;

import java.io.Serializable;

public class Blog implements Serializable {
    private String id;
    private String title;
    private String content;
    private String imageUrl;
    private String author;
    private long timestamp;
    private String category;

    public Blog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
