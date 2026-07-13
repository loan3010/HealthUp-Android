package com.example.models;

public class Category {
    private String id;
    private String name;
    private String iconUrl;

    public Category() {}

    public Category(String id, String name, String iconUrl) {
        this.id = id;
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public static java.util.List<Category> getDummyCategories() {
        java.util.List<Category> list = new java.util.ArrayList<>();
        list.add(new Category("1", "Hạt dinh dưỡng", "grain.png"));
        list.add(new Category("2", "Granola", "dry.png"));
        list.add(new Category("3", "Trái cây sấy", "fruit.png"));
        list.add(new Category("4", "Đồ ăn vặt", "cooking.png"));
        list.add(new Category("5", "Trà thảo mộc", "leaf.png"));
        list.add(new Category("6", "Combo", "multiple.png"));
        return list;
    }
}
