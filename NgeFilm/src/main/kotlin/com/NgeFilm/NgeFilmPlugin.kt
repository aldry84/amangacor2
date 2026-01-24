package com.NgeFilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NgeFilmPlugin: Plugin() {
    override fun load(context: Context) {
        // FIX ERROR 3: Menggunakan nama class NgeFilmProvider yang benar
        registerMainAPI(NgeFilmProvider())
    }
}
