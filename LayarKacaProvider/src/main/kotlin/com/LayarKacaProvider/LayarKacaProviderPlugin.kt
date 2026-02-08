package com.LayarKacaProvider // Harus sama persis dengan file lainnya!

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        // Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Extractor Servers
        registerExtractorAPI(EmturbovidExtractor()) 
        registerExtractorAPI(P2PExtractor())        
        registerExtractorAPI(F16Extractor())        
        registerExtractorAPI(HydraxExtractor()) 
    }
}
