package com.example.healthup.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.databinding.ItemAboutGalleryBinding

/**
 * Gallery images (2-column grid). Each item tries to load its asset by name and
 * gracefully shows a rounded placeholder when the image is unavailable.
 */
class GalleryAdapter(private val imageNames: List<String>) :
    RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemAboutGalleryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) =
        holder.bind(imageNames[position])

    override fun getItemCount(): Int = imageNames.size

    class GalleryViewHolder(private val binding: ItemAboutGalleryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imageName: String) {
            AssetImageLoader.load(binding.galleryImage, binding.galleryPlaceholder, imageName)
        }
    }
}
