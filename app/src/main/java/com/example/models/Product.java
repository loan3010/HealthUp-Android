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
import java.util.Locale;
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
    // Must be transient: Firebase Timestamp is not java.io.Serializable.
    // Nesting Product inside CartItem Intent extras otherwise crashes Buy Now
    // (BadParcelableException / NotSerializableException: Timestamp).
    private transient Timestamp createdAt;
    private transient Timestamp updatedAt;

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
    /** Up to 3 custom-named option groups, e.g. Khối lượng / Loại đóng gói / Thể tích. */
    private List<VariantDimension> variantDimensions;
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

    /**
     * Real sold recorded in Firestore (no mock). 0 means never seeded / no purchases tracked.
     */
    public int getRealSoldCount() {
        if (variants != null && !variants.isEmpty()) {
            int sum = 0;
            for (ProductVariant v : variants) {
                if (v != null) sum += Math.max(0, v.getSold());
            }
            if (sum > 0) return sum;
        }
        if (sold > 0) return sold;
        return Math.max(0, soldCount);
    }

    /**
     * UI sold count: real data if present, otherwise a stable mock baseline
     * (looks random per product, always ≥ reviewCount so 3 reviews + 0 sold never appears).
     */
    public int getSoldCount() {
        int real = getRealSoldCount();
        if (real > 0) return real;
        return computeMockSold(id, getReviewCount());
    }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    /**
     * Deterministic “random” baseline from product id — stable across opens, no server script.
     * Range ~30–220, and at least reviewCount * 8 + small jitter when there are reviews.
     */
    public static int computeMockSold(@Nullable String productId, int reviewCount) {
        String key = productId != null ? productId : "product";
        int hash = Math.abs(key.hashCode());
        int base = 30 + (hash % 191); // 30..220
        if (reviewCount > 0) {
            int minForReviews = reviewCount * 8 + (hash % 17); // e.g. 3 reviews → ≥24–40
            base = Math.max(base, minForReviews);
        }
        return Math.max(1, base);
    }

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
    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
        this.resolvedVariantsCache = null;
    }

    public List<VariantDimension> getVariantDimensions() { return variantDimensions; }
    public void setVariantDimensions(List<VariantDimension> variantDimensions) {
        this.variantDimensions = variantDimensions;
        this.resolvedVariantsCache = null;
    }

    /** Alias of aggregated sold (variants sum, else product-level). */
    public int getTotalSold() {
        return getSoldCount();
    }

    /**
     * Sellable stock for UX / purchase checks.
     * When SKU {@code variants} exist: sum of stocks of {@code enabled} variants only
     * (ignores stale product-level {@code stock} and disabled combos).
     * When no variants: product-level stock.
     */
    public int getTotalVariantStock() {
        if (variants != null && !variants.isEmpty()) {
            int sum = 0;
            for (ProductVariant v : variants) {
                if (v != null && v.isEnabled()) sum += Math.max(0, v.getStock());
            }
            return sum;
        }
        return Math.max(0, getStockCount());
    }

    /** Alias of {@link #getTotalVariantStock()} for card/detail stock display. */
    public int getAvailableStock() {
        return getTotalVariantStock();
    }

    /** True when at least one sellable unit exists (enabled in-stock SKU, or product stock). */
    public boolean isInStock() {
        return getAvailableStock() > 0;
    }

    /** Variants for UI: prefers `variants`, merges pricing from `weights`, falls back to legacy options. */
    public List<ProductVariant> getResolvableVariants() {
        if (resolvedVariantsCache != null) return resolvedVariantsCache;
        List<ProductVariant> resolved;
        if (variants != null && !variants.isEmpty()) {
            // Copy SKUs so legacy price/stock fill-in never mutates canonical `variants`
            // (getAvailableStock / admin stock must keep Firestore truth).
            resolved = copyVariants(variants);
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

    /** Returns a map of category names to their respective option chips for the buyer picker. */
    public Map<String, List<ProductVariant>> getGroupedVariants() {
        Map<String, List<ProductVariant>> groups = new LinkedHashMap<>();
        List<VariantDimension> dims = resolveVariantDimensions();

        // Prefer dimensions only when they actually cover existing SKUs.
        // Old catalog often had dims=[Túi zip] while SKUs=[500g,1kg,Túi zip] → client looked "broken".
        if (!dims.isEmpty() && dimensionsAlignWithVariants(dims, variants)) {
            for (VariantDimension dim : dims) {
                if (dim == null || dim.getName() == null) continue;
                List<ProductVariant> options = new ArrayList<>();
                List<String> labels = dim.getOptions() != null ? dim.getOptions() : new ArrayList<>();
                for (int i = 0; i < labels.size(); i++) {
                    String label = labels.get(i);
                    if (label == null || label.trim().isEmpty()) continue;
                    String trimmed = label.trim();
                    ProductVariant option = new ProductVariant();
                    option.setId((dim.getId() != null ? dim.getId() : "dim") + "_opt_" + i);
                    option.setName(trimmed);
                    ProductVariant sku = findSkuByNameDirect(trimmed);
                    if (sku != null) {
                        option.setPrice(sku.getPrice() > 0 ? sku.getPrice() : getPrice());
                        option.setOriginalPrice(sku.getOriginalPrice() > 0
                                ? sku.getOriginalPrice() : getOriginalPrice());
                        option.setStock(Math.max(0, sku.getStock()));
                        option.setEnabled(sku.isEnabled());
                    } else {
                        option.setPrice(getPrice());
                        option.setOriginalPrice(getOriginalPrice());
                        option.setStock(0);
                        option.setEnabled(true);
                    }
                    options.add(option);
                }
                if (!options.isEmpty()) {
                    groups.put(dim.getName(), options);
                }
            }
            if (!groups.isEmpty()) {
                return groups;
            }
        }

        // Legacy field fallback
        List<ProductVariant> w = normalizeVariants(parseVariantsField(weights, this));
        if (!w.isEmpty()) groups.put("Khối lượng", w);

        List<ProductVariant> f = normalizeVariants(parseVariantsField(flavors, this));
        if (!f.isEmpty()) groups.put("Hương vị", f);

        List<ProductVariant> p = normalizeVariants(parseVariantsField(packagingTypes, this));
        if (!p.isEmpty()) groups.put("Loại đóng gói", p);

        if (groups.isEmpty() && variants != null && !variants.isEmpty()) {
            inferGroupsFromComboVariants(groups);
        }

        // Always expose flat SKUs as one "Phân loại" group so buyer never sees empty sheet.
        if (groups.isEmpty() && variants != null && !variants.isEmpty()) {
            groups.put("Phân loại", normalizeVariants(new ArrayList<>(variants)));
        }

        return groups;
    }

    /**
     * True when every SKU can be explained by the dimension options
     * (exact label, or combo parts joined by " · ").
     */
    private static boolean dimensionsAlignWithVariants(
            @Nullable List<VariantDimension> dims,
            @Nullable List<ProductVariant> skus) {
        if (dims == null || dims.isEmpty()) return false;
        if (skus == null || skus.isEmpty()) return true;

        List<String> allOptions = new ArrayList<>();
        for (VariantDimension dim : dims) {
            if (dim == null || dim.getOptions() == null) continue;
            for (String opt : dim.getOptions()) {
                if (opt != null && !opt.trim().isEmpty()) {
                    allOptions.add(opt.trim());
                }
            }
        }
        if (allOptions.isEmpty()) return false;

        for (ProductVariant sku : skus) {
            if (sku == null || sku.getName() == null || sku.getName().trim().isEmpty()) continue;
            String name = sku.getName().trim();
            if (name.contains(" · ")) {
                String[] parts = name.split(" · ");
                if (parts.length > dims.size()) return false;
                for (String part : parts) {
                    if (!containsIgnoreCase(allOptions, part.trim())) {
                        return false;
                    }
                }
            } else if (!containsIgnoreCase(allOptions, name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(@NonNull List<String> options, @NonNull String value) {
        for (String opt : options) {
            if (opt.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    @Nullable
    private ProductVariant findSkuByNameDirect(@NonNull String name) {
        if (variants == null) return null;
        for (ProductVariant variant : variants) {
            if (variant != null && variant.getName() != null
                    && variant.getName().trim().equalsIgnoreCase(name)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * Preferred dimensions: explicit {@code variantDimensions}, else migrate legacy fields.
     */
    @NonNull
    public List<VariantDimension> resolveVariantDimensions() {
        if (variantDimensions != null && !variantDimensions.isEmpty()) {
            List<VariantDimension> cleaned = new ArrayList<>();
            for (VariantDimension dim : variantDimensions) {
                if (dim == null || dim.getName() == null || dim.getName().trim().isEmpty()) continue;
                if (dim.getOptions() == null || dim.getOptions().isEmpty()) continue;
                cleaned.add(dim);
            }
            if (!cleaned.isEmpty()) {
                return cleaned.size() > 3 ? cleaned.subList(0, 3) : cleaned;
            }
        }
        return migrateLegacyDimensionsLocal();
    }

    @NonNull
    private List<VariantDimension> migrateLegacyDimensionsLocal() {
        List<VariantDimension> dims = new ArrayList<>();
        List<ProductVariant> flavorOpts = parseVariantsField(flavors, this);
        List<ProductVariant> weightOpts = parseVariantsField(weights, this);
        List<ProductVariant> pkgOpts = parseVariantsField(packagingTypes, this);
        if (!flavorOpts.isEmpty()) {
            dims.add(dimensionFromOptions("dim_flavor", "Hương vị", flavorOpts));
        }
        if (!pkgOpts.isEmpty()) {
            dims.add(dimensionFromOptions("dim_packaging", "Loại đóng gói", pkgOpts));
        }
        if (!weightOpts.isEmpty()) {
            dims.add(dimensionFromOptions("dim_weight", "Khối lượng", weightOpts));
        }
        return dims.size() > 3 ? new ArrayList<>(dims.subList(0, 3)) : dims;
    }

    @NonNull
    private static VariantDimension dimensionFromOptions(String id, String name,
                                                         List<ProductVariant> options) {
        VariantDimension dim = new VariantDimension();
        dim.setId(id);
        dim.setName(name);
        List<String> labels = new ArrayList<>();
        for (ProductVariant v : options) {
            if (v != null && v.getName() != null && !v.getName().isEmpty()) {
                labels.add(v.getName());
            }
        }
        dim.setOptions(labels);
        return dim;
    }

    /**
     * Finds an enabled, in-stock SKU matching the selected options per dimension name.
     */
    @Nullable
    public ProductVariant findEnabledVariant(@NonNull Map<String, String> selectionsByDimName) {
        if (selectionsByDimName == null || selectionsByDimName.isEmpty()) {
            return null;
        }
        for (ProductVariant variant : getResolvableVariants()) {
            if (variant == null || !variant.isEnabled() || variant.getStock() <= 0) continue;
            if (variantMatchesSelections(variant, selectionsByDimName)) {
                return variant;
            }
        }
        // Allow out-of-stock match for display (caller decides).
        for (ProductVariant variant : getResolvableVariants()) {
            if (variant == null || !variant.isEnabled()) continue;
            if (variantMatchesSelections(variant, selectionsByDimName)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * True if any enabled sellable SKU exists for the given partial selections
     * (other dims may still be free). Used to grey-out impossible chip paths.
     */
    public boolean isOptionAvailable(@NonNull String dimName,
                                     @NonNull String optionLabel,
                                     @NonNull Map<String, String> currentSelections) {
        Map<String, String> trial = new LinkedHashMap<>(currentSelections);
        trial.put(dimName, optionLabel);
        for (ProductVariant variant : getResolvableVariants()) {
            if (variant == null || !variant.isEnabled()) continue;
            if (variantPartialMatch(variant, trial)) {
                return true;
            }
        }
        return false;
    }

        private boolean variantMatchesSelections(@NonNull ProductVariant variant,
                                             @NonNull Map<String, String> selections) {
        Map<String, String> map = variant.getSelections();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, String> entry : selections.entrySet()) {
                String got = map.get(entry.getKey());
                if (got == null || !got.equalsIgnoreCase(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        // Fallback: combo name contains every selected option label
        String name = variant.getName();
        if (name == null) return false;
        for (String value : selections.values()) {
            if (value == null || !name.toLowerCase().contains(value.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private boolean variantPartialMatch(@NonNull ProductVariant variant,
                                        @NonNull Map<String, String> partial) {
        Map<String, String> map = variant.getSelections();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, String> entry : partial.entrySet()) {
                String got = map.get(entry.getKey());
                if (got == null || !got.equalsIgnoreCase(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        String name = variant.getName();
        if (name == null) return false;
        for (String value : partial.values()) {
            if (value == null || !name.toLowerCase().contains(value.toLowerCase())) {
                return false;
            }
        }
        return true;
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
        Map<String, String> selections = new LinkedHashMap<>();
        for (Map.Entry<String, ProductVariant> entry : selectedByGroup.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getName() != null) {
                selections.put(entry.getKey(), entry.getValue().getName());
            }
        }
        ProductVariant matched = findEnabledVariant(selections);
        if (matched != null) {
            return matched;
        }
        // Fall through to legacy name matching for older catalogs
        if (selectedByGroup.size() == 1) {
            ProductVariant only = selectedByGroup.values().iterator().next();
            if (only == null) return null;
            // Only return a real SKU from variants — never the chip stub (stock always 0).
            return findVariantByName(only.getName());
        }

        List<String> parts = new ArrayList<>();
        for (ProductVariant variant : selectedByGroup.values()) {
            if (variant != null && variant.getName() != null && !variant.getName().isEmpty()) {
                parts.add(variant.getName().trim());
            }
        }
        if (parts.isEmpty()) {
            return null;
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

    /**
     * Unit selling price for a resolved SKU.
     * Uses the SKU's own {@code price} when &gt; 0; otherwise falls back to product base price
     * (no variants, or SKU price unset/0).
     */
    public double resolveUnitPrice(@Nullable ProductVariant sku) {
        if (sku != null && sku.getPrice() > 0) return sku.getPrice();
        return getPrice();
    }

    /**
     * Original/compare-at price for a resolved SKU; falls back like {@link #resolveUnitPrice}.
     */
    public double resolveOriginalUnitPrice(@Nullable ProductVariant sku) {
        if (sku != null) {
            if (sku.getOriginalPrice() > 0) return sku.getOriginalPrice();
            if (sku.getPrice() > 0) return sku.getPrice();
        }
        return getOriginalPrice() > 0 ? getOriginalPrice() : getPrice();
    }

    /**
     * True when this product has at least one enabled SKU in the canonical {@code variants} list.
     * (Independent of stock — disabled-only catalogs fall through to product-level price.)
     */
    public boolean hasEnabledVariants() {
        if (variants == null || variants.isEmpty()) return false;
        for (ProductVariant v : variants) {
            if (v != null && v.isEnabled()) return true;
        }
        return false;
    }

    /**
     * SKU used for card / detail "outside" price before the buyer picks a variant.
     * Prefers the cheapest enabled in-stock SKU; if all enabled are OOS, the cheapest
     * enabled SKU (for consistent OOS display). Null when there are no enabled variants.
     */
    @Nullable
    public ProductVariant getDisplayPriceVariant() {
        if (variants == null || variants.isEmpty()) return null;
        ProductVariant bestInStock = null;
        double bestInStockPrice = Double.POSITIVE_INFINITY;
        ProductVariant bestEnabled = null;
        double bestEnabledPrice = Double.POSITIVE_INFINITY;
        for (ProductVariant v : variants) {
            if (v == null || !v.isEnabled()) continue;
            double p = resolveUnitPrice(v);
            if (p < bestEnabledPrice) {
                bestEnabledPrice = p;
                bestEnabled = v;
            }
            if (v.getStock() > 0 && p < bestInStockPrice) {
                bestInStockPrice = p;
                bestInStock = v;
            }
        }
        return bestInStock != null ? bestInStock : bestEnabled;
    }

    /**
     * List / detail display price (before opening the variant sheet).
     * With enabled SKUs: min price among enabled in-stock variants; if none in stock,
     * min among all enabled. Without variants: product-level {@link #getPrice()}.
     * Does not replace cart pricing — carts use {@link #resolveUnitPrice} for the selected SKU.
     */
    public double getDisplayPrice() {
        ProductVariant v = getDisplayPriceVariant();
        if (v != null) return resolveUnitPrice(v);
        return getPrice();
    }

    /**
     * Compare-at / original price paired with {@link #getDisplayPrice()}.
     * With enabled SKUs: original of the same SKU chosen for display; else product-level.
     */
    public double getDisplayOriginalPrice() {
        ProductVariant v = getDisplayPriceVariant();
        if (v != null) return resolveOriginalUnitPrice(v);
        return getOriginalPrice() > 0 ? getOriginalPrice() : getPrice();
    }

    /** Price for a weight/option label; falls back to base product price. */
    public double getPriceForOption(String optionLabel) {
        return resolveUnitPrice(findVariantByName(optionLabel));
    }

    public static Product fromDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Product p = doc.toObject(Product.class);
        if (p == null) return null;
        p.setId(doc.getId());
        p.resolvedVariantsCache = null;
        p.images = normalizeImageList(doc.get("images"), p.imageUrl, p.image);

        // True sellable SKUs live only in `variants`.
        List<ProductVariant> skuVariants = parseVariantsField(doc.get("variants"), p);
        enrichVariantsFromMaps(doc.get("variants"), skuVariants);
        p.variants = skuVariants;

        p.variantDimensions = parseVariantDimensions(doc.get("variantDimensions"));
        if (p.variantDimensions == null || p.variantDimensions.isEmpty()) {
            p.variantDimensions = p.migrateLegacyDimensionsLocal();
        }
        if (p.variantDimensions != null) {
            for (ProductVariant v : skuVariants) {
                if (v == null) continue;
                // inline attach (same logic as AdminVariantComboHelper)
                if (v.getSelections() == null || v.getSelections().isEmpty()) {
                    attachSelectionsLocal(v, p.variantDimensions);
                }
            }
        }

        p.hasVariants = !skuVariants.isEmpty() || p.hasVariants;
        if (doc.contains("hasVariants")) {
            Boolean hv = doc.getBoolean("hasVariants");
            if (hv != null) {
                p.hasVariants = hv;
            }
        } else {
            p.hasVariants = !skuVariants.isEmpty()
                    || (p.variantDimensions != null && !p.variantDimensions.isEmpty());
        }

        // Prefer aggregated variant sold when product-level sold missing.
        if (p.getRealSoldCount() <= 0 && !skuVariants.isEmpty()) {
            int sumSold = 0;
            for (ProductVariant v : skuVariants) {
                if (v != null) sumSold += Math.max(0, v.getSold());
            }
            if (sumSold > 0) {
                p.setSold(sumSold);
                p.setSoldCount(sumSold);
            }
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

    @SuppressWarnings("unchecked")
    private static void enrichVariantsFromMaps(@Nullable Object raw,
                                               @NonNull List<ProductVariant> variants) {
        if (!(raw instanceof List) || variants.isEmpty()) return;
        List<?> list = (List<?>) raw;
        for (int i = 0; i < list.size() && i < variants.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            ProductVariant v = variants.get(i);
            Object soldVal = map.get("sold");
            if (soldVal instanceof Number) {
                v.setSold(((Number) soldVal).intValue());
            }
            Object enabledVal = map.get("enabled");
            if (enabledVal instanceof Boolean) {
                v.setEnabled((Boolean) enabledVal);
            } else {
                v.setEnabled(true);
            }
            Object selections = map.get("selections");
            if (selections instanceof Map) {
                Map<String, String> sel = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) selections).entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        sel.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
                if (!sel.isEmpty()) {
                    v.setSelections(sel);
                }
            }
        }
    }

    @Nullable
    private static List<VariantDimension> parseVariantDimensions(@Nullable Object raw) {
        if (!(raw instanceof List)) return null;
        List<VariantDimension> result = new ArrayList<>();
        int i = 0;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            String name = map.get("name") != null ? String.valueOf(map.get("name")).trim() : "";
            if (name.isEmpty()) continue;
            List<String> options = new ArrayList<>();
            Object optsRaw = map.get("options");
            if (optsRaw instanceof List) {
                for (Object o : (List<?>) optsRaw) {
                    if (o == null) continue;
                    String label = String.valueOf(o).trim();
                    if (!label.isEmpty() && !options.contains(label)) options.add(label);
                }
            }
            if (options.isEmpty()) continue;
            VariantDimension dim = new VariantDimension();
            dim.setId(map.get("id") != null ? String.valueOf(map.get("id")) : ("dim_" + i));
            dim.setName(name);
            dim.setOptions(options);
            result.add(dim);
            i++;
            if (result.size() >= 3) break;
        }
        return result.isEmpty() ? null : result;
    }

    private static void attachSelectionsLocal(@NonNull ProductVariant variant,
                                              @NonNull List<VariantDimension> dimensions) {
        String name = variant.getName();
        if (name == null || name.isEmpty()) return;
        Map<String, String> selections = new LinkedHashMap<>();
        if (name.contains(" · ")) {
            String[] parts = name.split(" · ");
            for (int i = 0; i < parts.length && i < dimensions.size(); i++) {
                selections.put(dimensions.get(i).getName(), parts[i].trim());
            }
        } else {
            for (VariantDimension dim : dimensions) {
                if (dim.getOptions() == null) continue;
                for (String opt : dim.getOptions()) {
                    if (name.equalsIgnoreCase(opt)) {
                        selections.put(dim.getName(), opt);
                        break;
                    }
                }
            }
        }
        if (!selections.isEmpty()) {
            variant.setSelections(selections);
        }
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
                    // Only fill missing fields — never overwrite a real SKU price with
                    // legacy chip defaults (legacy strings often inherit product base price).
                    if (t.getPrice() <= 0 && s.getPrice() > 0) t.setPrice(s.getPrice());
                    if (t.getOriginalPrice() <= 0 && s.getOriginalPrice() > 0) {
                        t.setOriginalPrice(s.getOriginalPrice());
                    }
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

    /**
     * Fills missing per-variant price/stock from legacy weights/flavors/packaging.
     * Never overwrite an existing SKU price/stock: legacy option maps often store
     * label-only strings that inherit product.price, which would wipe real SKU prices.
     */
    private static void mergeVariantPricing(List<ProductVariant> target, List<ProductVariant> pricingSource) {
        Map<String, ProductVariant> byName = new HashMap<>();
        for (ProductVariant source : pricingSource) {
            if (source.getName() != null) {
                byName.put(source.getName().trim().toLowerCase(Locale.ROOT), source);
            }
        }
        for (ProductVariant variant : target) {
            if (variant.getName() == null) continue;
            ProductVariant priced = byName.get(variant.getName().trim().toLowerCase(Locale.ROOT));
            if (priced == null) continue;
            if (variant.getPrice() <= 0 && priced.getPrice() > 0) {
                variant.setPrice(priced.getPrice());
            }
            if (variant.getOriginalPrice() <= 0 && priced.getOriginalPrice() > 0) {
                variant.setOriginalPrice(priced.getOriginalPrice());
            }
            // Same rule as mergeVariantLists: only adopt positive stock from legacy.
            if (priced.getStock() > 0) variant.setStock(priced.getStock());
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
                        // Do not inherit product total stock onto a variant option.
                        keyed.setStock(0);
                        return keyed;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Nếu không có giá trong Key, mới dùng giá trị Value của Map
        if (value instanceof Number) {
            keyed.setPrice(((Number) value).doubleValue());
            keyed.setOriginalPrice(keyed.getPrice());
            keyed.setStock(0);
            return keyed;
        }
        if (value instanceof String) {
            try {
                keyed.setPrice(Double.parseDouble(((String) value).trim()));
                keyed.setOriginalPrice(keyed.getPrice());
                keyed.setStock(0);
                return keyed;
            } catch (NumberFormatException ignored) {
                keyed.setPrice(parent.getPrice());
                keyed.setOriginalPrice(parent.getOriginalPrice());
                keyed.setStock(0);
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
            // Missing stock on a variant = 0, never product total stock.
            v.setStock(firstInt(map, "stock", "stockCount", 0));
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
                    String stockStr = extractVal(raw, "stock");
                    try {
                        v.setStock(stockStr != null ? Integer.parseInt(stockStr) : 0);
                    } catch (Exception e) {
                        v.setStock(0);
                    }
                    return v;
                }
            }

            // Label-only option (e.g. weight/flavor chip) — no own stock.
            ProductVariant v = new ProductVariant();
            v.setId("variant_" + index);
            v.setName(raw);
            v.setPrice(parent.getPrice());
            v.setStock(0);
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
                // Keep stock as-is (including 0). Never copy product total onto a SKU.
                result.add(v);
            }
        }
        return result;
    }

    @NonNull
    private static List<ProductVariant> copyVariants(@NonNull List<ProductVariant> source) {
        List<ProductVariant> copies = new ArrayList<>(source.size());
        for (ProductVariant v : source) {
            if (v == null) continue;
            ProductVariant c = new ProductVariant();
            c.setId(v.getId());
            c.setName(v.getName());
            c.setPrice(v.getPrice());
            c.setOriginalPrice(v.getOriginalPrice());
            c.setStock(v.getStock());
            c.setSold(v.getSold());
            c.setEnabled(v.isEnabled());
            c.setSku(v.getSku());
            c.setImageUrl(v.getImageUrl());
            if (v.getSelections() != null && !v.getSelections().isEmpty()) {
                c.setSelections(new LinkedHashMap<>(v.getSelections()));
            }
            copies.add(c);
        }
        return copies;
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
        private int sold;
        private boolean enabled = true;
        /** Dimension name → option label, e.g. {"Khối lượng":"2kg","Loại đóng gói":"Zip"}. */
        private Map<String, String> selections;
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
        public int getSold() { return sold; }
        public void setSold(int sold) { this.sold = sold; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Map<String, String> getSelections() { return selections; }
        public void setSelections(Map<String, String> selections) { this.selections = selections; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    /** One buyer-facing option group (admin-named), max 3 per product. */
    public static class VariantDimension implements Serializable {
        private String id;
        private String name;
        private List<String> options;
        public VariantDimension() {}
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
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
