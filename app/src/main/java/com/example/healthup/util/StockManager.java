package com.example.healthup.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.models.OrderItem;
import com.example.models.Product;
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
        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            applyStockChange(transaction, db, items, true, true);
            return null;
        }).continueWithTask(task -> {
            if (task.isSuccessful()) {
                return Tasks.<Void>forResult(null);
            }
            if (!isPermissionDenied(task.getException())) {
                return Tasks.forException(task.getException() != null
                        ? task.getException()
                        : new FirebaseFirestoreException("restore_stock_failed",
                        FirebaseFirestoreException.Code.ABORTED));
            }
            // Older rules: retry without top-level sold.
            return db.runTransaction((Transaction.Function<Void>) transaction -> {
                applyStockChange(transaction, db, items, true, false);
                return null;
            });
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

        // Try with top-level sold (needs rules allowing `sold`). If Console still has
        // older isStockOnlyUpdate(['stock','stockCount','variants']), fall back to
        // inventory-only keys so checkout still completes (variant.sold stays nested).
        runStockTransaction(db, items, restore, /*writeProductSold*/ true, callback,
                /*allowInventoryFallback*/ true);
    }

    private static void runStockTransaction(@NonNull FirebaseFirestore db,
                                            @NonNull List<OrderItem> items,
                                            boolean restore,
                                            boolean writeProductSold,
                                            @NonNull StockCallback callback,
                                            boolean allowInventoryFallback) {
        db.runTransaction(transaction -> {
            applyStockChange(transaction, db, items, restore, writeProductSold);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof InsufficientStockException) {
                        InsufficientStockException ise = (InsufficientStockException) cause;
                        callback.onInsufficientStock(ise.productName, ise.available);
                        return;
                    }
                    if (allowInventoryFallback && writeProductSold && isPermissionDenied(e)) {
                        runStockTransaction(db, items, restore, false, callback, false);
                        return;
                    }
                    if (isPermissionDenied(e)) {
                        callback.onError(
                                "PERMISSION_DENIED: thiếu quyền cập nhật tồn kho trên products. "
                                        + "Deploy firestore.rules (isStockOnlyUpdate gồm sold).");
                        return;
                    }
                    String message = cause.getMessage() != null ? cause.getMessage() : "Lỗi cập nhật tồn kho";
                    callback.onError(message);
                });
    }

    private static boolean isPermissionDenied(@Nullable Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof FirebaseFirestoreException) {
                if (((FirebaseFirestoreException) cursor).getCode()
                        == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    return true;
                }
            }
            String m = cursor.getMessage();
            if (m != null && m.toLowerCase().contains("permission")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static void applyStockChange(@NonNull Transaction transaction,
                                         @NonNull FirebaseFirestore db,
                                         @NonNull List<OrderItem> items,
                                         boolean restore,
                                         boolean writeProductSold) throws FirebaseFirestoreException {
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
            applyDeltas(transaction, productRef, snap, deltas, writeProductSold);
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
                                    @NonNull Map<String, Integer> deltas,
                                    boolean writeProductSold) throws FirebaseFirestoreException {
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
                    // delta < 0 = deduct → tăng sold; delta > 0 = restore → giảm sold
                    if (delta < 0) {
                        int qty = -delta;
                        int currentVariantSold = readInt(variant.get("sold"), 0);
                        int currentTotalSold = 0;
                        for (Map<String, Object> v : variants) {
                            currentTotalSold += readInt(v.get("sold"), 0);
                        }
                        if (currentTotalSold <= 0 && !Boolean.TRUE.equals(snap.getBoolean("adminCreated"))) {
                            // Legacy seed data: continue from mock baseline (not from 0 → 1).
                            int reviews = readInt(snap.get("reviewCount"), 0);
                            int baseline = Product.computeMockSold(snap.getId(), reviews);
                            variant.put("sold", baseline + qty);
                        } else if (currentTotalSold <= 0) {
                            // Admin-added product: genuine sales start from 0.
                            variant.put("sold", qty);
                        } else {
                            variant.put("sold", currentVariantSold + qty);
                        }
                    } else if (delta > 0) {
                        int currentSold = readInt(variant.get("sold"), 0);
                        variant.put("sold", Math.max(0, currentSold - delta));
                    }
                    found = true;
                    break;
                }
                if (!found) {
                    throw new IllegalStateException("Không tìm thấy biến thể: " + variantId);
                }
            }

            int totalStock = 0;
            int totalSold = 0;
            for (Map<String, Object> variant : variants) {
                Object enabledVal = variant.get("enabled");
                boolean enabled = !(enabledVal instanceof Boolean) || (Boolean) enabledVal;
                if (enabled) {
                    totalStock += readInt(variant.get("stock"), 0);
                }
                totalSold += readInt(variant.get("sold"), 0);
            }

            // Nested variant.sold is part of `variants` key (OK with older rules).
            // Top-level `sold` needs isStockOnlyUpdate to allow `sold`.
            Map<String, Object> updates = new HashMap<>();
            updates.put("variants", variants);
            updates.put("stock", totalStock);
            updates.put("stockCount", totalStock);
            if (writeProductSold) {
                updates.put("sold", totalSold);
            }
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
        if (writeProductSold) {
            if (totalDelta < 0) {
                int qty = -totalDelta;
                int currentSold = readInt(snap.get("sold"), readInt(snap.get("soldCount"), 0));
                if (currentSold <= 0 && !Boolean.TRUE.equals(snap.getBoolean("adminCreated"))) {
                    // Legacy seed data continues from a mock baseline; admin-added starts from 0.
                    int reviews = readInt(snap.get("reviewCount"), 0);
                    currentSold = Product.computeMockSold(snap.getId(), reviews);
                }
                updates.put("sold", currentSold + qty);
            } else if (totalDelta > 0) {
                int currentSold = readInt(snap.get("sold"), readInt(snap.get("soldCount"), 0));
                updates.put("sold", Math.max(0, currentSold - totalDelta));
            }
        }
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
