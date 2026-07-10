package com.example.healthup.diet

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.healthup.R
import com.example.models.Product

object DietProductImageLoader {

    fun load(imageView: ImageView, product: Product?) {
        val imagePath = product?.imageUrl
        if (imagePath.isNullOrBlank()) {
            imageView.setImageResource(R.color.neutral_light_grey)
            return
        }
        val cleanPath = if (imagePath.startsWith("/")) imagePath.substring(1) else imagePath
        val loadTarget = when {
            cleanPath.startsWith("images/") -> "file:///android_asset/$cleanPath"
            imagePath.startsWith("http") -> imagePath
            else -> "file:///android_asset/images/products/$cleanPath"
        }
        Glide.with(imageView.context)
            .load(loadTarget)
            .placeholder(R.color.neutral_light_grey)
            .centerCrop()
            .into(imageView)
    }

    fun loadForMeal(imageView: ImageView, exportProductId: String, productName: String) {
        ProductResolver.resolveProduct(exportProductId, productName) { product ->
            load(imageView, product)
        }
    }
}
