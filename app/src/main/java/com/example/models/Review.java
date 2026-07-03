package com.example.models;

import java.io.Serializable;
import java.util.List;

public class Review implements Serializable {
    private float rating;
    private String comment;
    private List<String> mediaUris;
    private long createdAt;

    public Review() {}

    public Review(float rating, String comment, List<String> mediaUris, long createdAt) {
        this.rating = rating;
        this.comment = comment;
        this.mediaUris = mediaUris;
        this.createdAt = createdAt;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<String> getMediaUris() {
        return mediaUris;
    }

    public void setMediaUris(List<String> mediaUris) {
        this.mediaUris = mediaUris;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
