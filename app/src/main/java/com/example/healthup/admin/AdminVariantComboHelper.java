package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Product;
import com.example.models.Product.ProductVariant;
import com.example.models.Product.VariantDimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flexible variant dimensions (1–3 custom-named groups) → cartesian SKU combos.
 * Unavailable real-world combos are kept in the list but can be {@code enabled=false}
 * (or stock=0) so the buyer UI can disable invalid chip paths.
 */
public final class AdminVariantComboHelper {

    public static final String COMBO_SEPARATOR = " · ";
    public static final int MAX_DIMENSIONS = 3;

    private AdminVariantComboHelper() {
    }

    @NonNull
    public static List<String> parseOptionList(@NonNull String raw) {
        List<String> result = new ArrayList<>();
        if (raw.trim().isEmpty()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @NonNull
    public static List<String> extractOptionNames(@Nullable Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                String name = optionName(item);
                if (name != null && !name.isEmpty() && !result.contains(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    @Nullable
    private static String optionName(@Nullable Object item) {
        if (item == null) return null;
        if (item instanceof String) {
            String s = ((String) item).trim();
            return s.isEmpty() ? null : s;
        }
        if (item instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) item;
            Object name = map.get("name");
            if (name == null) name = map.get("label");
            if (name == null) name = map.get("title");
            return name != null ? String.valueOf(name).trim() : null;
        }
        return String.valueOf(item).trim();
    }

    /**
     * Builds cartesian product of dimension options.
     * Example: [Khối lượng:1kg,2kg] × [Đóng gói:Zip,Hút chân không] → 4 SKUs.
     * Admin then disables combos that do not exist (e.g. 2kg · Hút chân không).
     */
    @NonNull
    public static List<ProductVariant> generateCombinations(
            @NonNull List<VariantDimension> dimensions,
            double basePrice,
            double baseOriginalPrice) {
        List<VariantDimension> usable = new ArrayList<>();
        for (VariantDimension dim : dimensions) {
            if (dim == null) continue;
            String name = dim.getName() != null ? dim.getName().trim() : "";
            List<String> options = dim.getOptions() != null ? dim.getOptions() : new ArrayList<>();
            List<String> cleaned = new ArrayList<>();
            for (String opt : options) {
                if (opt != null && !opt.trim().isEmpty() && !cleaned.contains(opt.trim())) {
                    cleaned.add(opt.trim());
                }
            }
            if (!name.isEmpty() && !cleaned.isEmpty()) {
                VariantDimension copy = new VariantDimension();
                copy.setId(dim.getId() != null ? dim.getId() : ("dim_" + usable.size()));
                copy.setName(name);
                copy.setOptions(cleaned);
                usable.add(copy);
            }
        }
        if (usable.isEmpty() || usable.size() > MAX_DIMENSIONS) {
            return new ArrayList<>();
        }

        List<Map<String, String>> combos = new ArrayList<>();
        combos.add(new LinkedHashMap<>());
        for (VariantDimension dim : usable) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> partial : combos) {
                for (String option : dim.getOptions()) {
                    Map<String, String> copy = new LinkedHashMap<>(partial);
                    copy.put(dim.getName(), option);
                    next.add(copy);
                }
            }
            combos = next;
        }

        List<ProductVariant> result = new ArrayList<>();
        int index = 0;
        for (Map<String, String> selections : combos) {
            ProductVariant variant = new ProductVariant();
            variant.setId("variant_" + index);
            variant.setName(joinSelections(selections));
            variant.setSelections(new LinkedHashMap<>(selections));
            variant.setPrice(basePrice);
            variant.setOriginalPrice(baseOriginalPrice > 0 ? baseOriginalPrice : basePrice);
            variant.setStock(0);
            variant.setSold(0);
            variant.setEnabled(true);
            result.add(variant);
            index++;
        }
        return result;
    }

    @NonNull
    public static String joinSelections(@NonNull Map<String, String> selections) {
        List<String> parts = new ArrayList<>();
        for (String value : selections.values()) {
            if (value != null && !value.trim().isEmpty()) {
                parts.add(value.trim());
            }
        }
        return String.join(COMBO_SEPARATOR, parts);
    }

    @NonNull
    public static List<ProductVariant> mergeWithExisting(@NonNull List<ProductVariant> generated,
                                                          @NonNull List<ProductVariant> existing) {
        List<ProductVariant> merged = new ArrayList<>();
        for (ProductVariant variant : generated) {
            ProductVariant kept = findCompatible(existing, variant);
            if (kept != null) {
                variant.setPrice(kept.getPrice() > 0 ? kept.getPrice() : variant.getPrice());
                variant.setOriginalPrice(kept.getOriginalPrice() > 0
                        ? kept.getOriginalPrice() : variant.getOriginalPrice());
                variant.setStock(kept.getStock());
                variant.setSold(kept.getSold());
                variant.setSku(kept.getSku());
                variant.setImageUrl(kept.getImageUrl());
                variant.setId(kept.getId());
                variant.setEnabled(kept.isEnabled());
            }
            merged.add(variant);
        }
        return merged;
    }

    @Nullable
    private static ProductVariant findCompatible(@NonNull List<ProductVariant> existing,
                                                 @NonNull ProductVariant generated) {
        ProductVariant byName = findByName(existing, generated.getName());
        if (byName != null) return byName;
        if (generated.getSelections() == null || generated.getSelections().isEmpty()) {
            return null;
        }
        for (ProductVariant candidate : existing) {
            if (candidate.getSelections() != null
                    && !candidate.getSelections().isEmpty()
                    && candidate.getSelections().equals(generated.getSelections())) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static ProductVariant findByName(@NonNull List<ProductVariant> existing, String name) {
        if (name == null) return null;
        for (ProductVariant variant : existing) {
            if (name.equals(variant.getName())) {
                return variant;
            }
        }
        return null;
    }

    @NonNull
    public static List<Map<String, Object>> toOptionFirestoreList(@NonNull List<String> labels) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            Map<String, Object> option = new HashMap<>();
            option.put("id", "opt_" + i);
            option.put("name", labels.get(i));
            result.add(option);
        }
        return result;
    }

    @NonNull
    public static List<Map<String, Object>> dimensionsToFirestore(
            @NonNull List<VariantDimension> dimensions) {
        List<Map<String, Object>> result = new ArrayList<>();
        int i = 0;
        for (VariantDimension dim : dimensions) {
            if (dim == null || dim.getName() == null || dim.getName().trim().isEmpty()) continue;
            List<String> options = dim.getOptions() != null ? dim.getOptions() : new ArrayList<>();
            if (options.isEmpty()) continue;
            Map<String, Object> map = new HashMap<>();
            map.put("id", dim.getId() != null ? dim.getId() : ("dim_" + i));
            map.put("name", dim.getName().trim());
            map.put("options", new ArrayList<>(options));
            result.add(map);
            i++;
        }
        return result;
    }

    @NonNull
    public static String suggestSku(@NonNull String productName, @NonNull String variantName, int index) {
        String base = slug(productName);
        String variantPart = slug(variantName.replace(COMBO_SEPARATOR, "-"));
        if (base.isEmpty()) {
            base = "SKU";
        }
        if (variantPart.isEmpty()) {
            return base + "-" + (index + 1);
        }
        return base + "-" + variantPart;
    }

    /** Build dimensions from legacy flavors / weights / packagingTypes fields. */
    @NonNull
    public static List<VariantDimension> migrateLegacyDimensions(
            @Nullable Object flavors,
            @Nullable Object weights,
            @Nullable Object packagingTypes) {
        List<VariantDimension> dims = new ArrayList<>();
        List<String> flavorOpts = extractOptionNames(flavors);
        List<String> weightOpts = extractOptionNames(weights);
        List<String> pkgOpts = extractOptionNames(packagingTypes);

        if (!flavorOpts.isEmpty()) {
            dims.add(dim("dim_flavor", "Hương vị", flavorOpts));
        }
        if (!pkgOpts.isEmpty()) {
            dims.add(dim("dim_packaging", "Loại đóng gói", pkgOpts));
        }
        if (!weightOpts.isEmpty()) {
            dims.add(dim("dim_weight", "Khối lượng", weightOpts));
        }
        if (dims.size() > MAX_DIMENSIONS) {
            return new ArrayList<>(dims.subList(0, MAX_DIMENSIONS));
        }
        return dims;
    }

    @NonNull
    private static VariantDimension dim(String id, String name, List<String> options) {
        VariantDimension d = new VariantDimension();
        d.setId(id);
        d.setName(name);
        d.setOptions(new ArrayList<>(options));
        return d;
    }

    /**
     * Infer selection map on a variant from combo name + dimension option lists.
     */
    public static void attachSelectionsFromName(@NonNull ProductVariant variant,
                                                @NonNull List<VariantDimension> dimensions) {
        if (variant.getSelections() != null && !variant.getSelections().isEmpty()) {
            return;
        }
        String name = variant.getName();
        if (name == null || name.isEmpty() || dimensions.isEmpty()) {
            return;
        }
        Map<String, String> selections = new LinkedHashMap<>();
        if (name.contains(COMBO_SEPARATOR)) {
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

    public static boolean looksLikeFlatOptionSkus(@NonNull List<ProductVariant> variants,
                                                   @NonNull List<String> optionLabels) {
        if (variants.isEmpty() || optionLabels.isEmpty()) {
            return false;
        }
        int flatMatches = 0;
        for (ProductVariant variant : variants) {
            String name = variant.getName();
            if (name == null || name.isEmpty()) continue;
            if (name.contains(COMBO_SEPARATOR)) {
                return false;
            }
            for (String option : optionLabels) {
                if (name.equalsIgnoreCase(option)) {
                    flatMatches++;
                    break;
                }
            }
        }
        return flatMatches >= Math.min(variants.size(), 2);
    }

    public static boolean dimensionsAlignWithVariantRows(
            @Nullable List<VariantDimension> dims,
            @Nullable List<ProductVariant> skus) {
        if (dims == null || dims.isEmpty()) return false;
        if (skus == null || skus.isEmpty()) return true;
        List<String> allOptions = new ArrayList<>();
        for (VariantDimension dim : dims) {
            if (dim == null || dim.getOptions() == null) continue;
            for (String opt : dim.getOptions()) {
                if (opt != null && !opt.trim().isEmpty()) allOptions.add(opt.trim());
            }
        }
        if (allOptions.isEmpty()) return false;
        for (ProductVariant sku : skus) {
            if (sku == null || sku.getName() == null || sku.getName().trim().isEmpty()) continue;
            String name = sku.getName().trim();
            if (name.contains(COMBO_SEPARATOR)) {
                String[] parts = name.split(" · ");
                for (String part : parts) {
                    boolean found = false;
                    for (String opt : allOptions) {
                        if (opt.equalsIgnoreCase(part.trim())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
            } else {
                boolean found = false;
                for (String opt : allOptions) {
                    if (opt.equalsIgnoreCase(name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
        }
        return true;
    }

    /**
     * From flat SKUs (500g / 1kg / Túi zip):
     * - Split into Khối lượng + Loại đóng gói when both kinds exist
     * - Otherwise one "Phân loại" group
     * From combo SKUs (A · B) → restore dimensions by splitting parts.
     */
    @NonNull
    public static List<VariantDimension> rebuildDimensionsFromSkus(
            @NonNull List<ProductVariant> skus) {
        List<VariantDimension> result = new ArrayList<>();
        if (skus.isEmpty()) return result;

        boolean anyCombo = false;
        for (ProductVariant sku : skus) {
            if (sku != null && sku.getName() != null && sku.getName().contains(COMBO_SEPARATOR)) {
                anyCombo = true;
                break;
            }
        }

        if (!anyCombo) {
            List<String> labels = new ArrayList<>();
            for (ProductVariant sku : skus) {
                if (sku == null || sku.getName() == null) continue;
                String name = sku.getName().trim();
                if (!name.isEmpty() && !labels.contains(name)) labels.add(name);
            }
            return rebuildFlatLabelsIntoDimensions(labels);
        }

        List<List<String>> columns = new ArrayList<>();
        for (ProductVariant sku : skus) {
            if (sku == null || sku.getName() == null || !sku.getName().contains(COMBO_SEPARATOR)) {
                continue;
            }
            String[] parts = sku.getName().split(" · ");
            for (int i = 0; i < parts.length && i < MAX_DIMENSIONS; i++) {
                while (columns.size() <= i) columns.add(new ArrayList<>());
                String part = parts[i].trim();
                if (!part.isEmpty() && !columns.get(i).contains(part)) {
                    columns.get(i).add(part);
                }
            }
        }
        String[] defaultNames = {"Nhóm 1", "Nhóm 2", "Nhóm 3"};
        for (int i = 0; i < columns.size(); i++) {
            List<String> col = columns.get(i);
            VariantDimension dim = new VariantDimension();
            dim.setId("dim_" + i);
            dim.setName(guessDimensionName(col, defaultNames[i]));
            dim.setOptions(col);
            result.add(dim);
        }
        return result;
    }

    /**
     * Heuristic split for old flat catalogs: 500g/1kg vs Túi zip/Hũ thủy tinh.
     */
    @NonNull
    public static List<VariantDimension> rebuildFlatLabelsIntoDimensions(
            @NonNull List<String> labels) {
        List<VariantDimension> result = new ArrayList<>();
        if (labels.isEmpty()) return result;

        List<String> weights = new ArrayList<>();
        List<String> packaging = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String label : labels) {
            if (looksLikeWeightLabel(label)) {
                weights.add(label);
            } else if (looksLikePackagingLabel(label)) {
                packaging.add(label);
            } else {
                other.add(label);
            }
        }

        // Only split when we clearly have both weight and packaging kinds.
        if (!weights.isEmpty() && !packaging.isEmpty()) {
            result.add(dim("dim_weight", "Khối lượng", weights));
            result.add(dim("dim_packaging", "Loại đóng gói", packaging));
            if (!other.isEmpty() && result.size() < MAX_DIMENSIONS) {
                result.add(dim("dim_other", "Phân loại khác", other));
            }
            return result;
        }

        // Single mixed list — keep as one group; admin can split manually.
        VariantDimension dim = new VariantDimension();
        dim.setId("dim_phan_loai");
        dim.setName("Phân loại");
        dim.setOptions(new ArrayList<>(labels));
        result.add(dim);
        return result;
    }

    @NonNull
    private static String guessDimensionName(@NonNull List<String> options, @NonNull String fallback) {
        int weightHits = 0;
        int pkgHits = 0;
        for (String opt : options) {
            if (looksLikeWeightLabel(opt)) weightHits++;
            if (looksLikePackagingLabel(opt)) pkgHits++;
        }
        if (weightHits > pkgHits && weightHits > 0) return "Khối lượng";
        if (pkgHits > weightHits && pkgHits > 0) return "Loại đóng gói";
        return fallback;
    }

    public static boolean looksLikeWeightLabel(@Nullable String label) {
        if (label == null) return false;
        String n = label.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return false;
        // 250g, 500gr, 1kg, 1.5 kg, 100ml, 1l...
        return n.matches(".*\\d+([.,]\\d+)?\\s*(g|gr|kg|mg|ml|l|lit|lít).*")
                || n.contains("khối lượng")
                || n.contains("dung tích")
                || n.contains("thể tích");
    }

    public static boolean looksLikePackagingLabel(@Nullable String label) {
        if (label == null) return false;
        String n = label.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return false;
        return n.contains("tui") || n.contains("túi") || n.contains("zip")
                || n.contains("hu") || n.contains("hũ") || n.contains("hop") || n.contains("hộp")
                || n.contains("chai") || n.contains("goi") || n.contains("gói")
                || n.contains("jar") || n.contains("bag") || n.contains("pack")
                || n.contains("hut") || n.contains("hút") || n.contains("chan khong")
                || n.contains("chân không") || n.contains("thuy tinh") || n.contains("thủy tinh")
                || n.contains("dong goi") || n.contains("đóng gói");
    }

    private static String slug(String input) {
        if (input == null) return "";
        return input.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    // ---- deprecated wrappers (call sites may still compile during transition) ----

    @Deprecated
    @NonNull
    public static List<ProductVariant> generateCombinations(@NonNull List<String> flavors,
                                                            @NonNull List<String> sizes,
                                                            double basePrice,
                                                            double baseOriginalPrice) {
        List<VariantDimension> dims = new ArrayList<>();
        if (!flavors.isEmpty()) {
            dims.add(dim("dim_a", "Nhóm 1", flavors));
        }
        if (!sizes.isEmpty()) {
            dims.add(dim("dim_b", "Nhóm 2", sizes));
        }
        return generateCombinations(dims, basePrice, baseOriginalPrice);
    }

    @Deprecated
    @NonNull
    public static List<ProductVariant> generateCombinations(@NonNull List<String> flavors,
                                                            @NonNull List<String> sizes,
                                                            double basePrice,
                                                            double baseOriginalPrice,
                                                            int baseStock) {
        return generateCombinations(flavors, sizes, basePrice, baseOriginalPrice);
    }

    @Deprecated
    public static boolean looksLikePackaging(@NonNull List<String> labels) {
        if (labels.isEmpty()) return false;
        int hits = 0;
        for (String label : labels) {
            if (looksLikePackagingLabel(label)) {
                hits++;
            }
        }
        return hits * 2 >= labels.size();
    }
}
