package com.PusatFilm21 // <--- INI BAGIAN YANG SAYA UBAH

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class PusatFilm21Plugin: Plugin() {
    override fun load(context: Context) {
        // Register Provider
        registerMainAPI(PusatFilm21())
    }
}
