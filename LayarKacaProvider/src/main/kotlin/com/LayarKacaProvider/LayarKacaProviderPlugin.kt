package com.LayarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@Plugin
class LayarKacaProviderPlugin : CloudstreamPlugin() {
    override fun load(context: Context) {
        // 1. Mendaftarkan Provider Utama (LayarKaca21)
        // Ini agar muncul di halaman Home aplikasi
        registerMainAPI(LayarKacaProvider())

        // 2. Mendaftarkan Extractor Khusus (Hydrax)
        // Ini penting agar saat aplikasi menemukan link "abysscdn.com", 
        // dia tahu harus menggunakan logika di file Hydrax.kt kita
        registerExtractorAPI(Hydrax())
    }
}
