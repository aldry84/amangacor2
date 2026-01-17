package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // 1. Daftarkan Provider Utama (LayarKacaProvider)
        registerMainAPI(LayarKacaProvider())
        
        // 2. Daftarkan Extractor Custom (Dari file Extractors.kt)
        
        // Extractor untuk Server Turbovid (Versi Header Chrome 132)
        registerExtractorAPI(Turbovidhls())
        
        // Extractor untuk Server CAST (F16Px) dengan fitur Decrypt AES
        registerExtractorAPI(F16Px())
        registerExtractorAPI(CastBox()) // Cadangan/Redirect ke F16Px
        
        // Extractor lainnya
        registerExtractorAPI(Furher())
        registerExtractorAPI(Co4nxtrl())
        
        // 3. Daftarkan Extractor Bawaan Cloudstream yang masih oke
        registerExtractorAPI(VidHidePro6())
        
        // CATATAN: 
        // Jangan daftarkan 'EmturbovidExtractor()' agar aplikasi 
        // terpaksa menggunakan 'Turbovidhls' buatan kita.
    }
}
