package com.example.models;

import com.google.firebase.Timestamp;
import java.io.Serializable;

public class PolicyItem implements Serializable {
    private String id;
    private String title;
    private String content;
    private long order;
    private Timestamp updatedAt;

    public PolicyItem() {} // bắt buộc cho Firestore

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getOrder() { return order; }
    public void setOrder(long order) { this.order = order; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
