package com.example.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.List;

public class Review implements Serializable {
    private String userName;
    private String userAvatar;
    private float rating;
    private String comment;
    private String date;
    private String imageUrl;
    private List<String> mediaUris;
    private transient com.google.firebase.Timestamp createdAt;
    private String variantLabel;

    public Review() {}

    public Review(String userName, float rating, String comment) {
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
    }

    public Review(String userName, float rating, String comment, List<String> mediaUris, Timestamp createdAt) {
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
        this.mediaUris = mediaUris;
        this.createdAt = createdAt;
    }

    public Review(float rating, String comment, List<String> mediaUris, Timestamp createdAt) {
        this.rating = rating;
        this.comment = comment;
        this.mediaUris = mediaUris;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public List<String> getMediaUris() { return mediaUris; }
    public void setMediaUris(List<String> mediaUris) { this.mediaUris = mediaUris; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public String getVariantLabel() { return variantLabel; }
    public void setVariantLabel(String variantLabel) { this.variantLabel = variantLabel; }
}
