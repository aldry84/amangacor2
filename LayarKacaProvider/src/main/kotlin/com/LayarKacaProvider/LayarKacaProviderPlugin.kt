package com.layarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
// Hapus import com.lagradost.cloudstream3.extractors.EmturbovidExtractor (yang bikin error)
import com.lagradost.cloudstream3.extractors.VidHidePro6

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Daftarkan Extractor Custom Kita
        // Perhatikan: Kita menghapus EmturbovidExtractor() bawaan
        // Kita akan menanganinya lewat Turbovidhls dan EmturboCustom di Extractors.kt
        
        registerExtractorAPI(EmturboCustom()) // <--- INI BARU
        registerExtractorAPI(Turbovidhls())
        
        registerExtractorAPI(Furher())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Co4nxtrl())
    }
}
