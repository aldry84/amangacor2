package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // Register Router
        registerExtractorAPI(PlayerIframe())
        
        // Register Server Spesifik
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(F16px())
        registerExtractorAPI(Hydrax())
        
        // Server Simple
        registerExtractorAPI(Co4nxtrl())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Furher2())
        
        // Bawaan
        registerExtractorAPI(VidHidePro6())
    }
}
