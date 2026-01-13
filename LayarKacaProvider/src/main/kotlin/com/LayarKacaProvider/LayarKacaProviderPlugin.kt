package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // Prioritas Utama
        registerExtractorAPI(PlayerIframe()) // INI WAJIB UNTUK MEMBUKA KULIT CAST/TURBO
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(F16px())        // Logika baru CAST
        registerExtractorAPI(Turbovidhls())  // Logika baru TURBO
        registerExtractorAPI(EmturbovidCustom())

        // Cadangan
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Co4nxtrl())

        // Bawaan
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(MixDrop())    
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(DoodLaExtractor())
    }
}
