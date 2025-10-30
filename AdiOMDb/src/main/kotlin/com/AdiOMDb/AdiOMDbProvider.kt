package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdiOMDbProvider : BasePlugin() {
    override fun load() {
        // Daftarkan sumber utama ke Cloudstream
        registerMainAPI(AdiOMDb())
    }
}
