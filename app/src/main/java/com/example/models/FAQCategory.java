package com.example.models;

public class FAQCategory {
    private String name;
    private int iconResId;

    public FAQCategory(String name, int iconResId) {
        this.name = name;
        this.iconResId = iconResId;
    }

    public String getName() { return name; }
    public int getIconResId() { return iconResId; }
}
