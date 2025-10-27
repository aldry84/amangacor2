package com.Adimoviemaze

import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdimoviemazeProvider: BasePlugin() {
    override fun load() {
        // Daftarkan Main API
        registerMainAPI(Adimoviemaze())
        
        // Daftarkan Extractor umum dari CloudStream
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Uqload()) 
        
        // Daftarkan Extractor Kustom dari Extractors.kt
        registerExtractorAPI(StreamWishCustom())
        registerExtractorAPI(StreamSBCustom())
        registerExtractorAPI(MazePlayerExtractor())
    }
}
