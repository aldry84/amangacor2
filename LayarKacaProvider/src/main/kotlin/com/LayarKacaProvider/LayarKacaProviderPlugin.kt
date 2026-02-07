package com.LayarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        // Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Daftarkan Extractor Khusus (Anti Error 3001)
        registerExtractorAPI(EmturbovidExtractor())
    }
}
