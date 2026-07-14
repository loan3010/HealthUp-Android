package com.example.healthup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.healthup.BaseAppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.healthup.databinding.ActivityDietMenuBinding
import com.example.healthup.diet.ComputedMeal
import com.example.healthup.diet.DayMenu
import com.example.healthup.diet.DietCalculator
import com.example.healthup.diet.DietPreferences
import com.example.healthup.diet.DietRepository
import com.example.healthup.diet.DietViewModel
import com.example.healthup.diet.MealPlanAdapter
import com.example.healthup.diet.MenuGenerator
import com.example.healthup.diet.ProductResolver
import com.example.healthup.diet.SavedDishRepository
import com.google.firebase.auth.FirebaseAuth

class DietMenuActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityDietMenuBinding
    private lateinit var viewModel: DietViewModel
    private lateinit var mealAdapter: MealPlanAdapter
    private val savedDishRepository = SavedDishRepository()
    private var currentMenu: DayMenu? = null
    private var savedDishId: String? = null
    private var savedDishIdsToday: Set<String> = emptySet()
    private var isReadOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDietMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dietToolbar.btnToolbarBack.setOnClickListener { finish() }
        viewModel = ViewModelProvider(this)[DietViewModel::class.java]

        savedDishId = intent.getStringExtra(EXTRA_SAVED_DISH_ID)

        mealAdapter = MealPlanAdapter(
            onProductClick = { meal -> openProductDetail(meal) },
            onSwapMeal = { meal, position -> swapMeal(meal, position) },
            onSaveDish = { meal -> onSaveDishClicked(meal) }
        )
        binding.rvMeals.layoutManager = LinearLayoutManager(this)
        binding.rvMeals.adapter = mealAdapter

        binding.btnRefreshMenu.setOnClickListener {
            if (isReadOnly) {
                Toast.makeText(this, R.string.diet_saved_dish_readonly_hint, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.refreshMenu()
            }
        }

        viewModel.dietData.observe(this) { data ->
            data?.let { binding.tvDisclaimer.text = it.meta.disclaimer }
        }

        viewModel.dayMenu.observe(this) { menu ->
            menu?.let { renderMenu(it) }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        if (savedDishId != null) {
            isReadOnly = true
            binding.dietToolbar.tvToolbarTitle.setText(R.string.diet_saved_dish_detail_title)
            loadSavedDish(savedDishId!!)
        } else {
            binding.dietToolbar.tvToolbarTitle.setText(R.string.diet_menu_title)
            loadMenu()
        }
    }

    private fun loadMenu() {
        val history = DietPreferences.load(this)
        if (history == null) {
            Toast.makeText(this, R.string.diet_data_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.calculate(history.input, history.goalId)
        loadSavedDishIdsToday()
    }

    private fun loadSavedDishIdsToday() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        savedDishRepository.loadSavedDishIdsToday(
            uid = user.uid,
            onSuccess = { ids ->
                runOnUiThread {
                    savedDishIdsToday = ids
                    currentMenu?.let { renderMenu(it) }
                }
            }
        )
    }

    private fun loadSavedDish(dishDocId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, R.string.diet_data_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.btnRefreshMenu.visibility = View.GONE
        savedDishRepository.load(
            uid = user.uid,
            dishDocId = dishDocId,
            onSuccess = { saved ->
                runOnUiThread { renderSavedDish(saved.toComputedMeal()) }
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, R.string.diet_saved_dish_load_error, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }

    private fun renderSavedDish(meal: ComputedMeal) {
        binding.tvMenuKcalSummary.text = getString(
            R.string.diet_saved_dish_kcal,
            meal.totalKcal
        )
        mealAdapter.submit(
            meals = listOf(meal),
            readOnly = true
        )
    }

    private fun renderMenu(menu: DayMenu) {
        currentMenu = menu
        binding.tvMenuKcalSummary.text = getString(
            R.string.diet_menu_kcal_summary,
            menu.actualKcalDay,
            menu.targetKcalDay
        )
        val orderedMeals = MenuGenerator.MEAL_ORDER.mapNotNull { menu.meals[it] }
        mealAdapter.submit(
            meals = orderedMeals,
            times = MenuGenerator.MEAL_ORDER,
            savedDishIdsToday = savedDishIdsToday,
            readOnly = false
        )
    }

    private fun onSaveDishClicked(meal: ComputedMeal) {
        val menu = currentMenu
        if (menu == null) {
            Toast.makeText(this, R.string.diet_menu_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.diet_login_required_title)
                .setMessage(R.string.diet_login_required_save_dish_message)
                .setPositiveButton(R.string.login_button) { _, _ ->
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                .setNegativeButton(R.string.diet_dialog_cancel, null)
                .show()
            return
        }

        savedDishRepository.save(
            uid = user.uid,
            meal = meal,
            goalId = menu.goalId,
            onSuccess = { created ->
                runOnUiThread {
                    if (created) {
                        savedDishIdsToday = savedDishIdsToday + meal.dishId
                        mealAdapter.markDishSaved(meal.dishId)
                        Toast.makeText(this, R.string.diet_save_dish_success, Toast.LENGTH_SHORT).show()
                    } else {
                        savedDishIdsToday = savedDishIdsToday + meal.dishId
                        mealAdapter.markDishSaved(meal.dishId)
                        Toast.makeText(this, R.string.diet_dish_already_saved_toast, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, R.string.diet_save_dish_error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun swapMeal(meal: ComputedMeal, position: Int) {
        if (isReadOnly) {
            Toast.makeText(this, R.string.diet_saved_dish_readonly_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val menu = currentMenu ?: return
        val history = DietPreferences.load(this) ?: return
        val data = DietRepository(this).loadDietData() ?: return
        val result = DietCalculator.calculate(data, history.input, history.goalId) ?: return

        val mealTime = MenuGenerator.MEAL_ORDER.getOrNull(position) ?: return
        val updated = MenuGenerator.swapMeal(
            data = data,
            goalId = history.goalId,
            tier = result.selectedTier,
            mealTime = mealTime,
            currentMenu = menu
        )
        currentMenu = updated
        renderMenu(updated)
    }

    private fun openProductDetail(meal: ComputedMeal) {
        ProductResolver.resolveProductId(meal.productId, meal.productName) { firestoreId ->
            if (firestoreId != null) {
                startActivity(
                    Intent(this, ProductDetailActivity::class.java)
                        .putExtra("productId", firestoreId)
                )
            } else {
                Toast.makeText(this, R.string.diet_product_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_SAVED_DISH_ID = "saved_dish_id"
    }
}
