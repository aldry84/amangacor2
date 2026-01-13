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
        
        // Extractor Utama
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Turbovidhls())       // Pastikan ini terdaftar
        registerExtractorAPI(EmturbovidCustom())  // Untuk redirect Turbo
        registerExtractorAPI(F16px())             // Untuk Cast
        
        // Extractor Cadangan
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Co4nxtrl())

        // Extractor Bawaan Cloudstream
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(MixDrop())    
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(DoodLaExtractor())
    }
}
