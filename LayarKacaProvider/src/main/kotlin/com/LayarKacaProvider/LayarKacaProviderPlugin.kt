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
        registerExtractorAPI(Furher())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(CastBox()) // <-- TAMBAHKAN INI
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
