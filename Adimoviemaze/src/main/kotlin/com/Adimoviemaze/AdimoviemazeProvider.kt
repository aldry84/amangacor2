package com.Adimoviemaze

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdimoviemazeProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Adimoviemaze())
        // Daftarkan Extractor API di sini jika ada (seperti Plextream, Xcloud, dll.)
    }
}
