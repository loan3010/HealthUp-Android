package com.example.models;

public class FAQCategory {
    private String name;
    private int iconResId;
    private String key;

    public FAQCategory(String name, int iconResId, String key) {
        this.name = name;
        this.iconResId = iconResId;
        this.key = key;
    }

    public String getName() { return name; }
    public int getIconResId() { return iconResId; }
    public String getKey() { return key; }
}
