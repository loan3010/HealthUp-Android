package com.example.models;

import java.io.Serializable;

public class Blog implements Serializable {
    private String id;
    private String title;
    private String content;
    private String imageUrl;
    private long timestamp;
    private String author;

    public Blog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
