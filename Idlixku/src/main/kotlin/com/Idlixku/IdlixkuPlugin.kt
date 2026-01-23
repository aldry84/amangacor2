package com.Idlixku

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IdlixkuPlugin: BasePlugin() {
    override fun load() {
        // Mendaftarkan Provider utama (untuk scraping daftar film & search)
        registerMainAPI(IdlixkuProvider())
        
        // Mendaftarkan Extractor (untuk mengolah link player JeniusPlay)
        // TANPA INI, LINK VIDEO TIDAK AKAN PERNAH JALAN!
        registerExtractorAPI(JeniusPlayExtractor())
    }
}
