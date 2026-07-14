package com.example.healthup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.healthup.BaseAppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.healthup.databinding.ActivityDietLandingBinding
import com.example.healthup.diet.DietPreferences
import com.example.healthup.diet.SavedDishAdapter
import com.example.healthup.diet.SavedDishRepository
import com.example.healthup.diet.SavedDishSummary
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class DietLandingActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityDietLandingBinding
    private val savedDishRepository = SavedDishRepository()
    private lateinit var savedAdapter: SavedDishAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDietLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dietToolbar.btnToolbarBack.setOnClickListener { finish() }
        binding.dietToolbar.tvToolbarTitle.setText(R.string.diet_landing_title)
        binding.btnStart.setOnClickListener {
            startActivity(Intent(this, DietInputActivity::class.java))
        }

        savedAdapter = SavedDishAdapter(
            onItemClick = { summary ->
                startActivity(
                    Intent(this, DietMenuActivity::class.java)
                        .putExtra(DietMenuActivity.EXTRA_SAVED_DISH_ID, summary.id)
                )
            },
            onDeleteClick = { summary -> confirmDeleteDish(summary) }
        )
        binding.rvSavedMenus.layoutManager = LinearLayoutManager(this)
        binding.rvSavedMenus.adapter = savedAdapter
        setupSwipeToDelete()
        setupSearch()

        renderHistory()
    }


    override fun onResume() {
        super.onResume()
        renderHistory()
        loadSavedDishes()
    }

    private fun setupSearch() {
        binding.etSearchSavedDishes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                savedAdapter.filter(s?.toString().orEmpty())
                updateEmptyState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setupSwipeToDelete() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = savedAdapter.getItemAt(position) ?: run {
                    savedAdapter.notifyItemChanged(position)
                    return
                }
                confirmDeleteDish(item, onCancel = { savedAdapter.notifyItemChanged(position) })
            }
        })
        helper.attachToRecyclerView(binding.rvSavedMenus)
    }

    private fun renderHistory() {
        val history = DietPreferences.load(this)
        if (history == null) {
            binding.cardHistory.visibility = View.GONE
            return
        }

        binding.cardHistory.visibility = View.VISIBLE
        val daysAgo = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - history.savedAtMillis
        ).coerceAtLeast(0)
        val daysLabel = when (daysAgo) {
            0L -> getString(R.string.diet_history_today)
            1L -> getString(R.string.diet_history_yesterday)
            else -> getString(R.string.diet_history_days_ago, daysAgo)
        }
        binding.tvHistoryLabel.text = getString(R.string.diet_history_label, daysLabel)
        binding.tvHistoryBmi.text = String.format("%.1f", history.bmi)
        binding.tvHistoryBadge.text = history.bmiLabel

        binding.cardHistory.setOnClickListener {
            startActivity(Intent(this, DietResultActivity::class.java))
        }
    }

    private fun loadSavedDishes() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.sectionSavedMenus.visibility = View.GONE
            return
        }

        savedDishRepository.list(
            uid = user.uid,
            onSuccess = { dishes ->
                runOnUiThread {
                    if (dishes.isEmpty()) {
                        binding.sectionSavedMenus.visibility = View.GONE
                    } else {
                        binding.sectionSavedMenus.visibility = View.VISIBLE
                        savedAdapter.submit(dishes)
                        savedAdapter.filter(binding.etSearchSavedDishes.text?.toString().orEmpty())
                        updateEmptyState()
                    }
                }
            },
            onError = {
                runOnUiThread {
                    binding.sectionSavedMenus.visibility = View.GONE
                    Toast.makeText(this, R.string.diet_saved_dishes_load_error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateEmptyState() {
        val hasQuery = binding.etSearchSavedDishes.text?.isNotBlank() == true
        binding.tvSavedDishesEmpty.visibility =
            if (hasQuery && savedAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteDish(summary: SavedDishSummary, onCancel: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(R.string.diet_delete_saved_dish_title)
            .setMessage(getString(R.string.diet_delete_saved_dish_message, summary.dishName))
            .setPositiveButton(R.string.diet_delete_confirm) { _, _ -> deleteDish(summary) }
            .setNegativeButton(R.string.diet_dialog_cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    private fun deleteDish(summary: SavedDishSummary) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        savedDishRepository.delete(
            uid = user.uid,
            dishDocId = summary.id,
            onSuccess = {
                runOnUiThread {
                    savedAdapter.removeItem(summary.id)
                    updateEmptyState()
                    if (savedAdapter.isEmpty()) {
                        binding.sectionSavedMenus.visibility = View.GONE
                    }
                    Toast.makeText(this, R.string.diet_delete_saved_dish_success, Toast.LENGTH_SHORT).show()
                }
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, R.string.diet_delete_saved_dish_error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
