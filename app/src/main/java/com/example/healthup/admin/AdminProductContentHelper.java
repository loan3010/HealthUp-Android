package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.Product;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

public final class AdminProductContentHelper {

    private AdminProductContentHelper() {
    }

    @NonNull
    public static String formatNutritionForEdit(@Nullable Product product, @Nullable DocumentSnapshot doc) {
        if (product == null) {
            return "";
        }
        if (product.getNutritionText() != null && !product.getNutritionText().trim().isEmpty()) {
            return product.getNutritionText().trim();
        }
        List<Product.NutritionItem> items = product.getNutrition();
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Product.NutritionItem item : items) {
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
        return builder.toString();
    }

    @NonNull
    public static String readString(@Nullable DocumentSnapshot doc, @NonNull String field) {
        if (doc == null || !doc.exists()) {
            return "";
        }
        String value = doc.getString(field);
        return value != null ? value.trim() : "";
    }
}
