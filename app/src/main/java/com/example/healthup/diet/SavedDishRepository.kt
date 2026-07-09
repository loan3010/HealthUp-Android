package com.example.healthup.diet

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SavedDishSummary(
    val id: String,
    val dishId: String,
    val dishName: String,
    val mealLabel: String,
    val productName: String,
    val totalKcal: Int,
    val savedAtMillis: Long
)

data class SavedDish(
    val id: String,
    val dishId: String,
    val dishName: String,
    val mealLabel: String,
    val productId: String,
    val productName: String,
    val productGram: Int,
    val productKcal: Int,
    val otherIngredients: List<IngredientItem>,
    val totalKcal: Int,
    val tip: String,
    val goalId: String,
    val savedAtMillis: Long
) {
    fun toComputedMeal(): ComputedMeal = ComputedMeal(
        dishId = dishId,
        dishName = dishName,
        productId = productId,
        productName = productName,
        productGram = productGram,
        productKcal = productKcal,
        otherIngredients = otherIngredients,
        otherKcal = otherIngredients.sumOf { it.kcal },
        totalKcal = totalKcal,
        targetKcal = totalKcal,
        tip = tip,
        mealLabel = mealLabel
    )
}

class SavedDishRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun collection(uid: String) = firestore.collection("users")
        .document(uid)
        .collection("savedDishes")

    private fun dayKey(millis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    private fun docIdForDay(dishId: String, millis: Long = System.currentTimeMillis()): String {
        return "${dishId}_${dayKey(millis)}"
    }

    fun isSavedToday(
        uid: String,
        dishId: String,
        onResult: (Boolean) -> Unit
    ) {
        collection(uid)
            .document(docIdForDay(dishId))
            .get()
            .addOnSuccessListener { doc -> onResult(doc.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun loadSavedDishIdsToday(
        uid: String,
        onSuccess: (Set<String>) -> Unit,
        onError: () -> Unit = {}
    ) {
        val todayKey = dayKey()
        collection(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.documents.mapNotNull { doc ->
                    val key = doc.getString("dayKey")
                    if (key == todayKey) doc.getString("dishId") else null
                }.toSet()
                onSuccess(ids)
            }
            .addOnFailureListener { onError() }
    }

    fun save(
        uid: String,
        meal: ComputedMeal,
        goalId: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val docId = docIdForDay(meal.dishId, now)
        val docRef = collection(uid).document(docId)

        docRef.get()
            .addOnSuccessListener { existing ->
                if (existing.exists()) {
                    onSuccess(false)
                    return@addOnSuccessListener
                }
                val payload = mealToPayload(meal, goalId, now)
                docRef.set(payload)
                    .addOnSuccessListener { onSuccess(true) }
                    .addOnFailureListener { onError(it.localizedMessage ?: "Save failed") }
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Save failed") }
    }

    fun list(
        uid: String,
        onSuccess: (List<SavedDishSummary>) -> Unit,
        onError: (String) -> Unit
    ) {
        collection(uid)
            .orderBy("savedAtMillis", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { doc -> summaryFromDoc(doc.id, doc.data) }
                onSuccess(items)
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Load failed") }
    }

    fun load(
        uid: String,
        dishDocId: String,
        onSuccess: (SavedDish) -> Unit,
        onError: (String) -> Unit
    ) {
        collection(uid)
            .document(dishDocId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onError("not_found")
                    return@addOnSuccessListener
                }
                val data = doc.data
                if (data == null) {
                    onError("invalid_data")
                    return@addOnSuccessListener
                }
                try {
                    onSuccess(savedDishFromData(doc.id, data))
                } catch (_: Exception) {
                    onError("invalid_data")
                }
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "Load failed") }
    }

    fun delete(
        uid: String,
        dishDocId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        collection(uid)
            .document(dishDocId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Delete failed") }
    }

    private fun mealToPayload(meal: ComputedMeal, goalId: String, savedAtMillis: Long): Map<String, Any> {
        val ingredients = meal.otherIngredients.map { mapOf("name" to it.name, "kcal" to it.kcal) }
        return hashMapOf(
            "dishId" to meal.dishId,
            "dishName" to meal.dishName,
            "mealLabel" to meal.mealLabel,
            "productId" to meal.productId,
            "productName" to meal.productName,
            "productGram" to meal.productGram,
            "productKcal" to meal.productKcal,
            "otherIngredients" to ingredients,
            "totalKcal" to meal.totalKcal,
            "tip" to meal.tip,
            "goalId" to goalId,
            "savedAtMillis" to savedAtMillis,
            "dayKey" to dayKey(savedAtMillis)
        )
    }

    private fun summaryFromDoc(id: String, data: Map<String, Any>?): SavedDishSummary? {
        if (data == null) return null
        val savedAt = (data["savedAtMillis"] as? Number)?.toLong() ?: return null
        return SavedDishSummary(
            id = id,
            dishId = data["dishId"] as? String ?: return null,
            dishName = data["dishName"] as? String ?: return null,
            mealLabel = data["mealLabel"] as? String ?: "",
            productName = data["productName"] as? String ?: "",
            totalKcal = (data["totalKcal"] as? Number)?.toInt() ?: 0,
            savedAtMillis = savedAt
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun savedDishFromData(id: String, data: Map<String, Any>): SavedDish {
        val ingredientsRaw = data["otherIngredients"] as? List<Map<String, Any>> ?: emptyList()
        val ingredients = ingredientsRaw.map { item ->
            IngredientItem(
                name = item["name"] as? String ?: "",
                kcal = (item["kcal"] as? Number)?.toInt() ?: 0
            )
        }
        return SavedDish(
            id = id,
            dishId = data["dishId"] as String,
            dishName = data["dishName"] as String,
            mealLabel = data["mealLabel"] as? String ?: "",
            productId = data["productId"] as? String ?: "",
            productName = data["productName"] as? String ?: "",
            productGram = (data["productGram"] as? Number)?.toInt() ?: 0,
            productKcal = (data["productKcal"] as? Number)?.toInt() ?: 0,
            otherIngredients = ingredients,
            totalKcal = (data["totalKcal"] as? Number)?.toInt() ?: 0,
            tip = data["tip"] as? String ?: "",
            goalId = data["goalId"] as? String ?: "",
            savedAtMillis = (data["savedAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    companion object {
        fun formatSavedDate(millis: Long): String {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return formatter.format(Date(millis))
        }
    }

    fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
