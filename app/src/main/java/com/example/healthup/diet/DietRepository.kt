package com.example.healthup.diet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class DietRepository(private val context: Context) {

    fun loadDietData(): DietData? {
        val raw = readAsset(ASSET_FILE) ?: return null
        return try {
            parse(JSONObject(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $ASSET_FILE", e)
            null
        }
    }

    private fun readAsset(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read asset $name", e)
        null
    }

    private fun parse(json: JSONObject): DietData {
        val metaJson = json.getJSONObject("meta")
        val meta = DietMeta(disclaimer = metaJson.optString("disclaimer"))

        val bmiJson = json.getJSONObject("bmi")
        val classifications = bmiJson.optJSONArray("classification").mapObjects {
            BmiClassification(
                id = it.optString("id"),
                label = it.optString("label"),
                min = it.optDouble("min"),
                max = it.optDouble("max"),
                suggestedGoal = it.optString("suggestedGoal"),
                advice = it.optString("advice")
            )
        }
        val bmi = BmiSection(classification = classifications)

        val calJson = json.getJSONObject("calorieCalculation")
        val activityLevels = calJson.optJSONArray("activityLevels").mapObjects {
            ActivityLevel(
                id = it.optString("id"),
                label = it.optString("label"),
                factor = it.optDouble("factor")
            )
        }
        val goalAdjustments = calJson.optJSONArray("goalAdjustment").mapObjects {
            GoalAdjustment(
                goalId = it.optString("goalId"),
                label = it.optString("label"),
                adjustPercentMin = it.optDouble("adjustPercentMin"),
                adjustPercentMax = it.optDouble("adjustPercentMax"),
                note = it.optString("note")
            )
        }
        val mealSplitJson = calJson.getJSONObject("mealSplit")
        val mealSplit = linkedMapOf<String, Double>()
        mealSplitJson.keys().forEach { key ->
            mealSplit[key] = mealSplitJson.optDouble(key)
        }
        val calorieCalculation = CalorieCalculation(
            activityLevels = activityLevels,
            goalAdjustment = goalAdjustments,
            mealSplit = mealSplit
        )

        val goals = json.optJSONArray("goals").mapObjects { goalJson ->
            DietGoal(
                id = goalJson.optString("id"),
                name = goalJson.optString("name"),
                description = goalJson.optString("description"),
                tiers = goalJson.optJSONArray("tiers").mapObjects { tierJson ->
                    GoalTier(
                        id = tierJson.optString("id"),
                        label = tierJson.optString("label"),
                        kcalRange = tierJson.optJSONArray("kcalRange").toIntList(),
                        targetKcal = tierJson.optInt("targetKcal")
                    )
                }
            )
        }

        val dishLibrary = json.optJSONArray("dishLibrary").mapObjects { dishJson ->
            parseDish(dishJson)
        }

        val sampleMenus = json.optJSONArray("sampleMenus").mapObjects { menuJson ->
            val mealsJson = menuJson.getJSONObject("meals")
            val meals = linkedMapOf<String, ComputedMeal>()
            mealsJson.keys().forEach { mealKey ->
                meals[mealKey] = parseComputedMeal(mealsJson.getJSONObject(mealKey))
            }
            SampleMenu(
                goalId = menuJson.optString("goalId"),
                tierId = menuJson.optString("tierId"),
                day = menuJson.optInt("day"),
                targetKcalDay = menuJson.optInt("targetKcalDay"),
                actualKcalDay = menuJson.optInt("actualKcalDay"),
                meals = meals
            )
        }

        return DietData(meta, bmi, calorieCalculation, goals, dishLibrary, sampleMenus)
    }

    private fun parseDish(dishJson: JSONObject): Dish {
        val otherIngredients = dishJson.optJSONArray("otherIngredients").mapObjects {
            IngredientItem(name = it.optString("name"), kcal = it.optInt("kcal"))
        }
        val hpJson = dishJson.getJSONObject("healthupProduct")
        val rangeJson = dishJson.getJSONObject("servingRange")
        return Dish(
            id = dishJson.optString("id"),
            name = dishJson.optString("name"),
            mealTimes = dishJson.optJSONArray("mealTimes").toStringList(),
            suitableGoals = dishJson.optJSONArray("suitableGoals").toStringList(),
            description = dishJson.optString("description"),
            otherIngredients = otherIngredients,
            otherKcal = dishJson.optInt("otherKcal"),
            healthupProduct = HealthupProductRef(
                productId = hpJson.optString("productId"),
                name = hpJson.optString("name"),
                kcalPer100g = hpJson.optDouble("kcalPer100g")
            ),
            servingRange = ServingRange(
                minGram = rangeJson.optInt("minGram"),
                maxGram = rangeJson.optInt("maxGram")
            ),
            tip = dishJson.optString("tip")
        )
    }

    private fun parseComputedMeal(json: JSONObject): ComputedMeal {
        val otherIngredients = json.optJSONArray("otherIngredients")?.mapObjects {
            IngredientItem(name = it.optString("name"), kcal = it.optInt("kcal"))
        } ?: emptyList()
        return ComputedMeal(
            dishId = json.optString("dishId"),
            dishName = json.optString("dishName"),
            productId = json.optString("productId"),
            productName = json.optString("productName"),
            productGram = json.optInt("productGram"),
            productKcal = json.optInt("productKcal"),
            otherIngredients = otherIngredients,
            otherKcal = json.optInt("otherKcal"),
            totalKcal = json.optInt("totalKcal"),
            targetKcal = json.optInt("targetKcal"),
            tip = json.optString("tip")
        )
    }

    companion object {
        private const val TAG = "DietRepository"
        private const val ASSET_FILE = "healthup_diet_data.json"
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it) }
}

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val result = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { result.add(transform(it)) }
    }
    return result
}
