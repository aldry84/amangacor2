package com.Adimoviemaze

import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Uqload

@CloudstreamPlugin
class AdimoviemazeProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Adimoviemaze())
        
        // Beberapa extractor umum yang mungkin digunakan
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Uqload()) 
        // Tambahkan extractor kustom jika Anda menemukannya di moviemaze.cc
    }
}
