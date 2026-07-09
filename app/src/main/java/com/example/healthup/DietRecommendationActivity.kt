package com.example.healthup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Redirects legacy entry points to the new diet landing flow. */
class DietRecommendationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DietLandingActivity::class.java))
        finish()
    }
}
