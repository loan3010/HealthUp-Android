package com.example.healthup.util;

import android.content.Context;
import android.widget.Toast;

import com.example.healthup.R;
import com.example.healthup.firebase.FirestoreManager;
import com.example.models.CartItem;
import com.example.models.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;

public final class CartHelper {

    private CartHelper() {
    }

    public static void addToCart(Context context, Product product, Product.ProductVariant variant, int quantity) {
        if (context == null || product == null || quantity <= 0) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            GuestCartManager.getInstance(context).addItem(product, variant, quantity);
            Toast.makeText(context, R.string.added_to_cart, Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        String productId = product.getId();
        String variantId = variant != null ? variant.getId() : null;

        CollectionReference cartRef = FirestoreManager.getInstance().getFirestore()
                .collection("users").document(userId).collection("cart");

        cartRef.whereEqualTo("productId", productId)
                .whereEqualTo("variantId", variantId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Long currentQtyLong = doc.getLong("quantity");
                        long currentQty = currentQtyLong != null ? currentQtyLong : 0;
                        doc.getReference().update("quantity", currentQty + quantity, "updatedAt", Timestamp.now());
                    } else {
                        CartItem newItem = new CartItem(productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                            newItem.setOriginalPrice(variant.getPrice());
                        } else {
                            newItem.setPrice(product.getPrice());
                            newItem.setOriginalPrice(product.getOriginalPrice());
                        }
                        newItem.setUpdatedAt(Timestamp.now());
                        cartRef.add(newItem);
                    }
                    Toast.makeText(context, R.string.added_to_cart, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, context.getString(R.string.register_error_generic), Toast.LENGTH_SHORT).show()
                );
    }
}
