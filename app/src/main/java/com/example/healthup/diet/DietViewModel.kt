package com.example.healthup.diet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DietViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DietRepository(application)

    private val _dietData = MutableLiveData<DietData?>()
    val dietData: LiveData<DietData?> = _dietData

    private val _calculationResult = MutableLiveData<DietCalculationResult?>()
    val calculationResult: LiveData<DietCalculationResult?> = _calculationResult

    private val _dayMenu = MutableLiveData<DayMenu?>()
    val dayMenu: LiveData<DayMenu?> = _dayMenu

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var lastInput: UserDietInput? = null
    private var menuDayIndex = 1

    init {
        _dietData.value = repository.loadDietData()
    }

    fun calculate(input: UserDietInput, goalIdOverride: String? = null) {
        val data = _dietData.value
        if (data == null) {
            _errorMessage.value = "Không tải được dữ liệu dinh dưỡng"
            return
        }
        lastInput = input
        val result = DietCalculator.calculate(data, input, goalIdOverride)
        if (result == null) {
            _errorMessage.value = "Không thể tính toán. Vui lòng kiểm tra thông tin nhập vào."
            return
        }
        _calculationResult.value = result
        menuDayIndex = 1
        generateMenu(result.selectedGoal.id, result.selectedTier, menuDayIndex)
    }

    fun updateGoal(goalId: String) {
        val input = lastInput ?: return
        calculate(input.copy(selectedGoalId = goalId), goalId)
    }

    fun refreshMenu() {
        val result = _calculationResult.value ?: return
        val data = _dietData.value ?: return
        menuDayIndex = (menuDayIndex % 2) + 1
        generateMenu(result.selectedGoal.id, result.selectedTier, menuDayIndex)
    }

    private fun generateMenu(goalId: String, tier: GoalTier, day: Int) {
        val data = _dietData.value ?: return
        val sample = MenuGenerator.findSampleMenu(data, goalId, tier.id, day)
        _dayMenu.value = sample ?: MenuGenerator.generateDayMenu(data, goalId, tier, day)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
