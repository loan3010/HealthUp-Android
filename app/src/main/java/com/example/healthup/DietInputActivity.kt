package com.example.healthup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import com.example.healthup.BaseAppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.healthup.databinding.ActivityDietInputBinding
import com.example.healthup.diet.ActivityLevel
import com.example.healthup.diet.DietPreferences
import com.example.healthup.diet.DietViewModel
import com.example.healthup.diet.Gender
import com.example.healthup.diet.UserDietInput
import kotlin.math.roundToInt

class DietInputActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityDietInputBinding
    private lateinit var viewModel: DietViewModel
    private var activityLevels: List<ActivityLevel> = emptyList()
    private var selectedActivityId: String = "sedentary"
    private var selectedGender = Gender.MALE
    private var suppressSliderUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDietInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dietToolbar.btnToolbarBack.setOnClickListener { finish() }
        binding.dietToolbar.tvToolbarTitle.setText(R.string.diet_input_screen_title)
        viewModel = ViewModelProvider(this)[DietViewModel::class.java]

        setupGenderToggle()
        setupSliders()
        prefillFromHistory()

        binding.btnCalculate.setOnClickListener { onCalculateClicked() }

        viewModel.dietData.observe(this) { data ->
            if (data == null) {
                Toast.makeText(this, R.string.diet_data_error, Toast.LENGTH_LONG).show()
                return@observe
            }
            activityLevels = data.calorieCalculation.activityLevels
            setupActivityDropdown(activityLevels)
        }

        viewModel.calculationResult.observe(this) { result ->
            result ?: return@observe
            val input = buildInput() ?: return@observe
            DietPreferences.save(this, input, result)
            startActivity(Intent(this, DietResultActivity::class.java))
            finish()
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }


    private fun prefillFromHistory() {
        val history = DietPreferences.load(this) ?: return
        val input = history.input
        selectedGender = input.gender
        updateGenderUi()
        binding.etAge.setText(input.age.toString())
        setHeight(input.heightCm)
        setWeight(input.weightKg)
        selectedActivityId = input.activityId
    }

    private fun setupGenderToggle() {
        binding.btnGenderMale.setOnClickListener {
            selectedGender = Gender.MALE
            updateGenderUi()
        }
        binding.btnGenderFemale.setOnClickListener {
            selectedGender = Gender.FEMALE
            updateGenderUi()
        }
        updateGenderUi()
    }

    private fun updateGenderUi() {
        val isMale = selectedGender == Gender.MALE
        binding.btnGenderMale.isSelected = isMale
        binding.btnGenderFemale.isSelected = !isMale
        binding.btnGenderMale.setTextColor(
            getColor(if (isMale) R.color.white else R.color.text_primary)
        )
        binding.btnGenderFemale.setTextColor(
            getColor(if (!isMale) R.color.white else R.color.text_primary)
        )
    }

    private fun setupSliders() {
        binding.sliderHeight.value = 165f
        binding.sliderWeight.value = 60f

        binding.sliderHeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !suppressSliderUpdate) {
                binding.etHeight.setText(value.roundToInt().toString())
            }
        }
        binding.sliderWeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !suppressSliderUpdate) {
                binding.etWeight.setText(String.format("%.1f", value))
            }
        }

        binding.etHeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toFloatOrNull() ?: return
                if (value in 120f..220f) {
                    suppressSliderUpdate = true
                    binding.sliderHeight.value = value
                    suppressSliderUpdate = false
                }
            }
        })

        binding.etWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.replace(",", ".")?.toFloatOrNull() ?: return
                if (value in 30f..150f) {
                    suppressSliderUpdate = true
                    binding.sliderWeight.value = value
                    suppressSliderUpdate = false
                }
            }
        })
    }

    private fun setHeight(value: Double) {
        val clamped = value.toFloat().coerceIn(120f, 220f)
        suppressSliderUpdate = true
        binding.sliderHeight.value = clamped
        binding.etHeight.setText(clamped.roundToInt().toString())
        suppressSliderUpdate = false
    }

    private fun setWeight(value: Double) {
        val clamped = value.toFloat().coerceIn(30f, 150f)
        suppressSliderUpdate = true
        binding.sliderWeight.value = clamped
        binding.etWeight.setText(String.format("%.1f", clamped))
        suppressSliderUpdate = false
    }

    private fun setupActivityDropdown(levels: List<ActivityLevel>) {
        val labels = levels.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvActivityLevel.setAdapter(adapter)

        val selectedIndex = levels.indexOfFirst { it.id == selectedActivityId }.coerceAtLeast(0)
        binding.actvActivityLevel.setText(labels[selectedIndex], false)

        binding.actvActivityLevel.setOnItemClickListener { _, _, position, _ ->
            selectedActivityId = levels[position].id
        }
    }

    private fun onCalculateClicked() {
        val input = buildInput()
        if (input == null) return
        viewModel.calculate(input)
    }

    private fun buildInput(): UserDietInput? {
        val age = binding.etAge.text?.toString()?.toIntOrNull()
        val height = binding.etHeight.text?.toString()?.toDoubleOrNull()
        val weight = binding.etWeight.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

        if (age == null || age < 1 || age > 120) {
            binding.etAge.error = getString(R.string.diet_invalid_age)
            return null
        }
        if (height == null || height < 50 || height > 250) {
            binding.etHeight.error = getString(R.string.diet_invalid_height)
            return null
        }
        if (weight == null || weight < 20 || weight > 300) {
            binding.etWeight.error = getString(R.string.diet_invalid_weight)
            return null
        }

        return UserDietInput(
            gender = selectedGender,
            age = age,
            heightCm = height,
            weightKg = weight,
            activityId = selectedActivityId
        )
    }
}
