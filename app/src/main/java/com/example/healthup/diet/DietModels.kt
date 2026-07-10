package com.example.healthup.diet

data class DietData(
    val meta: DietMeta,
    val bmi: BmiSection,
    val calorieCalculation: CalorieCalculation,
    val goals: List<DietGoal>,
    val dishLibrary: List<Dish>,
    val sampleMenus: List<SampleMenu>
)

data class DietMeta(
    val disclaimer: String
)

data class BmiSection(
    val classification: List<BmiClassification>
)

data class BmiClassification(
    val id: String,
    val label: String,
    val min: Double,
    val max: Double,
    val suggestedGoal: String,
    val advice: String
)

data class CalorieCalculation(
    val activityLevels: List<ActivityLevel>,
    val goalAdjustment: List<GoalAdjustment>,
    val mealSplit: Map<String, Double>
)

data class ActivityLevel(
    val id: String,
    val label: String,
    val factor: Double
)

data class GoalAdjustment(
    val goalId: String,
    val label: String,
    val adjustPercentMin: Double,
    val adjustPercentMax: Double,
    val note: String
)

data class DietGoal(
    val id: String,
    val name: String,
    val description: String,
    val tiers: List<GoalTier>
)

data class GoalTier(
    val id: String,
    val label: String,
    val kcalRange: List<Int>,
    val targetKcal: Int
)

data class Dish(
    val id: String,
    val name: String,
    val mealTimes: List<String>,
    val suitableGoals: List<String>,
    val description: String,
    val otherIngredients: List<IngredientItem>,
    val otherKcal: Int,
    val healthupProduct: HealthupProductRef,
    val servingRange: ServingRange,
    val tip: String
)

data class IngredientItem(
    val name: String,
    val kcal: Int
)

data class HealthupProductRef(
    val productId: String,
    val name: String,
    val kcalPer100g: Double
)

data class ServingRange(
    val minGram: Int,
    val maxGram: Int
)

data class SampleMenu(
    val goalId: String,
    val tierId: String,
    val day: Int,
    val targetKcalDay: Int,
    val actualKcalDay: Int,
    val meals: Map<String, ComputedMeal>
)

data class ComputedMeal(
    val dishId: String,
    val dishName: String,
    val productId: String,
    val productName: String,
    val productGram: Int,
    val productKcal: Int,
    val otherIngredients: List<IngredientItem>,
    val otherKcal: Int,
    val totalKcal: Int,
    val targetKcal: Int,
    val tip: String,
    val mealLabel: String = ""
)

data class DayMenu(
    val goalId: String,
    val tierId: String,
    val day: Int,
    val targetKcalDay: Int,
    val actualKcalDay: Int,
    val meals: Map<String, ComputedMeal>
)

enum class Gender { MALE, FEMALE }

data class UserDietInput(
    val gender: Gender,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activityId: String,
    val selectedGoalId: String? = null
)

data class DietCalculationResult(
    val bmi: Double,
    val bmiClassification: BmiClassification,
    val bmr: Double,
    val tdee: Double,
    val computedTargetKcal: Double,
    val selectedGoal: DietGoal,
    val selectedTier: GoalTier,
    val suggestedGoalId: String,
    val showDoctorWarning: Boolean
)
