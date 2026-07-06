package com.example.healthup;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.healthup.firebase.FirestoreManager;
import com.example.models.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-user wishlist stored at users/{uid}/wishlist/{productId}. */
public final class WishlistManager {

    private static final String COL_USERS = "users";
    private static final String COL_WISHLIST = "wishlist";

    public interface SimpleCallback {
        void onComplete(boolean success);
    }

    public interface ProductsCallback {
        void onLoaded(@NonNull List<Product> products);
    }

    public interface IdsCallback {
        void onLoaded(@NonNull Set<String> productIds);
    }

    private WishlistManager() {
    }

    @Nullable
    public static String currentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public static void toggle(@NonNull Context context, @NonNull Product product,
                              @Nullable SimpleCallback callback) {
        String uid = currentUserId();
        if (uid == null) {
            Toast.makeText(context, "Vui lòng đăng nhập để sử dụng chức năng yêu thích",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (product.getId() == null) {
            return;
        }

        boolean add = !product.isFavorite();
        product.setFavorite(add);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (add) {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("productId", product.getId());
            data.put("addedAt", FieldValue.serverTimestamp());
            db.collection(COL_USERS).document(uid)
                    .collection(COL_WISHLIST).document(product.getId())
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(context, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(true);
                    })
                    .addOnFailureListener(e -> {
                        product.setFavorite(!add);
                        Toast.makeText(context, "Không thể cập nhật yêu thích", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false);
                    });
        } else {
            db.collection(COL_USERS).document(uid)
                    .collection(COL_WISHLIST).document(product.getId())
                    .delete()
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(context, "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(true);
                    })
                    .addOnFailureListener(e -> {
                        product.setFavorite(!add);
                        Toast.makeText(context, "Không thể cập nhật yêu thích", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false);
                    });
        }
    }

    public static void loadFavoriteIds(@NonNull String uid, @NonNull IdsCallback callback) {
        FirebaseFirestore.getInstance()
                .collection(COL_USERS).document(uid)
                .collection(COL_WISHLIST)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> ids = new HashSet<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        ids.add(doc.getId());
                    }
                    callback.onLoaded(ids);
                })
                .addOnFailureListener(e -> callback.onLoaded(new HashSet<>()));
    }

    public static void loadWishlistProducts(@NonNull String uid, @NonNull ProductsCallback callback) {
        FirebaseFirestore.getInstance()
                .collection(COL_USERS).document(uid)
                .collection(COL_WISHLIST)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        ids.add(doc.getId());
                    }
                    if (ids.isEmpty()) {
                        callback.onLoaded(new ArrayList<>());
                        return;
                    }
                    fetchProductsByIds(ids, callback);
                })
                .addOnFailureListener(e -> callback.onLoaded(new ArrayList<>()));
    }

    private static void fetchProductsByIds(@NonNull List<String> ids,
                                           @NonNull ProductsCallback callback) {
        List<Product> loaded = new ArrayList<>();
        final int[] pending = {ids.size()};
        for (String id : ids) {
            FirestoreManager.getInstance().getProductsCollection().document(id)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Product product = doc.toObject(Product.class);
                            if (product != null) {
                                product.setId(doc.getId());
                                product.setFavorite(true);
                                loaded.add(product);
                            }
                        }
                        if (--pending[0] == 0) {
                            callback.onLoaded(loaded);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (--pending[0] == 0) {
                            callback.onLoaded(loaded);
                        }
                    });
        }
    }

    public static void applyFavoriteState(@NonNull List<Product> products,
                                          @NonNull Set<String> favoriteIds) {
        for (Product product : products) {
            if (product.getId() != null) {
                product.setFavorite(favoriteIds.contains(product.getId()));
            }
        }
    }
}
