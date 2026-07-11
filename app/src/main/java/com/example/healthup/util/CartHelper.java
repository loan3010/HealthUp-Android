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


    public interface CartCallback {
        void onSuccess();
        void onFailure(Exception e);
    }


    public static void addToCart(Context context, Product product, Product.ProductVariant variant, int quantity) {
        addToCart(context, product, variant, quantity, null);
    }


    public static void addToCart(Context context, Product product, Product.ProductVariant variant, int quantity, CartCallback callback) {
        if (context == null || product == null || quantity <= 0) {
            if (callback != null) callback.onFailure(new Exception("Invalid input"));
            return;
        }


        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            GuestCartManager.getInstance(context).addItem(product, variant, quantity);
            if (callback != null) {
                callback.onSuccess();
            } else {
                Toast.makeText(context, R.string.added_to_cart, Toast.LENGTH_SHORT).show();
            }
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
                        doc.getReference().update("quantity", currentQty + quantity, "updatedAt", Timestamp.now())
                                .addOnSuccessListener(v -> {
                                    if (callback != null) callback.onSuccess();
                                    else Toast.makeText(context, R.string.added_to_cart, Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        CartItem newItem = new CartItem(productId, product, quantity, userId);
                        if (variant != null) {
                            newItem.setVariantId(variant.getId());
                            newItem.setVariantName(variant.getName());
                            newItem.setPrice(variant.getPrice());
                            newItem.setOriginalPrice(variant.getPrice());
                            if (variant.getImageUrl() != null && !variant.getImageUrl().isEmpty()) {
                                newItem.setImageUrl(variant.getImageUrl());
                            }
                        } else {
                            newItem.setPrice(product.getPrice());
                            newItem.setOriginalPrice(product.getOriginalPrice());
                        }
                        newItem.setUpdatedAt(Timestamp.now());
                        cartRef.add(newItem)
                                .addOnSuccessListener(v -> {
                                    if (callback != null) callback.onSuccess();
                                    else Toast.makeText(context, R.string.added_to_cart, Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                    else Toast.makeText(context, context.getString(R.string.register_error_generic), Toast.LENGTH_SHORT).show();
                });
    }


    /**
     * FIX (đồng bộ số lượng giỏ hàng giữa CartFragment và các badge icon):
     * Trước đây MainActivity và ProductDetailActivity đếm số sản phẩm hợp lệ trong giỏ hàng
     * chỉ bằng cách kiểm tra doc.getString("name") ở cấp cao nhất của document. Trong khi đó,
     * CartFragment.parseCartItem() lại chấp nhận CẢ trường hợp "name" nằm lồng bên trong field
     * "product" (Map) — dữ liệu cũ / dữ liệu tạo từ luồng khác có thể chỉ có "product.name" mà
     * không có "name" ở cấp cao nhất. Hệ quả: 2 nơi đếm ra 2 con số khác nhau, khiến số lượng
     * hiển thị trên icon giỏ hàng (Thanh điều hướng, Chi tiết sản phẩm) không khớp với số lượng
     * hiển thị ở tiêu đề "Giỏ hàng (n)" trong trang Giỏ hàng.
     *
     * Hàm này dùng chung logic hợp lệ hoá với CartFragment.parseCartItem() để đảm bảo mọi nơi
     * đếm số lượng giỏ hàng đều cho ra cùng 1 kết quả.
     */
    public static boolean isValidCartDocument(DocumentSnapshot doc) {
        if (doc == null) {
            return false;
        }

        String productId = doc.getString("productId");
        if (productId == null || productId.isEmpty()) {
            return false;
        }

        String name = doc.getString("name");
        if (name == null || name.isEmpty()) {
            Object productObj = doc.get("product");
            if (productObj instanceof java.util.Map) {
                Object productName = ((java.util.Map<?, ?>) productObj).get("name");
                if (productName instanceof String) {
                    name = (String) productName;
                }
            }
        }

        return name != null && !name.isEmpty();
    }
}