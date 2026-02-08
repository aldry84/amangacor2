package com.LayarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import android.content.Context

// Hapus anotasi @Plugin, biarkan class ini polos.
// Cloudstream akan menemukannya lewat manifest.json
class LayarKacaProviderPlugin : CloudstreamPlugin() {
    
    override fun load(context: Context) {
        // Mendaftarkan Provider
        registerMainAPI(LayarKacaProvider())

        // Mendaftarkan Extractor
        registerExtractorAPI(Hydrax())
    }
}
