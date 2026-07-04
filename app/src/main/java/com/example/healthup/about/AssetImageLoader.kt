package com.example.healthup.about

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView

/**
 * Loads an image from the app's assets folder by file name and shows it, falling
 * back to a placeholder view when the asset is missing or cannot be decoded.
 * This never throws, so a missing image can never crash the screen.
 */
object AssetImageLoader {

    fun load(imageView: ImageView, placeholder: View, assetName: String?) {
        val bitmap = decodeAsset(imageView.context, assetName)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } else {
            imageView.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
        }
    }

    private fun decodeAsset(context: Context, name: String?) =
        if (name.isNullOrBlank()) {
            null
        } else {
            try {
                context.assets.open(name).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
}
