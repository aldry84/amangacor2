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
        
        // --- INI URUTAN TERBAIK ---
        registerExtractorAPI(PlayerIframe()) // 1. Buka Kulit Luar
        registerExtractorAPI(UniversalVIP()) // 2. Eksekusi CAST, TURBO, HYDRAX
        
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        
        // Redirector Classes (biar aman)
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(F16px())
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
