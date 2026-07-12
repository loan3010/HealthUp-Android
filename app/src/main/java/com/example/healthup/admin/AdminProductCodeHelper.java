package com.example.healthup.admin;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.Locale;

public final class AdminProductCodeHelper {

    private static final String META_COLLECTION = "meta";
    private static final String PRODUCT_CODE_DOC = "productCodes";
    private static final String FIELD_NEXT = "next";

    private AdminProductCodeHelper() {
    }

    @NonNull
    public static String formatCode(long sequence) {
        return String.format(Locale.US, "HEALTHUP-%04d", sequence);
    }

    public static Task<String> assignNextCode(@NonNull FirebaseFirestore db) {
        DocumentReference ref = db.collection(META_COLLECTION).document(PRODUCT_CODE_DOC);
        return db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            long next = 1L;
            if (snap.exists()) {
                Long stored = snap.getLong(FIELD_NEXT);
                if (stored != null && stored > 0) {
                    next = stored;
                }
            }
            transaction.set(ref, java.util.Collections.singletonMap(FIELD_NEXT, next + 1));
            return formatCode(next);
        });
    }

    public static Task<String> ensureProductCode(@NonNull FirebaseFirestore db,
                                                 @NonNull String existingCode) {
        if (existingCode != null && !existingCode.trim().isEmpty()) {
            return Tasks.forResult(existingCode.trim());
        }
        return assignNextCode(db);
    }
}
