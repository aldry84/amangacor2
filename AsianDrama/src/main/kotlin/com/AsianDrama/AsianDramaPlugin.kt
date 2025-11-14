package com.AsianDrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AsianDramaPlugin : Plugin() {
    override fun load(context: Context) {
        // Register main provider
        registerMainAPI(AsianDramaProvider())
        
        // Register all extractors from one file
        registerExtractorAPI(AsianDramaExtractors.IdlixExtractor())
        registerExtractorAPI(AsianDramaExtractors.MappleExtractor()) 
        registerExtractorAPI(AsianDramaExtractors.WyzieExtractor())
        registerExtractorAPI(AsianDramaExtractors.GomoviesExtractor())
    }
}
