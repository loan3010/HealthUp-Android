package com.group.models;

public class ReturnReason {
    private String title;
    private String description;

    public ReturnReason(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
