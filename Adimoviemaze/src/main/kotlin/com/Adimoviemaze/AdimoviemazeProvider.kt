package com.Adimoviemaze

import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.VidCloud
import com.lagradost.cloudstream3.extractors.StreamWish
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Uqload

@CloudstreamPlugin
class AdimoviemazeProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Adimoviemaze())
        
        // Extractor umum dan terpercaya
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Uqload()) 
        registerExtractorAPI(StreamWish())
        // Anda dapat menambahkan extractor kustom di sini jika diperlukan
    }
}
