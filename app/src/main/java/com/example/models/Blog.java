package com.example.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;

public class Blog implements Serializable {
    private String id;
    private String title;
    private String content;
    private String imageUrl;
    private Timestamp publishedAt;

    public Blog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Timestamp getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Timestamp publishedAt) { this.publishedAt = publishedAt; }
}
