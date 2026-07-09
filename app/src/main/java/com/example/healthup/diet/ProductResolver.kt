package com.example.healthup.diet

import com.example.healthup.firebase.FirestoreManager
import com.example.models.Product

object ProductResolver {

    fun resolveProduct(
        exportProductId: String,
        productName: String,
        onResult: (Product?) -> Unit
    ) {
        FirestoreManager.getInstance().getProductsCollection().get()
            .addOnSuccessListener { snapshots ->
                val products = snapshots.documents.mapNotNull { doc ->
                    Product.fromDocument(doc)?.also { it.id = doc.id }
                }
                val id = findMatch(products, exportProductId, productName)
                onResult(products.find { it.id == id })
            }
            .addOnFailureListener { onResult(null) }
    }

    fun resolveProductId(
        exportProductId: String,
        productName: String,
        onResult: (String?) -> Unit
    ) {
        resolveProduct(exportProductId, productName) { product ->
            onResult(product?.id)
        }
    }

    fun findMatch(
        products: List<Product>,
        exportProductId: String,
        productName: String
    ): String? {
        products.find { it.id == exportProductId }?.let { return it.id }

        val normalizedName = normalize(productName)
        products.find { normalize(it.name).contains(normalizedName) || normalizedName.contains(normalize(it.name)) }
            ?.let { return it.id }

        val firstWord = normalizedName.split(" ").firstOrNull() ?: return null
        if (firstWord.length >= 3) {
            products.find { normalize(it.name).contains(firstWord) }?.let { return it.id }
        }
        return null
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ\\s]"), "")
            .trim()
    }
}
