package com.Adi21 // PERUBAHAN: dari com.Adimoviebox

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Adi21Provider: BasePlugin() { // PERUBAHAN: dari AdimovieboxProvider
    override fun load() {
        registerMainAPI(Adi21()) // PERUBAHAN: dari Adimoviebox
        // Karena Moviebox Anda menggunakan tautan stream langsung (INFER_TYPE), 
        // dan tidak menggunakan extractor spesifik, saya tidak mendaftarkan extractor di sini.
        // Jika link-nya adalah extractor yang terpisah (seperti dari BanglaPlex), 
        // Anda perlu mendaftarkannya.
    }
}
