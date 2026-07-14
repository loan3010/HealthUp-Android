package com.example.healthup

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.example.healthup.BaseAppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.healthup.about.AboutContent
import com.example.healthup.about.AboutViewModel
import com.example.healthup.about.AssetImageLoader
import com.example.healthup.about.GalleryAdapter
import com.example.healthup.about.QualitiesAdapter
import com.example.healthup.about.StatsAdapter
import com.example.healthup.about.ValuesAdapter
import com.example.healthup.databinding.ActivityAboutBinding

/**
 * Static "Về HealthUp" (About) screen. All displayed content is loaded from the
 * local asset assets/about_healthup.json through an MVVM pipeline
 * (AboutRepository -> AboutViewModel -> this Activity). No network / Firebase.
 */
class AboutActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private lateinit var viewModel: AboutViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        viewModel = ViewModelProvider(this).get(AboutViewModel::class.java)
        viewModel.content.observe(this) { content ->
            content?.let { render(it) }
        }
    }

    private fun render(content: AboutContent) {
        bindHero(content)
        bindStory(content)
        bindGrid(binding.valuesRecycler, ValuesAdapter(content.values))
        bindGrid(binding.qualitiesRecycler, QualitiesAdapter(content.qualities))
        bindStatistics(content)
        bindGrid(binding.galleryRecycler, GalleryAdapter(content.gallery))
    }

    private fun bindHero(content: AboutContent) {
        binding.heroTitle.text = content.hero.title
        binding.heroDescription.text = content.hero.description
        AssetImageLoader.load(binding.bannerImage, binding.bannerPlaceholder, content.hero.image)
    }

    private fun bindStory(content: AboutContent) {
        binding.storyTitle.text = content.story.title
        binding.storyQuote.text = content.story.quote

        binding.storyParagraphs.removeAllViews()
        content.story.paragraphs.forEachIndexed { index, paragraph ->
            val textView = TextView(this).apply {
                text = paragraph
                setTextColor(ContextCompat.getColor(this@AboutActivity, R.color.text_secondary))
                textSize = 14f
                setLineSpacing(dp(3f), 1f)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.topMargin = dp(12f).toInt()
            textView.layoutParams = params
            binding.storyParagraphs.addView(textView)
        }
    }

    private fun bindStatistics(content: AboutContent) {
        binding.statsBadge.text = content.statistics.badge
        binding.statsTitle.text = content.statistics.title
        binding.statsSubtitle.text = content.statistics.subtitle
        bindGrid(binding.statsRecycler, StatsAdapter(content.statistics.items))
    }

    private fun bindGrid(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        gridAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>
    ) {
        recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.adapter = gridAdapter
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val GRID_SPAN = 2
    }
}
