package com.example.models;


import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;


public class Blog implements Serializable {
    private String id;
    private String title;
    private String content;
    private String imageUrl;
    private String author;

    @PropertyName("tag")
    private String category;

    // FIX (crash "bấm vào card/Đọc tiếp thì app bị out"): com.google.firebase.Timestamp KHÔNG
    // implement java.io.Serializable (chỉ implement Parcelable), trong khi Blog implements
    // Serializable để truyền được qua Intent.putExtra(). Cơ chế Java Serialization mặc định yêu
    // cầu MỌI field non-transient, non-null đều phải Serializable -> giữ Timestamp làm field
    // thường sẽ luôn crash NotSerializableException ngay khi startActivity() ghi Blog vào Bundle.
    // Đánh dấu transient để loại field này khỏi cơ chế serialize (transient KHÔNG ảnh hưởng việc
    // Firestore đọc/gán giá trị bằng reflection, nên map dữ liệu từ Firestore vẫn hoạt động
    // bình thường).
    private transient Timestamp publishedAt;

    // Lưu lại mốc thời gian dưới dạng long (kiểu nguyên thủy, luôn Serializable an toàn) ngay khi
    // Firestore gán publishedAt, để giá trị KHÔNG bị mất khi Blog được truyền qua Intent (field
    // transient ở trên sẽ về null sau khi deserialize qua Intent).
    private long publishedAtMillis;


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

    @PropertyName("tag")
    public String getCategory() { return category; }

    @PropertyName("tag")
    public void setCategory(String category) { this.category = category; }

    public Timestamp getPublishedAt() { return publishedAt; }

    public void setPublishedAt(Timestamp publishedAt) {
        this.publishedAt = publishedAt;
        this.publishedAtMillis = publishedAt != null ? publishedAt.toDate().getTime() : 0L;
    }

    /** Mốc thời gian (millis) dùng để sắp xếp/hiển thị; giữ nguyên giá trị kể cả sau khi Blog
     * bị truyền qua Intent (khác với getPublishedAt(), có thể null sau khi deserialize). */
    public long getTimestamp() {
        return publishedAtMillis;
    }
}