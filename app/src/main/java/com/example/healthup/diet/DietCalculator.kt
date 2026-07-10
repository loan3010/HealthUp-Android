package com.example.healthup.diet

import kotlin.math.abs
import kotlin.math.round

object DietCalculator {

    fun calculateBmi(weightKg: Double, heightCm: Double): Double {
        val heightM = heightCm / 100.0
        if (heightM <= 0) return 0.0
        return weightKg / (heightM * heightM)
    }

    fun classifyBmi(bmi: Double, classifications: List<BmiClassification>): BmiClassification? {
        return classifications.find { bmi >= it.min && bmi <= it.max }
    }

    fun calculateBmr(gender: Gender, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (gender) {
            Gender.MALE -> base + 5
            Gender.FEMALE -> base - 161
        }
    }

    fun calculateTdee(bmr: Double, activityFactor: Double): Double = bmr * activityFactor

    fun calculateTargetKcal(tdee: Double, adjustment: GoalAdjustment): Double {
        val avgPercent = (adjustment.adjustPercentMin + adjustment.adjustPercentMax) / 2.0 / 100.0
        return tdee * (1 + avgPercent)
    }

    fun findClosestTier(goal: DietGoal, targetKcal: Double): GoalTier {
        return goal.tiers.minByOrNull { abs(it.targetKcal - targetKcal) }
            ?: goal.tiers.first()
    }

    fun calculate(
        data: DietData,
        input: UserDietInput,
        goalIdOverride: String? = null
    ): DietCalculationResult? {
        val bmi = calculateBmi(input.weightKg, input.heightCm)
        val bmiInfo = classifyBmi(bmi, data.bmi.classification) ?: return null

        val activity = data.calorieCalculation.activityLevels.find { it.id == input.activityId }
            ?: data.calorieCalculation.activityLevels.first()

        val bmr = calculateBmr(input.gender, input.weightKg, input.heightCm, input.age)
        val tdee = calculateTdee(bmr, activity.factor)

        val goalId = goalIdOverride ?: input.selectedGoalId ?: bmiInfo.suggestedGoal
        val goal = data.goals.find { it.id == goalId } ?: return null

        val adjustment = data.calorieCalculation.goalAdjustment.find { it.goalId == goalId }
            ?: return null

        val targetKcal = calculateTargetKcal(tdee, adjustment)
        val tier = findClosestTier(goal, targetKcal)

        return DietCalculationResult(
            bmi = bmi,
            bmiClassification = bmiInfo,
            bmr = bmr,
            tdee = tdee,
            computedTargetKcal = targetKcal,
            selectedGoal = goal,
            selectedTier = tier,
            suggestedGoalId = bmiInfo.suggestedGoal,
            showDoctorWarning = bmi >= 30 || input.age < 18
        )
    }
}
