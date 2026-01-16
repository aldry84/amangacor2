package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // Daftarkan Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Daftarkan Extractor Custom
        registerExtractorAPI(Furher())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Co4nxtrl())
        
        // Extractor bawaan core biasanya tidak perlu didaftarkan di sini
        // kecuali kamu memang sengaja ingin memaksa versinya.
        // Jika kode ini error "Unresolved reference", hapus baris di bawah ini.
        try {
            registerExtractorAPI(EmturbovidExtractor())
            registerExtractorAPI(VidHidePro6())
        } catch (e: Exception) {
            // Abaikan jika extractor ini sudah ada di core dan tidak bisa diimport
        }
    }
}
