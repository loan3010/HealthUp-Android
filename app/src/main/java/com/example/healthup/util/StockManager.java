package com.example.healthup.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.OrderItem;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StockManager {

    public interface StockCallback {
        void onSuccess();
        void onInsufficientStock(@NonNull String productName, int available);
        void onError(@NonNull String message);
    }

    private StockManager() {
    }

    public static void deductStock(@NonNull FirebaseFirestore db,
                                   @NonNull List<OrderItem> items,
                                   @NonNull StockCallback callback) {
        runStockChange(db, items, false, callback);
    }

    public static void restoreStock(@NonNull FirebaseFirestore db,
                                    @NonNull List<OrderItem> items,
                                    @NonNull StockCallback callback) {
        runStockChange(db, items, true, callback);
    }

    public static Task<Void> restoreStockTask(@NonNull FirebaseFirestore db,
                                              @NonNull List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Tasks.forResult(null);
        }
        return db.runTransaction(transaction -> {
            applyStockChange(transaction, db, items, true);
            return null;
        });
    }

    public static int resolveAvailableStock(@NonNull DocumentSnapshot productSnap,
                                            @Nullable String variantId) {
        if (!productSnap.exists()) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        List<Object> variantsRaw = (List<Object>) productSnap.get("variants");
        if (variantsRaw != null && !variantsRaw.isEmpty()
                && variantId != null && !variantId.isEmpty()) {
            for (Object item : variantsRaw) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> map = (Map<?, ?>) item;
                String vId = map.get("id") != null ? String.valueOf(map.get("id")) : null;
                if (variantId.equals(vId)) {
                    return readInt(map.get("stock"), 0);
                }
            }
            return 0;
        }
        return readInt(productSnap.get("stock"), readInt(productSnap.get("stockCount"), 0));
    }

    private static void runStockChange(@NonNull FirebaseFirestore db,
                                       @NonNull List<OrderItem> items,
                                       boolean restore,
                                       @NonNull StockCallback callback) {
        if (items == null || items.isEmpty()) {
            callback.onSuccess();
            return;
        }

        db.runTransaction(transaction -> {
            applyStockChange(transaction, db, items, restore);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof InsufficientStockException) {
                        InsufficientStockException ise = (InsufficientStockException) cause;
                        callback.onInsufficientStock(ise.productName, ise.available);
                    } else {
                        String message = cause.getMessage() != null ? cause.getMessage() : "Lỗi cập nhật tồn kho";
                        callback.onError(message);
                    }
                });
    }

    private static void applyStockChange(@NonNull Transaction transaction,
                                         @NonNull FirebaseFirestore db,
                                         @NonNull List<OrderItem> items,
                                         boolean restore) throws FirebaseFirestoreException {
        Map<String, Map<String, Integer>> grouped = groupItems(items);

        Map<String, DocumentSnapshot> snapshots = new HashMap<>();
        for (String productId : grouped.keySet()) {
            if (productId == null || productId.isEmpty()) {
                throw new IllegalStateException("Thiếu productId trong đơn hàng");
            }
            DocumentReference productRef = db.collection("products").document(productId);
            DocumentSnapshot snap = transaction.get(productRef);
            if (!snap.exists()) {
                throw new IllegalStateException("Không tìm thấy sản phẩm: " + productId);
            }
            snapshots.put(productId, snap);
        }

        for (Map.Entry<String, Map<String, Integer>> entry : grouped.entrySet()) {
            String productId = entry.getKey();
            DocumentReference productRef = db.collection("products").document(productId);
            DocumentSnapshot snap = snapshots.get(productId);
            Map<String, Integer> deltas = new HashMap<>();
            for (Map.Entry<String, Integer> variantEntry : entry.getValue().entrySet()) {
                int qty = variantEntry.getValue();
                deltas.put(variantEntry.getKey(), restore ? qty : -qty);
            }
            applyDeltas(transaction, productRef, snap, deltas);
        }
    }

    private static Map<String, Map<String, Integer>> groupItems(@NonNull List<OrderItem> items) {
        Map<String, Map<String, Integer>> grouped = new HashMap<>();
        for (OrderItem item : items) {
            if (item == null) continue;
            String productId = item.getProductId();
            if (productId == null || productId.isEmpty()) continue;
            String variantId = item.getVariantId() != null ? item.getVariantId() : "";
            int quantity = Math.max(0, item.getQuantity());
            if (quantity <= 0) continue;

            grouped.computeIfAbsent(productId, key -> new HashMap<>())
                    .merge(variantId, quantity, Integer::sum);
        }
        return grouped;
    }

    private static void applyDeltas(@NonNull Transaction transaction,
                                    @NonNull DocumentReference productRef,
                                    @NonNull DocumentSnapshot snap,
                                    @NonNull Map<String, Integer> deltas) throws FirebaseFirestoreException {
        @SuppressWarnings("unchecked")
        List<Object> variantsRaw = (List<Object>) snap.get("variants");
        boolean hasVariants = variantsRaw != null && !variantsRaw.isEmpty();

        if (hasVariants) {
            List<Map<String, Object>> variants = deepCopyVariants(variantsRaw);
            for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
                String variantId = entry.getKey();
                int delta = entry.getValue();
                if (variantId == null || variantId.isEmpty()) {
                    throw new IllegalStateException("Thiếu variantId cho sản phẩm có biến thể");
                }

                boolean found = false;
                for (Map<String, Object> variant : variants) {
                    String vId = variant.get("id") != null ? String.valueOf(variant.get("id")) : null;
                    if (!variantId.equals(vId)) continue;

                    int current = readInt(variant.get("stock"), 0);
                    int newStock = current + delta;
                    if (newStock < 0) {
                        String name = variant.get("name") != null
                                ? String.valueOf(variant.get("name"))
                                : snap.getString("name");
                        throw new InsufficientStockException(
                                name != null ? name : "Sản phẩm", current);
                    }
                    variant.put("stock", newStock);
                    found = true;
                    break;
                }
                if (!found) {
                    throw new IllegalStateException("Không tìm thấy biến thể: " + variantId);
                }
            }

            int totalStock = 0;
            for (Map<String, Object> variant : variants) {
                totalStock += readInt(variant.get("stock"), 0);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("variants", variants);
            updates.put("stock", totalStock);
            updates.put("stockCount", totalStock);
            transaction.update(productRef, updates);
            return;
        }

        int totalDelta = 0;
        for (int delta : deltas.values()) {
            totalDelta += delta;
        }
        int current = readInt(snap.get("stock"), readInt(snap.get("stockCount"), 0));
        int newStock = current + totalDelta;
        if (newStock < 0) {
            String name = snap.getString("name");
            throw new InsufficientStockException(
                    name != null ? name : "Sản phẩm", current);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("stock", newStock);
        updates.put("stockCount", newStock);
        transaction.update(productRef, updates);
    }

    private static List<Map<String, Object>> deepCopyVariants(@Nullable List<Object> raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (Object item : raw) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            result.add(copy);
        }
        return result;
    }

    private static int readInt(@Nullable Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static final class InsufficientStockException extends RuntimeException {
        final String productName;
        final int available;

        InsufficientStockException(String productName, int available) {
            super("insufficient_stock");
            this.productName = productName;
            this.available = available;
        }
    }
}
