package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // Mendaftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Mendaftarkan Extractor yang SUDAH didefinisikan di Extractors.kt
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        
        // Mendaftarkan Extractor Bawaan Cloudstream (opsional tapi berguna)
        registerExtractorAPI(VidHidePro6())
    }
}
