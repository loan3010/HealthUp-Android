package com.example.healthup.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.databinding.ItemAboutQualityBinding

/** Green quality cards with white text: title + description (2-column grid). */
class QualitiesAdapter(private val items: List<QualityItem>) :
    RecyclerView.Adapter<QualitiesAdapter.QualityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QualityViewHolder {
        val binding = ItemAboutQualityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QualityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QualityViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class QualityViewHolder(private val binding: ItemAboutQualityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QualityItem) {
            binding.qualityTitle.text = item.title
            binding.qualityDescription.text = item.description
        }
    }
}
