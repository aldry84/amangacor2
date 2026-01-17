package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // 1. Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // 2. Daftarkan Extractor Custom
        // PENTING: EmturbovidExtractor DIHAPUS agar tidak bentrok
        registerExtractorAPI(Furher())
        registerExtractorAPI(Turbovidhls()) // Extractor ini yang akan dipakai
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
