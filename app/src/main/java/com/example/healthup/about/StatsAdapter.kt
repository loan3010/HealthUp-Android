package com.example.healthup.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.databinding.ItemAboutStatBinding

/** Statistic cards: large green number + small label (2-column grid). */
class StatsAdapter(private val items: List<StatItem>) :
    RecyclerView.Adapter<StatsAdapter.StatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
        val binding = ItemAboutStatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class StatViewHolder(private val binding: ItemAboutStatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StatItem) {
            binding.statNumber.text = item.number
            binding.statLabel.text = item.label
        }
    }
}
