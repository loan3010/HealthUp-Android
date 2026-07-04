package com.example.healthup.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.R
import com.example.healthup.databinding.ItemAboutValueBinding

/** White value cards: icon + title + description (2-column grid). */
class ValuesAdapter(private val items: List<ValueItem>) :
    RecyclerView.Adapter<ValuesAdapter.ValueViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
        val binding = ItemAboutValueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ValueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ValueViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class ValueViewHolder(private val binding: ItemAboutValueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ValueItem) {
            binding.valueIcon.setImageResource(iconResFor(item.icon))
            binding.valueTitle.text = item.title
            binding.valueDescription.text = item.description
        }

        private fun iconResFor(icon: String): Int = when (icon) {
            "leaf" -> R.drawable.ic_leaf
            "verified" -> R.drawable.ic_verified
            "eco" -> R.drawable.ic_eco
            "schedule" -> R.drawable.ic_schedule
            else -> R.drawable.ic_leaf
        }
    }
}
