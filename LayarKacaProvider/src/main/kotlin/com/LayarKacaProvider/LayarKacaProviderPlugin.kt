package com.layarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.DoodLaExtractor

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // Extractor Khusus yang kita buat di Extractors.kt
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(F16px())             // Untuk CAST / VidHide
        registerExtractorAPI(EmturbovidCustom())  // Untuk TURBOVIP
        
        // Extractor Cadangan
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Co4nxtrl())

        // Extractor Bawaan (Generic)
        registerExtractorAPI(MixDrop())    
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(DoodLaExtractor())
    }
}
