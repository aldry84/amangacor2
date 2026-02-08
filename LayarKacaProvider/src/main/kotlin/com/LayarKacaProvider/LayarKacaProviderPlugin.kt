package com.LayarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin // Penting: Import Plugin
import android.content.Context

@CloudstreamPlugin
class LayarKacaProviderPlugin : Plugin() { // Perhatikan: extends Plugin(), BUKAN CloudstreamPlugin()
    
    override fun load(context: Context) {
        // Mendaftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())

        // Mendaftarkan Extractor (Pastikan file Hydrax.kt ada)
        registerExtractorAPI(Hydrax())
    }
}
