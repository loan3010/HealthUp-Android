package com.example.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Product implements Serializable {
    private String id;
    private String name;
    private String description;
    private String shortDesc;
    private double price;
    private double oldPrice;
    private double originalPrice;
    private List<String> images;
    
    @PropertyName("cat")
    private Object cat; // Can be String or List<String> from Firestore
    
    private float rating;
    private int reviewCount;
    private int soldCount;
    private int stockCount;
    @PropertyName("favorite")
    private boolean favorite;
    private boolean isSelected = false;
    
    private String ingredients;
    private List<NutritionItem> nutrition;
    private String usage;
    private String origin;

    private Object weights;
    private List<Object> packagingTypes;
    private List<Object> flavors;
    private Object sale;
    private int stock;
    private Object reviews;
    private Timestamp createdAt;

    private String starsDisplay;
    private int sold;
    private String weight;
    private Object stars;
    private String saving;
    private String badge;

    private boolean isFlashSale;
    private boolean isNew;
    private boolean isHot;

    private boolean hasVariants;
    private List<ProductVariant> variants;

    public Product() {
        // Required for Firestore
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getShortDesc() { return shortDesc; }
    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOldPrice() { return oldPrice; }
    public void setOldPrice(double oldPrice) { this.oldPrice = oldPrice; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    @PropertyName("cat")
    public Object getCat() { return cat; }
    
    @PropertyName("cat")
    public void setCat(Object cat) { this.cat = cat; }

    public String getCategory() {
        if (cat instanceof String) return (String) cat;
        if (cat instanceof List && !((List<?>) cat).isEmpty()) {
            return String.valueOf(((List<?>) cat).get(0));
        }
        return "";
    }

    public float getRating() {
        float starsValue = getStarsValue();
        if (starsValue > 0) return starsValue;
        return rating;
    }
    public void setRating(float rating) { this.rating = rating; }

    public int getReviewCount() {
        if (reviewCount > 0) return reviewCount;
        if (reviews instanceof Long) return ((Long) reviews).intValue();
        if (reviews instanceof List) return ((List<?>) reviews).size();
        return 0;
    }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public int getSoldCount() {
        if (sold > 0) return sold;
        return soldCount;
    }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public int getStock() { return stock > 0 ? stock : stockCount; }
    public void setStock(int stock) { this.stock = stock; }

    public int getStockCount() { return stockCount > 0 ? stockCount : stock; }
    public void setStockCount(int stockCount) { this.stockCount = stockCount; }

    @PropertyName("favorite")
    public boolean isFavorite() { return favorite; }
    
    @PropertyName("favorite")
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public boolean isFlashSale() { return isFlashSale; }
    public void setFlashSale(boolean flashSale) { isFlashSale = flashSale; }

    public boolean isNew() {
        if (isNew) return true;
        if (badge != null && badge.toLowerCase().contains("mới")) return true;
        return false;
    }
    public void setNew(boolean aNew) { isNew = aNew; }

    public boolean isHot() {
        if (isHot) return true;
        if (badge != null && badge.toLowerCase().contains("hot")) return true;
        return false;
    }
    public void setHot(boolean hot) { isHot = hot; }

    public String getStarsDisplay() { return starsDisplay; }
    public void setStarsDisplay(String starsDisplay) { this.starsDisplay = starsDisplay; }

    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public Object getStars() { return stars; }
    public void setStars(Object stars) { this.stars = stars; }

    public String getSaving() { return saving; }
    public void setSaving(String saving) { this.saving = saving; }

    public Object getSale() { return sale; }
    public void setSale(Object sale) { this.sale = sale; }

    public Object getReviews() { return reviews; }
    public void setReviews(Object reviews) { this.reviews = reviews; }

    public List<Object> getWeights() {
        if (weights instanceof List) return (List<Object>) weights;
        return new ArrayList<>();
    }
    public void setWeights(Object weights) { this.weights = weights; }

    public List<Object> getFlavors() {
        if (flavors != null) return flavors;
        return new ArrayList<>();
    }
    public void setFlavors(List<Object> flavors) { this.flavors = flavors; }

    public List<Object> getPackagingTypes() {
        if (packagingTypes != null) return packagingTypes;
        return new ArrayList<>();
    }
    public void setPackagingTypes(List<Object> packagingTypes) { this.packagingTypes = packagingTypes; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return null;
    }

    public float getStarsValue() {
        if (stars instanceof Number) {
            return ((Number) stars).floatValue();
        } else if (stars instanceof String) {
            try {
                return Float.parseFloat((String) stars);
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    public static List<Product> getDummyProducts() {
        List<Product> list = new ArrayList<>();
        
        Product p1 = new Product();
        p1.setId("dummy1");
        p1.setName("Granola Siêu Hạt");
        p1.setPrice(150000);
        p1.setOriginalPrice(200000);
        p1.setImages(java.util.Collections.singletonList("images/products/granola_classic.jpg"));
        p1.setNew(true);
        p1.setFlashSale(true);
        list.add(p1);

        Product p2 = new Product();
        p2.setId("dummy2");
        p2.setName("Hạt Điều Rang Muối");
        p2.setPrice(120000);
        p2.setImages(java.util.Collections.singletonList("images/products/hat_dieu.jpg"));
        p2.setNew(true);
        list.add(p2);

        Product p3 = new Product();
        p3.setId("dummy3");
        p3.setName("Snack Rong Biển");
        p3.setPrice(45000);
        p3.setImages(java.util.Collections.singletonList("images/products/rong_bien.jpg"));
        p3.setHot(true);
        list.add(p3);

        return list;
    }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }
    public List<NutritionItem> getNutrition() { return nutrition; }
    public void setNutrition(List<NutritionItem> nutrition) { this.nutrition = nutrition; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public boolean isHasVariants() { return hasVariants || hasResolvableVariants(); }
    public void setHasVariants(boolean hasVariants) { this.hasVariants = hasVariants; }
    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }

    /** Variants for UI: prefers `variants`, falls back to legacy weights/flavors/packagingTypes. */
    public List<ProductVariant> getResolvableVariants() {
        List<ProductVariant> resolved = normalizeVariants(variants);
        if (!resolved.isEmpty()) return resolved;
        return buildVariantsFromLegacyOptions();
    }

    public boolean hasResolvableVariants() {
        return !getResolvableVariants().isEmpty();
    }

    public static Product fromDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Product p = doc.toObject(Product.class);
        if (p == null) return null;
        p.setId(doc.getId());
        p.variants = parseVariantsField(doc.get("variants"), p);
        if (!p.variants.isEmpty()) {
            p.hasVariants = true;
        }
        return p;
    }

    private static List<ProductVariant> parseVariantsField(Object raw, Product parent) {
        List<ProductVariant> result = new ArrayList<>();
        if (raw == null) return result;
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                ProductVariant variant = toVariant(item, parent, result.size());
                if (variant != null) result.add(variant);
            }
        }
        return result;
    }

    private static ProductVariant toVariant(Object item, Product parent, int index) {
        if (item instanceof ProductVariant) {
            ProductVariant v = (ProductVariant) item;
            if (v.getName() != null && !v.getName().isEmpty()) return v;
            return null;
        }
        if (item instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) item;
            String name = firstString(map, "name", "variantName", "label", "title", "option");
            if (name == null || name.isEmpty()) return null;
            ProductVariant v = new ProductVariant();
            v.setId(firstString(map, "id", "variantId"));
            if (v.getId() == null) v.setId("variant_" + index);
            v.setName(name);
            v.setPrice(firstDouble(map, "price", parent.getPrice()));
            v.setStock(firstInt(map, "stock", "stockCount", parent.getStockCount()));
            return v;
        }
        if (item instanceof String) {
            String name = ((String) item).trim();
            if (name.isEmpty()) return null;
            ProductVariant v = new ProductVariant();
            v.setId("variant_" + index);
            v.setName(name);
            v.setPrice(parent.getPrice());
            v.setStock(parent.getStockCount());
            return v;
        }
        return null;
    }

    private List<ProductVariant> normalizeVariants(List<ProductVariant> source) {
        List<ProductVariant> result = new ArrayList<>();
        if (source == null) return result;
        for (int i = 0; i < source.size(); i++) {
            ProductVariant v = source.get(i);
            if (v != null && v.getName() != null && !v.getName().isEmpty()) {
                if (v.getId() == null || v.getId().isEmpty()) v.setId("variant_" + i);
                if (v.getPrice() <= 0) v.setPrice(getPrice());
                if (v.getStock() <= 0) v.setStock(getStockCount());
                result.add(v);
            }
        }
        return result;
    }

    private List<ProductVariant> buildVariantsFromLegacyOptions() {
        List<String> options = new ArrayList<>();
        appendOptionLabels(options, getWeights(), null);
        if (options.isEmpty()) appendOptionLabels(options, getFlavors(), null);
        if (options.isEmpty() && packagingTypes != null) {
            appendOptionLabels(options, packagingTypes, null);
        }
        List<ProductVariant> result = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            ProductVariant v = new ProductVariant();
            v.setId("legacy_" + i);
            v.setName(options.get(i));
            v.setPrice(getPrice());
            v.setStock(getStockCount());
            result.add(v);
        }
        return result;
    }

    private static void appendOptionLabels(List<String> target, List<Object> source, String prefix) {
        if (source == null) return;
        for (Object item : source) {
            String label = String.valueOf(item).trim();
            if (label.isEmpty() || "null".equalsIgnoreCase(label)) continue;
            if (prefix != null && !label.isEmpty()) label = prefix + label;
            if (!target.contains(label)) target.add(label);
        }
    }

    private static String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return null;
    }

    private static double firstDouble(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static int firstInt(Map<?, ?> map, String key1, String key2, int fallback) {
        for (String key : new String[]{key1, key2}) {
            Object value = map.get(key);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) {
                try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
            }
        }
        return fallback;
    }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public static class ProductVariant implements Serializable {
        private String id;
        private String name;
        private double price;
        private int stock;
        public ProductVariant() {}
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }

    public static class NutritionItem implements Serializable {
        private String name;
        private String value;
        private int percent;
        public NutritionItem() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public int getPercent() { return percent; }
        public void setPercent(int percent) { this.percent = percent; }
    }
}
