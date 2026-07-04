package com.example.healthup.about

/**
 * Immutable data model for the "Về HealthUp" (About) screen.
 * All values are populated from assets/about_healthup.json by [AboutRepository].
 */
data class AboutContent(
    val hero: Hero,
    val story: Story,
    val values: List<ValueItem>,
    val qualities: List<QualityItem>,
    val statistics: Statistics,
    val gallery: List<String>
)

data class Hero(
    val title: String,
    val description: String,
    val image: String
)

data class Story(
    val title: String,
    val paragraphs: List<String>,
    val quote: String
)

data class ValueItem(
    val icon: String,
    val title: String,
    val description: String
)

data class QualityItem(
    val title: String,
    val description: String
)

data class Statistics(
    val badge: String,
    val title: String,
    val subtitle: String,
    val items: List<StatItem>
)

data class StatItem(
    val number: String,
    val label: String
)
