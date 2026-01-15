package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())
        registerExtractorAPI(F16px())
        
        // TAMBAHAN BARU:
        registerExtractorAPI(AbyssCdn())
    }
}
