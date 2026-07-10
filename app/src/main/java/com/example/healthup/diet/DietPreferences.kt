package com.example.healthup.diet

import android.content.Context
import org.json.JSONObject

data class DietHistory(
    val savedAtMillis: Long,
    val input: UserDietInput,
    val bmi: Double,
    val bmiLabel: String,
    val bmiClassificationId: String,
    val goalId: String,
    val tierId: String,
    val bmr: Double,
    val tdee: Double,
    val targetKcal: Double,
    val suggestedGoalId: String,
    val showDoctorWarning: Boolean
)

object DietPreferences {

    private const val PREFS_NAME = "diet_prefs"
    private const val KEY_HISTORY = "last_history"

    fun save(context: Context, input: UserDietInput, result: DietCalculationResult) {
        val json = JSONObject().apply {
            put("savedAt", System.currentTimeMillis())
            put("gender", input.gender.name)
            put("age", input.age)
            put("heightCm", input.heightCm)
            put("weightKg", input.weightKg)
            put("activityId", input.activityId)
            put("selectedGoalId", input.selectedGoalId ?: result.selectedGoal.id)
            put("bmi", result.bmi)
            put("bmiLabel", result.bmiClassification.label)
            put("bmiClassificationId", result.bmiClassification.id)
            put("goalId", result.selectedGoal.id)
            put("tierId", result.selectedTier.id)
            put("bmr", result.bmr)
            put("tdee", result.tdee)
            put("targetKcal", result.computedTargetKcal)
            put("suggestedGoalId", result.suggestedGoalId)
            put("showDoctorWarning", result.showDoctorWarning)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, json.toString())
            .apply()
    }

    fun load(context: Context): DietHistory? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val gender = if (json.optString("gender") == Gender.FEMALE.name) {
                Gender.FEMALE
            } else {
                Gender.MALE
            }
            val selectedGoalId = json.optString("selectedGoalId").takeIf { it.isNotBlank() }
            DietHistory(
                savedAtMillis = json.optLong("savedAt"),
                input = UserDietInput(
                    gender = gender,
                    age = json.optInt("age"),
                    heightCm = json.optDouble("heightCm"),
                    weightKg = json.optDouble("weightKg"),
                    activityId = json.optString("activityId"),
                    selectedGoalId = selectedGoalId
                ),
                bmi = json.optDouble("bmi"),
                bmiLabel = json.optString("bmiLabel"),
                bmiClassificationId = json.optString("bmiClassificationId"),
                goalId = json.optString("goalId"),
                tierId = json.optString("tierId"),
                bmr = json.optDouble("bmr"),
                tdee = json.optDouble("tdee"),
                targetKcal = json.optDouble("targetKcal"),
                suggestedGoalId = json.optString("suggestedGoalId"),
                showDoctorWarning = json.optBoolean("showDoctorWarning")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }
}
