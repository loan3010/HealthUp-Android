package com.example.healthup

import android.content.Intent
import android.os.Bundle
import com.example.healthup.BaseAppCompatActivity

/** Redirects legacy entry points to the new diet landing flow. */
class DietRecommendationActivity : BaseAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DietLandingActivity::class.java))
        finish()
    }
}
