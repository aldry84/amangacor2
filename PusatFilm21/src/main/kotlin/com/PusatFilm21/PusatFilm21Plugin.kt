package com.PusatFilm21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PusatFilm21Plugin: Plugin() {
    override fun load(context: Context) {
        // Register provider kita
        registerMainAPI(PusatFilm21())
    }
}
