package com.example.healthup.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Exposes the parsed About content to the UI. Content is loaded once from the
 * local asset via [AboutRepository]; there is no network dependency.
 */
class AboutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AboutRepository(application)

    private val _content = MutableLiveData<AboutContent?>()
    val content: LiveData<AboutContent?> = _content

    init {
        load()
    }

    fun load() {
        _content.value = repository.loadAboutContent()
    }
}
