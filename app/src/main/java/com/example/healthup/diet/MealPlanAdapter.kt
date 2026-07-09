package com.example.healthup.diet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.R
import com.google.android.material.button.MaterialButton

class MealPlanAdapter(
    private val onProductClick: (ComputedMeal) -> Unit,
    private val onSwapMeal: (ComputedMeal, Int) -> Unit,
    private val onSaveDish: (ComputedMeal) -> Unit = {}
) : RecyclerView.Adapter<MealPlanAdapter.MealViewHolder>() {

    private val items = mutableListOf<ComputedMeal>()
    private val mealTimes = mutableListOf<String>()
    private var savedDishIdsToday: Set<String> = emptySet()
    private var readOnly: Boolean = false

    fun submit(
        meals: List<ComputedMeal>,
        times: List<String> = emptyList(),
        savedDishIdsToday: Set<String> = emptySet(),
        readOnly: Boolean = false
    ) {
        items.clear()
        items.addAll(meals)
        mealTimes.clear()
        mealTimes.addAll(if (times.isNotEmpty()) times else MenuGenerator.MEAL_ORDER)
        this.savedDishIdsToday = savedDishIdsToday
        this.readOnly = readOnly
        notifyDataSetChanged()
    }

    fun markDishSaved(dishId: String) {
        savedDishIdsToday = savedDishIdsToday + dishId
        val index = items.indexOfFirst { it.dishId == dishId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diet_meal, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(
            meal = items[position],
            position = position,
            savedToday = items[position].dishId in savedDishIdsToday,
            readOnly = readOnly,
            onProductClick = onProductClick,
            onSwapMeal = onSwapMeal,
            onSaveDish = onSaveDish
        )
    }

    override fun getItemCount(): Int = items.size

    class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProductImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        private val tvMealLabel: TextView = itemView.findViewById(R.id.tvMealLabel)
        private val tvDishName: TextView = itemView.findViewById(R.id.tvDishName)
        private val tvProductServing: TextView = itemView.findViewById(R.id.tvProductServing)
        private val tvOtherIngredients: TextView = itemView.findViewById(R.id.tvOtherIngredients)
        private val tvKcal: TextView = itemView.findViewById(R.id.tvKcal)
        private val tvTip: TextView = itemView.findViewById(R.id.tvTip)
        private val btnViewProduct: MaterialButton = itemView.findViewById(R.id.btnViewProduct)
        private val btnSwapMeal: MaterialButton = itemView.findViewById(R.id.btnSwapMeal)
        private val btnSaveDish: MaterialButton = itemView.findViewById(R.id.btnSaveDish)

        fun bind(
            meal: ComputedMeal,
            position: Int,
            savedToday: Boolean,
            readOnly: Boolean,
            onProductClick: (ComputedMeal) -> Unit,
            onSwapMeal: (ComputedMeal, Int) -> Unit,
            onSaveDish: (ComputedMeal) -> Unit
        ) {
            tvMealLabel.text = meal.mealLabel
            tvDishName.text = meal.dishName

            if (meal.productGram > 0) {
                tvProductServing.text = itemView.context.getString(
                    R.string.diet_product_serving,
                    meal.productName,
                    meal.productGram,
                    meal.productKcal
                )
                tvProductServing.visibility = View.VISIBLE
                btnViewProduct.visibility = View.VISIBLE
                ivProductImage.visibility = View.VISIBLE
                DietProductImageLoader.loadForMeal(
                    ivProductImage,
                    meal.productId,
                    meal.productName
                )
            } else {
                tvProductServing.visibility = View.GONE
                btnViewProduct.visibility = View.GONE
                ivProductImage.visibility = View.GONE
            }

            if (meal.otherIngredients.isNotEmpty()) {
                val otherText = meal.otherIngredients.joinToString(", ") { ing ->
                    "${ing.name} (${ing.kcal} kcal)"
                }
                tvOtherIngredients.text = itemView.context.getString(
                    R.string.diet_other_ingredients,
                    otherText
                )
                tvOtherIngredients.visibility = View.VISIBLE
            } else {
                tvOtherIngredients.visibility = View.GONE
            }

            tvKcal.text = itemView.context.getString(
                R.string.diet_meal_kcal_compact,
                meal.totalKcal,
                meal.targetKcal
            )

            if (meal.tip.isNotBlank()) {
                tvTip.text = meal.tip
                tvTip.visibility = View.VISIBLE
            } else {
                tvTip.visibility = View.GONE
            }

            btnViewProduct.setOnClickListener { onProductClick(meal) }

            if (readOnly) {
                btnSwapMeal.visibility = View.GONE
                btnSaveDish.visibility = View.GONE
            } else {
                btnSwapMeal.visibility = View.VISIBLE
                btnSwapMeal.setOnClickListener { onSwapMeal(meal, position) }

                btnSaveDish.visibility = View.VISIBLE
                if (savedToday) {
                    btnSaveDish.text = itemView.context.getString(R.string.diet_dish_already_saved)
                    btnSaveDish.isEnabled = false
                    btnSaveDish.icon = null
                } else {
                    btnSaveDish.text = itemView.context.getString(R.string.diet_save_dish)
                    btnSaveDish.isEnabled = true
                    btnSaveDish.setIconResource(R.drawable.ic_check_circle)
                }
                btnSaveDish.setOnClickListener {
                    if (!savedToday) onSaveDish(meal)
                }
            }
        }
    }
}
