package com.Adicinema // Diubah dari com.AdiOMDb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdicinemaProvider : BasePlugin() { // Diubah dari AdiOMDbProvider
    override fun load() {
        // Daftarkan sumber utama ke Cloudstream
        registerMainAPI(Adicinema()) // Diubah dari AdiOMDb()
    }
}
