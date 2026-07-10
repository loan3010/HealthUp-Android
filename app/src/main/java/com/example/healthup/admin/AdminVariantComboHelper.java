package com.example.healthup.admin;

import androidx.annotation.NonNull;

import com.example.models.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminVariantComboHelper {

    private static final String COMBO_SEPARATOR = " · ";

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
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @NonNull
    public static List<Product.ProductVariant> generateCombinations(@NonNull List<String> flavors,
                                                                    @NonNull List<String> sizes,
                                                                    double basePrice,
                                                                    double baseOriginalPrice,
                                                                    int baseStock) {
        List<String> dimensionA = !flavors.isEmpty() ? flavors : sizes;
        List<String> dimensionB = !flavors.isEmpty() && !sizes.isEmpty() ? sizes : new ArrayList<>();

        List<Product.ProductVariant> result = new ArrayList<>();
        if (dimensionA.isEmpty()) {
            return result;
        }

        int index = 0;
        if (dimensionB.isEmpty()) {
            for (String label : dimensionA) {
                result.add(buildVariant(label, basePrice, baseOriginalPrice, baseStock, index++));
            }
            return result;
        }

        for (String flavor : flavors) {
            for (String size : sizes) {
                String label = flavor + COMBO_SEPARATOR + size;
                result.add(buildVariant(label, basePrice, baseOriginalPrice, baseStock, index++));
            }
        }
        return result;
    }

    @NonNull
    public static List<Product.ProductVariant> mergeWithExisting(@NonNull List<Product.ProductVariant> generated,
                                                                   @NonNull List<Product.ProductVariant> existing) {
        List<Product.ProductVariant> merged = new ArrayList<>();
        for (Product.ProductVariant variant : generated) {
            Product.ProductVariant kept = findByName(existing, variant.getName());
            if (kept != null) {
                variant.setPrice(kept.getPrice() > 0 ? kept.getPrice() : variant.getPrice());
                variant.setOriginalPrice(kept.getOriginalPrice() > 0 ? kept.getOriginalPrice() : variant.getOriginalPrice());
                variant.setStock(kept.getStock());
                variant.setSku(kept.getSku());
                variant.setImageUrl(kept.getImageUrl());
                variant.setId(kept.getId());
            }
            merged.add(variant);
        }
        return merged;
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

    private static Product.ProductVariant buildVariant(String name,
                                                       double basePrice,
                                                       double baseOriginalPrice,
                                                       int baseStock,
                                                       int index) {
        Product.ProductVariant variant = new Product.ProductVariant();
        variant.setId("variant_" + index);
        variant.setName(name);
        variant.setPrice(basePrice);
        variant.setOriginalPrice(baseOriginalPrice > 0 ? baseOriginalPrice : basePrice);
        variant.setStock(baseStock);
        return variant;
    }

    private static Product.ProductVariant findByName(@NonNull List<Product.ProductVariant> existing, String name) {
        for (Product.ProductVariant variant : existing) {
            if (name.equals(variant.getName())) {
                return variant;
            }
        }
        return null;
    }

    private static String slug(String input) {
        if (input == null) return "";
        return input.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
