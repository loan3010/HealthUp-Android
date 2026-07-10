package com.example.healthup.diet

import kotlin.math.round

object MenuGenerator {

    val MEAL_ORDER = listOf("sang", "trua", "xe", "toi")

    val MEAL_LABELS = mapOf(
        "sang" to "Bữa sáng",
        "trua" to "Bữa trưa",
        "xe" to "Bữa xế",
        "toi" to "Bữa tối"
    )

    fun generateDayMenu(
        data: DietData,
        goalId: String,
        tier: GoalTier,
        dayIndex: Int = 1
    ): DayMenu {
        val split = data.calorieCalculation.mealSplit
        val meals = linkedMapOf<String, ComputedMeal>()
        var actualTotal = 0

        for (mealTime in MEAL_ORDER) {
            val candidates = data.dishLibrary.filter { dish ->
                dish.mealTimes.contains(mealTime) && dish.suitableGoals.contains(goalId)
            }
            val dish = pickDish(candidates, dayIndex)
            val splitRatio = split[mealTime] ?: 0.0
            val targetMealKcal = tier.targetKcal * splitRatio
            val computed = computeServing(dish, targetMealKcal).copy(
                mealLabel = MEAL_LABELS[mealTime] ?: mealTime
            )
            meals[mealTime] = computed
            actualTotal += computed.totalKcal
        }

        return DayMenu(
            goalId = goalId,
            tierId = tier.id,
            day = dayIndex,
            targetKcalDay = tier.targetKcal,
            actualKcalDay = actualTotal,
            meals = meals
        )
    }

    fun findSampleMenu(data: DietData, goalId: String, tierId: String, day: Int): DayMenu? {
        val sample = data.sampleMenus.find {
            it.goalId == goalId && it.tierId == tierId && it.day == day
        } ?: return null

        val labeledMeals = sample.meals.mapValues { (key, meal) ->
            meal.copy(mealLabel = MEAL_LABELS[key] ?: key)
        }

        return DayMenu(
            goalId = sample.goalId,
            tierId = sample.tierId,
            day = sample.day,
            targetKcalDay = sample.targetKcalDay,
            actualKcalDay = sample.actualKcalDay,
            meals = labeledMeals
        )
    }

    private fun pickDish(candidates: List<Dish>, dayIndex: Int): Dish {
        require(candidates.isNotEmpty()) { "No suitable dish found" }
        return candidates[(dayIndex - 1) % candidates.size]
    }

    fun swapMeal(
        data: DietData,
        goalId: String,
        tier: GoalTier,
        mealTime: String,
        currentMenu: DayMenu,
        swapOffset: Int = 1
    ): DayMenu {
        val candidates = data.dishLibrary.filter { dish ->
            dish.mealTimes.contains(mealTime) && dish.suitableGoals.contains(goalId)
        }
        val currentDishId = currentMenu.meals[mealTime]?.dishId
        val currentIndex = candidates.indexOfFirst { it.id == currentDishId }.coerceAtLeast(0)
        val nextIndex = if (candidates.size <= 1) {
            currentIndex
        } else {
            (currentIndex + swapOffset) % candidates.size
        }
        val dish = candidates[nextIndex]
        val splitRatio = data.calorieCalculation.mealSplit[mealTime] ?: 0.0
        val targetMealKcal = tier.targetKcal * splitRatio
        val computed = computeServing(dish, targetMealKcal).copy(
            mealLabel = MEAL_LABELS[mealTime] ?: mealTime
        )
        val updatedMeals = currentMenu.meals.toMutableMap()
        updatedMeals[mealTime] = computed
        val actualTotal = updatedMeals.values.sumOf { it.totalKcal }
        return currentMenu.copy(
            meals = updatedMeals,
            actualKcalDay = actualTotal
        )
    }

    fun computeServing(dish: Dish, targetKcal: Double): ComputedMeal {
        val hp = dish.healthupProduct
        val remaining = maxOf(targetKcal - dish.otherKcal, 0.0)
        var gram = if (hp.kcalPer100g <= 0) 0.0 else (remaining / hp.kcalPer100g) * 100
        gram = gram.coerceIn(
            dish.servingRange.minGram.toDouble(),
            dish.servingRange.maxGram.toDouble()
        )
        val productKcal = round((gram / 100) * hp.kcalPer100g).toInt()
        return ComputedMeal(
            dishId = dish.id,
            dishName = dish.name,
            productId = hp.productId,
            productName = hp.name,
            productGram = round(gram).toInt(),
            productKcal = productKcal,
            otherIngredients = dish.otherIngredients,
            otherKcal = dish.otherKcal,
            totalKcal = dish.otherKcal + productKcal,
            targetKcal = round(targetKcal).toInt(),
            tip = dish.tip
        )
    }
}
