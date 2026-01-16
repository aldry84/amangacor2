package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // Daftarkan Extractor kustom untuk domain Movie & Series
        registerExtractorAPI(CustomEmturbovid()) 
        registerExtractorAPI(object : CustomEmturbovid() { 
            override val mainUrl = "https://turboviplay.com" 
        })
        
        registerExtractorAPI(Furher())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
