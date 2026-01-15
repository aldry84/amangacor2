package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // 1. Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // 2. Daftarkan Extractor Custom Kita (Sesuai nama class di Extractors.kt)
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(F16px())
        registerExtractorAPI(Hydrax()) // AbyssCDN
        
        // 3. Daftarkan Extractor Bawaan Cloudstream (Berguna untuk server umum)
        registerExtractorAPI(VidHidePro6())
    }
}
