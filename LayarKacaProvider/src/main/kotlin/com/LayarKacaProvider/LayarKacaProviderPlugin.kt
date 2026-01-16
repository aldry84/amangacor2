package com.layarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.extractors.VidHidePro6

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // GUNAKAN CUSTOM EXTRACTOR KITA
        registerExtractorAPI(EmturboCustom()) 
        registerExtractorAPI(Turbovidhls())
        
        registerExtractorAPI(Furher())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
