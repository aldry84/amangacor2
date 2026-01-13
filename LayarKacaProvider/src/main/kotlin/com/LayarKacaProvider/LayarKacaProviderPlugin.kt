package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
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
        
        // Custom Extractors (Milikmu)
        registerExtractorAPI(Furher())
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())

        // Generic/Built-in Extractors (PENTING)
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(MixDrop())    
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(DoodLaExtractor())
        
        // Catatan: Hydrax dan GenericExtractor dihapus sementara agar bisa build.
        // Deteksi link tetap akan berjalan via Regex di Provider.
    }
}
