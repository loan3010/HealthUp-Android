package com.example.healthup.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.healthup.firebase.FirestoreManager;
import com.example.models.CartItem;
import com.example.models.Product;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class GuestCartManager {

    public static final String ACTION_GUEST_CART_CHANGED = "com.example.healthup.ACTION_GUEST_CART_CHANGED";
    private static final String PREFS_NAME = "guest_cart_prefs";
    private static final String KEY_ITEMS_JSON = "guest_cart_items";

    private final Context context;
    private final SharedPreferences prefs;

    private GuestCartManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static GuestCartManager getInstance(Context context) {
        return new GuestCartManager(context);
    }

    public List<CartItem> getItems() {
        return itemsFromJson(prefs.getString(KEY_ITEMS_JSON, "[]"));
    }

    public void saveItems(List<CartItem> items) {
        prefs.edit().putString(KEY_ITEMS_JSON, itemsToJson(items)).apply();
        context.sendBroadcast(new Intent(ACTION_GUEST_CART_CHANGED));
    }

    public void addItem(Product product, Product.ProductVariant variant, int quantity) {
        if (product == null || quantity <= 0) {
            return;
        }

        List<CartItem> items = getItems();
        String productId = product.getId();
        String variantId = variant != null ? variant.getId() : null;

        for (CartItem existing : items) {
            if (TextUtils.equals(existing.getProductId(), productId)
                    && TextUtils.equals(existing.getVariantId(), variantId)) {
                existing.setQuantity(existing.getQuantity() + quantity);
                existing.setSelected(true);
                saveItems(items);
                return;
            }
        }

        CartItem newItem = new CartItem(productId, product, quantity, null);
        newItem.setId(UUID.randomUUID().toString());
        if (variant != null) {
            newItem.setVariantId(variant.getId());
            newItem.setVariantName(variant.getName());
            newItem.setPrice(variant.getPrice());
            newItem.setOriginalPrice(variant.getPrice());
        } else {
            newItem.setPrice(product.getPrice());
            newItem.setOriginalPrice(product.getOriginalPrice());
        }
        newItem.setSelected(true);
        items.add(newItem);
        saveItems(items);
    }

    public void updateQuantity(String itemId, int quantity) {
        List<CartItem> items = getItems();
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            if (TextUtils.equals(item.getId(), itemId)) {
                if (quantity <= 0) {
                    iterator.remove();
                } else {
                    item.setQuantity(quantity);
                }
                saveItems(items);
                return;
            }
        }
    }

    public void updateItem(CartItem updated) {
        if (updated == null || updated.getId() == null) {
            return;
        }
        List<CartItem> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            if (TextUtils.equals(items.get(i).getId(), updated.getId())) {
                items.set(i, updated);
                saveItems(items);
                return;
            }
        }
    }

    public void removeItem(String itemId) {
        List<CartItem> items = getItems();
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (TextUtils.equals(iterator.next().getId(), itemId)) {
                iterator.remove();
                saveItems(items);
                return;
            }
        }
    }

    public void removeItems(List<CartItem> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (CartItem item : toRemove) {
            if (item.getId() != null) {
                ids.add(item.getId());
            }
        }
        List<CartItem> items = getItems();
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (ids.contains(iterator.next().getId())) {
                iterator.remove();
            }
        }
        saveItems(items);
    }

    public void clear() {
        prefs.edit().remove(KEY_ITEMS_JSON).apply();
        context.sendBroadcast(new Intent(ACTION_GUEST_CART_CHANGED));
    }

    public boolean hasItems() {
        return !getItems().isEmpty();
    }

    public void mergeToFirestore(String userId, Runnable onComplete) {
        List<CartItem> guestItems = getItems();
        if (TextUtils.isEmpty(userId) || guestItems.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        CollectionReference cartRef = FirestoreManager.getInstance().getFirestore()
                .collection("users").document(userId).collection("cart");

        cartRef.get().addOnSuccessListener(snapshot -> {
            for (CartItem guestItem : guestItems) {
                mergeSingleItem(cartRef, snapshot, guestItem);
            }
            clear();
            if (onComplete != null) {
                onComplete.run();
            }
        }).addOnFailureListener(e -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void mergeSingleItem(
            CollectionReference cartRef,
            com.google.firebase.firestore.QuerySnapshot snapshot,
            CartItem guestItem
    ) {
        String productId = guestItem.getProductId();
        String variantId = guestItem.getVariantId();

        for (QueryDocumentSnapshot doc : snapshot) {
            String existingProductId = doc.getString("productId");
            String existingVariantId = doc.getString("variantId");
            if (TextUtils.equals(productId, existingProductId)
                    && TextUtils.equals(variantId, existingVariantId)) {
                Long currentQty = doc.getLong("quantity");
                int mergedQty = (currentQty != null ? currentQty.intValue() : 0) + guestItem.getQuantity();
                doc.getReference().update("quantity", mergedQty, "updatedAt", Timestamp.now());
                return;
            }
        }

        CartItem newItem = new CartItem(productId, guestItem.getProduct(), guestItem.getQuantity(), userIdFromRef(cartRef));
        newItem.setName(guestItem.getName());
        newItem.setImageUrl(guestItem.getImageUrl());
        newItem.setPrice(guestItem.getPrice());
        newItem.setOriginalPrice(guestItem.getOriginalPrice());
        newItem.setVariantId(guestItem.getVariantId());
        newItem.setVariantName(guestItem.getVariantName());
        newItem.setWeight(guestItem.getWeight());
        newItem.setFlavor(guestItem.getFlavor());
        newItem.setPackageType(guestItem.getPackageType());
        newItem.setUpdatedAt(Timestamp.now());
        cartRef.add(newItem);
    }

    private String userIdFromRef(CollectionReference cartRef) {
        return cartRef.getParent().getId();
    }

    static String itemsToJson(List<CartItem> items) {
        JSONArray array = new JSONArray();
        if (items == null) {
            return array.toString();
        }
        for (CartItem item : items) {
            try {
                array.put(itemToJson(item));
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    static List<CartItem> itemsFromJson(String json) {
        List<CartItem> items = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                CartItem item = itemFromJson(array.getJSONObject(i));
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    private static JSONObject itemToJson(CartItem item) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", item.getId());
        object.put("productId", item.getProductId());
        object.put("name", item.getName());
        object.put("imageUrl", item.getImageUrl());
        object.put("price", item.getPrice());
        object.put("originalPrice", item.getOriginalPrice());
        object.put("quantity", item.getQuantity());
        object.put("variantId", item.getVariantId());
        object.put("variantName", item.getVariantName());
        object.put("weight", item.getWeight());
        object.put("flavor", item.getFlavor());
        object.put("packageType", item.getPackageType());
        object.put("selected", item.isSelected());
        return object;
    }

    private static CartItem itemFromJson(JSONObject object) throws JSONException {
        CartItem item = new CartItem();
        item.setId(object.optString("id", null));
        item.setProductId(object.optString("productId", null));
        item.setName(object.optString("name", null));
        item.setImageUrl(object.optString("imageUrl", null));
        item.setPrice(object.optDouble("price", 0d));
        item.setOriginalPrice(object.optDouble("originalPrice", 0d));
        item.setQuantity(object.optInt("quantity", 1));
        item.setVariantId(object.optString("variantId", null));
        item.setVariantName(object.optString("variantName", null));
        item.setWeight(object.optString("weight", null));
        item.setFlavor(object.optString("flavor", null));
        item.setPackageType(object.optString("packageType", null));
        item.setSelected(object.optBoolean("selected", true));
        return item;
    }
}
