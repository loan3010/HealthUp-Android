package com.example.models;

import java.io.Serializable;

public class Blog implements Serializable {
    private String id;
    private Object title;
    private Object content;
    private String imageUrl;
    private Object timestamp;
    private String author;

    public Blog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() {
        return title != null ? String.valueOf(title) : "";
    }
    public void setTitle(Object title) { this.title = title; }

    public String getContent() {
        if (content instanceof String) return (String) content;
        if (content instanceof java.util.List) {
            StringBuilder sb = new StringBuilder();
            for (Object o : (java.util.List<?>) content) {
                sb.append(o.toString()).append("\n");
            }
            return sb.toString().trim();
        }
        return content != null ? String.valueOf(content) : "";
    }
    public void setContent(Object content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getTimestamp() {
        if (timestamp instanceof Long) return (Long) timestamp;
        if (timestamp instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) timestamp).getSeconds() * 1000;
        }
        return 0;
    }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
