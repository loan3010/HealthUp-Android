package com.example.healthup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.example.healthup.BaseAppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.healthup.databinding.ActivityDietResultBinding
import com.example.healthup.diet.DietCalculationResult
import com.example.healthup.diet.DietPreferences
import com.example.healthup.diet.DietViewModel
import com.example.healthup.diet.UserDietInput
import com.google.android.material.chip.Chip
import kotlin.math.roundToInt

class DietResultActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityDietResultBinding
    private lateinit var viewModel: DietViewModel
    private var isUpdatingGoalChips = false
    private var currentInput: UserDietInput? = null
    private var hasPlayedEntrance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDietResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dietToolbar.btnToolbarBack.setOnClickListener { finish() }
        binding.dietToolbar.tvToolbarTitle.setText(R.string.diet_result_screen_title)
        viewModel = ViewModelProvider(this)[DietViewModel::class.java]

        setupGoalChips()
        binding.tvUpdateMeasurements.setOnClickListener {
            startActivity(Intent(this, DietInputActivity::class.java))
        }
        binding.btnViewMenu.setOnClickListener {
            startActivity(Intent(this, DietMenuActivity::class.java))
        }

        viewModel.calculationResult.observe(this) { result ->
            result?.let {
                renderResults(it)
                currentInput?.let { input -> DietPreferences.save(this, input, it) }
                if (!hasPlayedEntrance) {
                    hasPlayedEntrance = true
                    playEntranceAnimations()
                }
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        loadAndRender()
    }

    private fun loadAndRender() {
        val history = DietPreferences.load(this)
        if (history == null) {
            Toast.makeText(this, R.string.diet_data_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentInput = history.input
        viewModel.calculate(history.input, history.goalId)
    }

    private fun renderResults(result: DietCalculationResult) {
        binding.tvBmiValue.text = String.format("%.1f", result.bmi)
        binding.tvBmiBadge.text = result.bmiClassification.label
        binding.tvBmiAdvice.text = result.bmiClassification.advice
        binding.bmiGauge.setBmi(result.bmi, animate = true)

        binding.tvBmrTdee.text = getString(
            R.string.diet_bmr_tdee,
            result.bmr.roundToInt(),
            result.tdee.roundToInt()
        )
        binding.tvTargetKcal.text = getString(
            R.string.diet_target_kcal,
            result.computedTargetKcal.roundToInt()
        )
        binding.tvTierLabel.text = getString(
            R.string.diet_tier_label,
            result.selectedTier.label,
            result.selectedTier.targetKcal
        )

        binding.tvDoctorWarning.visibility =
            if (result.showDoctorWarning) View.VISIBLE else View.GONE

        isUpdatingGoalChips = true
        resetGoalChipLabels()
        when (result.selectedGoal.id) {
            GOAL_LOSE -> binding.chipGiamCan.isChecked = true
            GOAL_MAINTAIN -> binding.chipDuyTri.isChecked = true
            GOAL_GAIN -> binding.chipTangCan.isChecked = true
        }
        isUpdatingGoalChips = false
        highlightSuggestedGoal(result.suggestedGoalId)
    }

    private fun setupGoalChips() {
        val chips = listOf(
            binding.chipGiamCan to GOAL_LOSE,
            binding.chipDuyTri to GOAL_MAINTAIN,
            binding.chipTangCan to GOAL_GAIN
        )
        chips.forEach { (chip, goalId) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !isUpdatingGoalChips) {
                    val input = currentInput ?: return@setOnCheckedChangeListener
                    viewModel.updateGoal(goalId)
                    currentInput = input.copy(selectedGoalId = goalId)
                }
            }
        }
    }

    private fun resetGoalChipLabels() {
        binding.chipGiamCan.text = getString(R.string.diet_goal_lose)
        binding.chipDuyTri.text = getString(R.string.diet_goal_maintain)
        binding.chipTangCan.text = getString(R.string.diet_goal_gain)
    }

    private fun highlightSuggestedGoal(suggestedGoalId: String) {
        val chip: Chip? = when (suggestedGoalId) {
            GOAL_LOSE -> binding.chipGiamCan
            GOAL_MAINTAIN -> binding.chipDuyTri
            GOAL_GAIN -> binding.chipTangCan
            else -> null
        }
        chip?.text = when (suggestedGoalId) {
            GOAL_LOSE -> getString(R.string.diet_goal_lose_suggested)
            GOAL_MAINTAIN -> getString(R.string.diet_goal_maintain_suggested)
            GOAL_GAIN -> getString(R.string.diet_goal_gain_suggested)
            else -> chip?.text
        }
    }

    private fun playEntranceAnimations() {
        val sections = listOf(
            binding.sectionBmi,
            binding.sectionAdvice,
            binding.sectionCalories,
            binding.chipGroupGoals,
            binding.btnViewMenu
        )
        sections.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 120).toLong())
                .setDuration(450)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    companion object {
        private const val GOAL_LOSE = "giam_can"
        private const val GOAL_MAINTAIN = "duy_tri"
        private const val GOAL_GAIN = "tang_can"
    }
}
