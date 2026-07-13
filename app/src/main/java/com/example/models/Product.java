package com.example.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Product implements Serializable {
    private String id;
    private String productCode;
    private String name;
    private String description;
    private String shortDesc;
    private double price;
    private double oldPrice;
    private double originalPrice;
    private List<String> images;
    private String image;
    private String imageUrl;
    
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
    private String nutritionText;
    private String usage;
    private String origin;

    private Object weights;
    private Object packagingTypes;
    private Object flavors;
    private Object sale;
    private int stock;
    private Object reviews;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    private String starsDisplay;
    private int sold;
    private String weight;
    private Object stars;
    private String saving;
    private String badge;

    private boolean isFlashSale;
    private boolean isNew;
    private boolean isHot;
    private boolean hidden;
    private boolean draft;

    private boolean hasVariants;
    private List<ProductVariant> variants;
    private transient List<ProductVariant> resolvedVariantsCache;

    public Product() {
        // Required for Firestore
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

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

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isDraft() { return draft; }
    public void setDraft(boolean draft) { this.draft = draft; }

    /** True when product should not appear in the shop for buyers. */
    public static boolean readHiddenFlag(DocumentSnapshot doc) {
        if (doc == null) return false;
        Boolean hiddenVal = doc.getBoolean("hidden");
        return hiddenVal != null && hiddenVal;
    }

    public static boolean readDraftFlag(DocumentSnapshot doc) {
        if (doc == null) return false;
        Boolean draftVal = doc.getBoolean("draft");
        return draftVal != null && draftVal;
    }

    /** Buyer-visible listing: not hidden and not draft. */
    public static boolean isVisibleToBuyers(DocumentSnapshot doc) {
        return !readHiddenFlag(doc) && !readDraftFlag(doc);
    }

    /** True for products created from the admin panel. */
    public static boolean isAdminListedProduct(DocumentSnapshot doc) {
        if (doc == null) return false;
        Boolean adminCreated = doc.getBoolean("adminCreated");
        if (adminCreated != null) {
            return adminCreated;
        }
        Boolean isNewVal = doc.getBoolean("isNew");
        return isNewVal != null && isNewVal;
    }

    /** Original catalog products for featured sections on the home screen. */
    public static boolean isCatalogFeaturedProduct(DocumentSnapshot doc) {
        return isVisibleToBuyers(doc) && !isAdminListedProduct(doc);
    }

    public static long readCreatedAtMillis(DocumentSnapshot doc) {
        if (doc == null) return 0L;
        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
        return createdAt != null ? createdAt.toDate().getTime() : 0L;
    }

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
        return toObjectList(weights);
    }
    public void setWeights(Object weights) { this.weights = weights; }

    public List<Object> getFlavors() {
        return toObjectList(flavors);
    }
    public void setFlavors(Object flavors) { this.flavors = flavors; }

    public List<Object> getPackagingTypes() {
        return toObjectList(packagingTypes);
    }
    public void setPackagingTypes(Object packagingTypes) { this.packagingTypes = packagingTypes; }

    public String getImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        if (imageUrl != null && !imageUrl.isEmpty()) {
            return imageUrl;
        }
        if (image != null && !image.isEmpty()) {
            return image;
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
    public String getNutritionText() { return nutritionText; }
    public void setNutritionText(String nutritionText) { this.nutritionText = nutritionText; }

    /** Text for product detail: prefers admin nutritionText, else formats structured list. */
    @Nullable
    public String getDisplayNutrition() {
        if (nutritionText != null && !nutritionText.trim().isEmpty()) {
            return nutritionText.trim();
        }
        if (nutrition == null || nutrition.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (NutritionItem item : nutrition) {
            if (item == null || item.getName() == null || item.getName().trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("• ").append(item.getName().trim());
            if (item.getValue() != null && !item.getValue().trim().isEmpty()) {
                builder.append(": ").append(item.getValue().trim());
            }
            if (item.getPercent() > 0) {
                builder.append(" (").append(item.getPercent()).append("%)");
            }
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public long getSortTimeMillis() {
        if (updatedAt != null) return updatedAt.toDate().getTime();
        if (createdAt != null) return createdAt.toDate().getTime();
        return 0L;
    }
    public boolean isHasVariants() { return hasVariants || hasResolvableVariants(); }
    public void setHasVariants(boolean hasVariants) { this.hasVariants = hasVariants; }
    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }

    /** Variants for UI: prefers `variants`, merges pricing from `weights`, falls back to legacy options. */
    public List<ProductVariant> getResolvableVariants() {
        if (resolvedVariantsCache != null) return resolvedVariantsCache;
        List<ProductVariant> resolved;
        if (variants != null && !variants.isEmpty()) {
            resolved = new ArrayList<>(variants);
            List<ProductVariant> fromLegacy = buildVariantsFromLegacyOptions();
            if (!fromLegacy.isEmpty()) {
                mergeVariantPricing(resolved, fromLegacy);
            }
        } else {
            resolved = buildVariantsFromLegacyOptions();
        }
        resolvedVariantsCache = normalizeVariants(resolved);
        return resolvedVariantsCache;
    }

    /** Returns a map of category names to their respective variants (Weights, Flavors, etc.) */
    public Map<String, List<ProductVariant>> getGroupedVariants() {
        Map<String, List<ProductVariant>> groups = new LinkedHashMap<>();
        
        List<ProductVariant> w = normalizeVariants(parseVariantsField(weights, this));
        if (!w.isEmpty()) groups.put("Khối lượng", w);
        
        List<ProductVariant> f = normalizeVariants(parseVariantsField(flavors, this));
        if (!f.isEmpty()) groups.put("Hương vị", f);
        
        List<ProductVariant> p = normalizeVariants(parseVariantsField(packagingTypes, this));
        if (!p.isEmpty()) groups.put("Loại đóng gói", p);

        if (groups.isEmpty() && variants != null && !variants.isEmpty()) {
            inferGroupsFromComboVariants(groups);
        }

        // Only add generic "Phân loại" if we don't have ANY specific groups
        if (groups.isEmpty() && variants != null && !variants.isEmpty()) {
            groups.put("Phân loại", normalizeVariants(variants));
        }
        
        return groups;
    }

    private void inferGroupsFromComboVariants(@NonNull Map<String, List<ProductVariant>> groups) {
        List<String> dimA = new ArrayList<>();
        List<String> dimB = new ArrayList<>();
        for (ProductVariant variant : variants) {
            if (variant == null || variant.getName() == null) continue;
            String name = variant.getName();
            if (!name.contains(" · ")) continue;
            String[] parts = name.split(" · ");
            if (parts.length != 2) continue;
            String first = parts[0].trim();
            String second = parts[1].trim();
            if (!first.isEmpty() && !dimA.contains(first)) dimA.add(first);
            if (!second.isEmpty() && !dimB.contains(second)) dimB.add(second);
        }
        if (!dimA.isEmpty()) {
            List<ProductVariant> flavorOptions = new ArrayList<>();
            for (int i = 0; i < dimA.size(); i++) {
                ProductVariant option = new ProductVariant();
                option.setId("flavor_" + i);
                option.setName(dimA.get(i));
                option.setPrice(getPrice());
                option.setOriginalPrice(getOriginalPrice());
                flavorOptions.add(option);
            }
            groups.put("Hương vị", flavorOptions);
        }
        if (!dimB.isEmpty()) {
            List<ProductVariant> weightOptions = new ArrayList<>();
            for (int i = 0; i < dimB.size(); i++) {
                ProductVariant option = new ProductVariant();
                option.setId("weight_" + i);
                option.setName(dimB.get(i));
                option.setPrice(getPrice());
                option.setOriginalPrice(getOriginalPrice());
                weightOptions.add(option);
            }
            groups.put("Khối lượng", weightOptions);
        }
    }

    private boolean isSameVariantList(List<ProductVariant> list1, List<ProductVariant> list2) {
        if (list1.size() != list2.size()) return false;
        for (int i = 0; i < list1.size(); i++) {
            String n1 = list1.get(i).getName();
            String n2 = list2.get(i).getName();
            if (n1 == null || !n1.equals(n2)) return false;
        }
        return true;
    }

    public boolean hasResolvableVariants() {
        return !getResolvableVariants().isEmpty() || !getGroupedVariants().isEmpty();
    }

    /**
     * True when the buyer must pick among multiple options (opens variant bottom sheet).
     * Products with a single SKU — including one option per group (e.g. one weight + one flavor)
     * — return false so the app can add to cart directly.
     */
    public boolean requiresVariantSelection() {
        Map<String, List<ProductVariant>> grouped = getGroupedVariants();
        if (!grouped.isEmpty()) {
            for (List<ProductVariant> options : grouped.values()) {
                if (options != null && options.size() > 1) {
                    return true;
                }
            }
            return false;
        }
        return getResolvableVariants().size() > 1;
    }

    /**
     * Resolves the variant to use without a picker: explicit name, sole resolvable variant,
     * or the only option in each grouped dimension.
     */
    @Nullable
    public ProductVariant getDefaultVariant(@Nullable String preferredName) {
        if (preferredName != null && !preferredName.trim().isEmpty()) {
            ProductVariant named = findVariantByName(preferredName.trim());
            if (named != null) {
                return named;
            }
        }
        List<ProductVariant> resolved = getResolvableVariants();
        if (resolved.size() == 1) {
            return resolved.get(0);
        }
        Map<String, List<ProductVariant>> grouped = getGroupedVariants();
        if (grouped.isEmpty()) {
            return null;
        }
        ProductVariant best = null;
        for (List<ProductVariant> options : grouped.values()) {
            if (options == null || options.isEmpty()) {
                continue;
            }
            if (options.size() > 1) {
                return null;
            }
            ProductVariant candidate = options.get(0);
            if (best == null) {
                best = candidate;
            } else if (candidate.getPrice() > 0 && candidate.getPrice() != getPrice()) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Resolves the full SKU variant from per-group selections (e.g. Hương vị + Khối lượng → "Dâu · 250g").
     */
    @Nullable
    public ProductVariant resolveComboVariant(@NonNull Map<String, ProductVariant> selectedByGroup) {
        if (selectedByGroup == null || selectedByGroup.isEmpty()) {
            return null;
        }
        if (selectedByGroup.size() == 1) {
            ProductVariant only = selectedByGroup.values().iterator().next();
            if (only == null) return null;
            ProductVariant resolved = findVariantByName(only.getName());
            return resolved != null ? resolved : only;
        }

        List<String> parts = new ArrayList<>();
        ProductVariant flavor = selectedByGroup.get("Hương vị");
        ProductVariant weight = selectedByGroup.get("Khối lượng");
        ProductVariant packaging = selectedByGroup.get("Loại đóng gói");
        if (flavor != null && flavor.getName() != null && !flavor.getName().isEmpty()) {
            parts.add(flavor.getName().trim());
        }
        if (weight != null && weight.getName() != null && !weight.getName().isEmpty()) {
            parts.add(weight.getName().trim());
        }
        if (packaging != null && packaging.getName() != null && !packaging.getName().isEmpty()) {
            parts.add(packaging.getName().trim());
        }
        if (parts.isEmpty()) {
            for (ProductVariant variant : selectedByGroup.values()) {
                if (variant != null && variant.getName() != null && !variant.getName().isEmpty()) {
                    parts.add(variant.getName().trim());
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            ProductVariant resolved = findVariantByName(parts.get(0));
            return resolved != null ? resolved : selectedByGroup.values().iterator().next();
        }

        String combo = String.join(" · ", parts);
        ProductVariant comboVariant = findVariantByName(combo);
        if (comboVariant != null) {
            return comboVariant;
        }
        for (ProductVariant variant : getResolvableVariants()) {
            if (variant.getName() == null) continue;
            boolean allMatch = true;
            for (String part : parts) {
                if (!variant.getName().contains(part)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return variant;
            }
        }
        return findVariantByName(parts.get(0));
    }

    /** Finds a variant by display name (e.g. "100g", "200g"). */
    public ProductVariant findVariantByName(String name) {
        if (name == null || name.isEmpty()) return null;
        String searchName = name.trim().toLowerCase();
        for (ProductVariant variant : getResolvableVariants()) {
            if (variant.getName() != null && variant.getName().trim().toLowerCase().equals(searchName)) {
                return variant;
            }
        }
        for (List<ProductVariant> options : getGroupedVariants().values()) {
            if (options == null) continue;
            for (ProductVariant variant : options) {
                if (variant.getName() != null && variant.getName().trim().toLowerCase().equals(searchName)) {
                    return variant;
                }
            }
        }
        return null;
    }

    /** Price for a weight/option label; falls back to base product price. */
    public double getPriceForOption(String optionLabel) {
        ProductVariant variant = findVariantByName(optionLabel);
        if (variant != null && variant.getPrice() > 0) return variant.getPrice();
        return getPrice();
    }

    public static Product fromDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Product p = doc.toObject(Product.class);
        if (p == null) return null;
        p.setId(doc.getId());
        p.resolvedVariantsCache = null;
        p.images = normalizeImageList(doc.get("images"), p.imageUrl, p.image);

        // Thu thập tất cả variants từ mọi nguồn (weights, flavors, pkg, variants)
        List<ProductVariant> allVariants = new ArrayList<>();

        // 1. Phân loại chính (nếu có)
        allVariants.addAll(parseVariantsField(doc.get("variants"), p));

        // 2. Khối lượng (Weights)
        List<ProductVariant> fromWeights = parseVariantsField(doc.get("weights"), p);
        allVariants = mergeVariantLists(allVariants, fromWeights);
        
        // 3. Hương vị (Flavors)
        List<ProductVariant> fromFlavors = parseVariantsField(doc.get("flavors"), p);
        allVariants = mergeVariantLists(allVariants, fromFlavors);
        
        // 4. Đóng gói (PackagingTypes)
        List<ProductVariant> fromPkg = parseVariantsField(doc.get("packagingTypes"), p);
        allVariants = mergeVariantLists(allVariants, fromPkg);

        p.variants = allVariants;
        if (!p.variants.isEmpty()) {
            p.hasVariants = true;
        }

        Boolean hiddenVal = doc.getBoolean("hidden");
        if (hiddenVal != null) {
            p.setHidden(hiddenVal);
        }
        Boolean draftVal = doc.getBoolean("draft");
        if (draftVal != null) {
            p.setDraft(draftVal);
        }
        p.setUpdatedAt(doc.getTimestamp("updatedAt"));
        if (p.getCreatedAt() == null) {
            p.setCreatedAt(doc.getTimestamp("createdAt"));
        }
        return p;
    }

    /** Normalizes Firestore image fields (list, single string, or legacy image/imageUrl). */
    @NonNull
    private static List<String> normalizeImageList(Object rawImages, String imageUrl, String image) {
        List<String> result = new ArrayList<>();
        if (rawImages instanceof List) {
            for (Object item : (List<?>) rawImages) {
                if (item == null) continue;
                String url = String.valueOf(item).trim();
                if (!url.isEmpty() && !"null".equalsIgnoreCase(url) && !result.contains(url)) {
                    result.add(url);
                }
            }
        } else if (rawImages instanceof String) {
            String url = ((String) rawImages).trim();
            if (!url.isEmpty()) result.add(url);
        }
        if (result.isEmpty() && imageUrl != null && !imageUrl.trim().isEmpty()) {
            result.add(imageUrl.trim());
        }
        if (result.isEmpty() && image != null && !image.trim().isEmpty()) {
            result.add(image.trim());
        }
        return result;
    }

    private static List<ProductVariant> mergeVariantLists(List<ProductVariant> target, List<ProductVariant> source) {
        if (source.isEmpty()) return target;
        if (target.isEmpty()) return new ArrayList<>(source);

        for (ProductVariant s : source) {
            if (s.getName() == null || s.getName().isEmpty()) continue;

            boolean found = false;
            for (ProductVariant t : target) {
                if (s.getName().equalsIgnoreCase(t.getName().trim())) {
                    // Cập nhật giá và kho nếu nguồn mới có giá trị tốt hơn
                    if (s.getPrice() > 0) t.setPrice(s.getPrice());
                    if (s.getOriginalPrice() > 0) t.setOriginalPrice(s.getOriginalPrice());
                    if (s.getStock() > 0) t.setStock(s.getStock());
                    found = true;
                    break;
                }
            }
            if (!found) {
                target.add(s);
            }
        }
        return target;
    }

    /** Applies per-variant price/stock from weights when the variants list only has labels. */
    private static void mergeVariantPricing(List<ProductVariant> target, List<ProductVariant> pricingSource) {
        Map<String, ProductVariant> byName = new HashMap<>();
        for (ProductVariant source : pricingSource) {
            if (source.getName() != null) byName.put(source.getName(), source);
        }
        for (ProductVariant variant : target) {
            ProductVariant priced = byName.get(variant.getName());
            if (priced == null) continue;
            if (priced.getPrice() > 0) variant.setPrice(priced.getPrice());
            variant.setStock(priced.getStock());
        }
    }

    private static List<ProductVariant> parseVariantsField(Object raw, Product parent) {
        List<ProductVariant> result = new ArrayList<>();
        if (raw == null) return result;
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                ProductVariant variant = toVariantWithMapKey(entry.getKey(), entry.getValue(), parent, result.size());
                if (variant != null && !containsVariantName(result, variant.getName())) {
                    result.add(variant);
                }
            }
            return result;
        }
        for (Object item : toObjectList(raw)) {
            ProductVariant variant = toVariant(item, parent, result.size());
            if (variant != null && !containsVariantName(result, variant.getName())) {
                result.add(variant);
            }
        }
        return result;
    }

    private static ProductVariant toVariantWithMapKey(Object key, Object value, Product parent, int index) {
        ProductVariant variant = toVariant(value, parent, index);
        if (variant != null) {
            if ((variant.getName() == null || variant.getName().isEmpty()) && key != null) {
                variant.setName(extractOptionLabel(key));
            }
            return variant;
        }
        if (key == null) return null;
        String name = extractOptionLabel(key);
        if (name.isEmpty()) return null;

        ProductVariant keyed = new ProductVariant();
        keyed.setId("variant_" + index);
        keyed.setName(name);

        // ✅ FIX QUAN TRỌNG: Nếu Key là chuỗi object "{price=..., label=...}"
        // thì phải ưu tiên lấy giá tiền bên trong chuỗi Key này.
        String keyStr = String.valueOf(key);
        if (keyStr.startsWith("{") && keyStr.endsWith("}")) {
            String pStr = extractVal(keyStr, "price");
            if (pStr != null) {
                try {
                    double p = Double.parseDouble(pStr);
                    if (p > 0) {
                        keyed.setPrice(p);
                        keyed.setOriginalPrice(p);
                        keyed.setStock(parent.getStockCount());
                        return keyed;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Nếu không có giá trong Key, mới dùng giá trị Value của Map
        if (value instanceof Number) {
            keyed.setPrice(((Number) value).doubleValue());
            keyed.setOriginalPrice(keyed.getPrice());
            keyed.setStock(parent.getStockCount());
            return keyed;
        }
        if (value instanceof String) {
            try {
                keyed.setPrice(Double.parseDouble(((String) value).trim()));
                keyed.setOriginalPrice(keyed.getPrice());
                keyed.setStock(parent.getStockCount());
                return keyed;
            } catch (NumberFormatException ignored) {
                keyed.setPrice(parent.getPrice());
                keyed.setOriginalPrice(parent.getOriginalPrice());
                keyed.setStock(parent.getStockCount());
                return keyed;
            }
        }
        return null;
    }

    private static List<Object> toObjectList(Object raw) {
        List<Object> result = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null) result.add(item);
            }
        } else if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            // Nếu Map này là dạng legacy (Key là nhãn, Value là giá số), ta cần lấy Key làm nhãn
            boolean isSimplePriceMap = true;
            for (Object val : map.values()) {
                if (val != null && !(val instanceof Number)) {
                    isSimplePriceMap = false;
                    break;
                }
            }

            if (isSimplePriceMap && !map.isEmpty()) {
                for (Object key : map.keySet()) {
                    if (key != null) result.add(key);
                }
            } else {
                for (Object item : map.values()) {
                    if (item != null) result.add(item);
                }
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
            v.setPrice(firstDouble(map, parent.getPrice(), "price", "salePrice", "variantPrice", "amount"));
            v.setOriginalPrice(firstDouble(map, v.getPrice(), "originalPrice", "oldPrice", "marketPrice"));
            v.setStock(firstInt(map, "stock", "stockCount", parent.getStockCount()));
            v.setSku(firstString(map, "sku", "SKU"));
            v.setImageUrl(firstString(map, "image", "imageUrl"));
            Object outOfStock = map.get("outOfStock");
            if (outOfStock instanceof Boolean && (Boolean) outOfStock) {
                v.setStock(0);
            }
            return v;
        }
        if (item instanceof String) {
            String raw = ((String) item).trim();
            if (raw.isEmpty()) return null;

            // Kiểm tra nếu chuỗi bị lỗi định dạng Map "{...}"
            if (raw.startsWith("{") && raw.endsWith("}")) {
                String label = extractVal(raw, "label");
                if (label == null) label = extractVal(raw, "name");
                
                if (label != null) {
                    ProductVariant v = new ProductVariant();
                    v.setId("variant_" + index);
                    v.setName(label);
                    // Cố gắng lấy giá từ chuỗi, nếu không có dùng giá của sản phẩm cha
                    String priceStr = extractVal(raw, "price");
                    try {
                        v.setPrice(priceStr != null ? Double.parseDouble(priceStr) : parent.getPrice());
                    } catch (Exception e) {
                        v.setPrice(parent.getPrice());
                    }
                    v.setStock(parent.getStockCount());
                    return v;
                }
            }

            // Trường hợp chuỗi văn bản bình thường
            ProductVariant v = new ProductVariant();
            v.setId("variant_" + index);
            v.setName(raw);
            v.setPrice(parent.getPrice());
            v.setStock(parent.getStockCount());
            return v;
        }
        return null;
    }

    private static String extractVal(String s, String key) {
        String target = key + "=";
        if (!s.contains(target)) return null;
        int start = s.indexOf(target) + target.length();
        int end = s.indexOf(",", start);
        if (end == -1) end = s.indexOf("}", start);
        if (end != -1) {
            String val = s.substring(start, end).trim();
            return "null".equalsIgnoreCase(val) ? null : val;
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
                if (v.getOriginalPrice() <= 0) v.setOriginalPrice(getOriginalPrice());
                if (v.getStock() <= 0) v.setStock(getStockCount());
                result.add(v);
            }
        }
        return result;
    }

    private List<ProductVariant> buildVariantsFromLegacyOptions() {
        List<ProductVariant> result = new ArrayList<>();
        result.addAll(parseVariantsField(weights, this));
        result.addAll(parseVariantsField(flavors, this));
        result.addAll(parseVariantsField(packagingTypes, this));

        for (int i = 0; i < result.size(); i++) {
            ProductVariant variant = result.get(i);
            if (variant.getId() == null || variant.getId().isEmpty()) {
                variant.setId("legacy_" + i);
            }
        }
        return result;
    }

    private static boolean containsVariantName(List<ProductVariant> variants, String name) {
        if (name == null || name.isEmpty()) return true;
        for (ProductVariant variant : variants) {
            if (name.equals(variant.getName())) return true;
        }
        return false;
    }

    /** Extracts a display label from a Firestore option entry (String or Map). */
    @NonNull
    public static String extractOptionLabel(Object item) {
        if (item instanceof Map) {
            String label = firstString((Map<?, ?>) item, "label", "name", "title", "option");
            return label != null ? label : "";
        }
        if (item == null) return "";
        String label = String.valueOf(item).trim();

        // Fix: Nếu chuỗi là một Map đã bị stringify, trích xuất lấy label
        if (label.startsWith("{") && label.endsWith("}")) {
            String extracted = extractVal(label, "label");
            if (extracted == null) extracted = extractVal(label, "name");
            if (extracted != null) return extracted;
        }

        return "null".equalsIgnoreCase(label) ? "" : label;
    }

    private static void appendOptionLabels(List<String> target, List<Object> source, String prefix) {
        if (source == null) return;
        for (Object item : source) {
            String label = extractOptionLabel(item);
            if (label.isEmpty()) continue;
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

    private static double firstDouble(Map<?, ?> map, double fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value instanceof String) {
                try { return Double.parseDouble(((String) value).trim()); } catch (NumberFormatException ignored) {}
            }
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
        private double originalPrice;
        private int stock;
        private String sku;
        private String imageUrl;
        public ProductVariant() {}
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public double getOriginalPrice() { return originalPrice; }
        public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
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
