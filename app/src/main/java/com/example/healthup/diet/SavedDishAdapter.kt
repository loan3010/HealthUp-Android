package com.example.healthup.diet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.R

class SavedDishAdapter(
    private val onItemClick: (SavedDishSummary) -> Unit,
    private val onDeleteClick: (SavedDishSummary) -> Unit
) : RecyclerView.Adapter<SavedDishAdapter.VH>() {

    private val allItems = mutableListOf<SavedDishSummary>()
    private val visibleItems = mutableListOf<SavedDishSummary>()
    private var query: String = ""

    fun submit(list: List<SavedDishSummary>) {
        allItems.clear()
        allItems.addAll(list)
        applyFilter(query)
    }

    fun filter(query: String) {
        this.query = query.trim()
        applyFilter(this.query)
    }

    fun removeItem(id: String) {
        allItems.removeAll { it.id == id }
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        visibleItems.clear()
        if (query.isBlank()) {
            visibleItems.addAll(allItems)
        } else {
            val lower = query.lowercase()
            visibleItems.addAll(
                allItems.filter { item ->
                    item.dishName.lowercase().contains(lower)
                        || item.mealLabel.lowercase().contains(lower)
                        || item.productName.lowercase().contains(lower)
                }
            )
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diet_saved_menu, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(visibleItems[position], onItemClick, onDeleteClick)
    }

    override fun getItemCount(): Int = visibleItems.size

    fun isEmpty(): Boolean = allItems.isEmpty()

    fun getItemAt(position: Int): SavedDishSummary? = visibleItems.getOrNull(position)

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMealBadge: TextView = itemView.findViewById(R.id.tvSavedMealBadge)
        private val tvDishName: TextView = itemView.findViewById(R.id.tvSavedDishName)
        private val tvKcal: TextView = itemView.findViewById(R.id.tvSavedKcal)
        private val tvDate: TextView = itemView.findViewById(R.id.tvSavedDate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteSavedDish)

        fun bind(
            item: SavedDishSummary,
            onItemClick: (SavedDishSummary) -> Unit,
            onDeleteClick: (SavedDishSummary) -> Unit
        ) {
            tvMealBadge.text = item.mealLabel
            tvDishName.text = item.dishName
            tvKcal.text = itemView.context.getString(R.string.diet_saved_dish_kcal, item.totalKcal)
            tvDate.text = SavedDishRepository.formatSavedDate(item.savedAtMillis)
            itemView.setOnClickListener { onItemClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
