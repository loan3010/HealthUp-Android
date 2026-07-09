package com.example.healthup.diet

import org.json.JSONArray
import org.json.JSONObject

object MealPlanSerializer {

    fun dayMenuToJson(menu: DayMenu): JSONObject = JSONObject().apply {
        put("goalId", menu.goalId)
        put("tierId", menu.tierId)
        put("day", menu.day)
        put("targetKcalDay", menu.targetKcalDay)
        put("actualKcalDay", menu.actualKcalDay)
        put("meals", mealsToJson(menu.meals))
    }

    fun dayMenuFromJson(json: JSONObject): DayMenu {
        val mealsJson = json.getJSONObject("meals")
        val meals = linkedMapOf<String, ComputedMeal>()
        for (mealTime in MenuGenerator.MEAL_ORDER) {
            if (mealsJson.has(mealTime)) {
                meals[mealTime] = computedMealFromJson(mealsJson.getJSONObject(mealTime))
            }
        }
        return DayMenu(
            goalId = json.getString("goalId"),
            tierId = json.getString("tierId"),
            day = json.optInt("day", 1),
            targetKcalDay = json.getInt("targetKcalDay"),
            actualKcalDay = json.getInt("actualKcalDay"),
            meals = meals
        )
    }

    private fun mealsToJson(meals: Map<String, ComputedMeal>): JSONObject {
        val obj = JSONObject()
        meals.forEach { (time, meal) -> obj.put(time, computedMealToJson(meal)) }
        return obj
    }

    private fun computedMealToJson(meal: ComputedMeal): JSONObject = JSONObject().apply {
        put("dishId", meal.dishId)
        put("dishName", meal.dishName)
        put("productId", meal.productId)
        put("productName", meal.productName)
        put("productGram", meal.productGram)
        put("productKcal", meal.productKcal)
        put("otherIngredients", ingredientsToJson(meal.otherIngredients))
        put("otherKcal", meal.otherKcal)
        put("totalKcal", meal.totalKcal)
        put("targetKcal", meal.targetKcal)
        put("tip", meal.tip)
        put("mealLabel", meal.mealLabel)
    }

    private fun computedMealFromJson(json: JSONObject): ComputedMeal = ComputedMeal(
        dishId = json.getString("dishId"),
        dishName = json.getString("dishName"),
        productId = json.getString("productId"),
        productName = json.getString("productName"),
        productGram = json.getInt("productGram"),
        productKcal = json.getInt("productKcal"),
        otherIngredients = ingredientsFromJson(json.optJSONArray("otherIngredients")),
        otherKcal = json.optInt("otherKcal"),
        totalKcal = json.getInt("totalKcal"),
        targetKcal = json.getInt("targetKcal"),
        tip = json.optString("tip", ""),
        mealLabel = json.optString("mealLabel", "")
    )

    private fun ingredientsToJson(items: List<IngredientItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("kcal", item.kcal)
            })
        }
        return arr
    }

    private fun ingredientsFromJson(arr: JSONArray?): List<IngredientItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            IngredientItem(name = obj.getString("name"), kcal = obj.getInt("kcal"))
        }
    }
}
