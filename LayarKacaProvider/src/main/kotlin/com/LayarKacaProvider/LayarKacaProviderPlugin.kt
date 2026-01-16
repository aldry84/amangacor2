package com.layarKacaProvide

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Daftarkan Extractor yang ada di Extractors.kt
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
