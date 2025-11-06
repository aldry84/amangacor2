package com.Phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Movie21Provider: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan MainAPI
        registerMainAPI(Movie21()) 
        // Mendaftarkan Extractor
        registerExtractorAPI(VidsrcEmbedExtractor())
    }
}
