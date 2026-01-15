package com.IndoFilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HomeSpeciallyPlugin: CloudstreamPlugin() {
    override fun load(context: Context) {
        // Mendaftarkan class utama provider kita
        registerMainAPI(HomeSpecially())
    }
}
