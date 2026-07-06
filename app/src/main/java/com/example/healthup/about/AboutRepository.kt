package com.example.healthup.about

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads and parses the static About content from the bundled JSON asset.
 * Returns null (never throws) when the asset is missing or malformed, so the
 * screen can degrade gracefully.
 */
class AboutRepository(private val context: Context) {

    fun loadAboutContent(): AboutContent? {
        val raw = readAsset(ASSET_FILE) ?: return null
        return try {
            parse(JSONObject(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $ASSET_FILE", e)
            null
        }
    }

    private fun readAsset(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read asset $name", e)
        null
    }

    private fun parse(json: JSONObject): AboutContent {
        val heroJson = json.getJSONObject("hero")
        val hero = Hero(
            title = heroJson.optString("title"),
            description = heroJson.optString("description"),
            image = heroJson.optString("image")
        )

        val storyJson = json.getJSONObject("story")
        val story = Story(
            title = storyJson.optString("title"),
            paragraphs = storyJson.optJSONArray("paragraphs").toStringList(),
            quote = storyJson.optString("quote")
        )

        val values = json.optJSONArray("values").mapObjects {
            ValueItem(
                icon = it.optString("icon"),
                title = it.optString("title"),
                description = it.optString("description")
            )
        }

        val qualities = json.optJSONArray("qualities").mapObjects {
            QualityItem(
                title = it.optString("title"),
                description = it.optString("description")
            )
        }

        val statsJson = json.getJSONObject("statistics")
        val statistics = Statistics(
            badge = statsJson.optString("badge"),
            title = statsJson.optString("title"),
            subtitle = statsJson.optString("subtitle"),
            items = statsJson.optJSONArray("items").mapObjects {
                StatItem(number = it.optString("number"), label = it.optString("label"))
            }
        )

        val gallery = json.optJSONArray("gallery").toStringList()

        return AboutContent(hero, story, values, qualities, statistics, gallery)
    }

    companion object {
        private const val TAG = "AboutRepository"
        private const val ASSET_FILE = "about_healthup.json"
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val result = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { result.add(transform(it)) }
    }
    return result
}
