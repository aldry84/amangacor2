package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

// MENGGUNAKAN BasePlugin SEPERTI PADA Adimoviebox
@CloudstreamPlugin
class AdiOMDbProvider: BasePlugin() { 
    override fun load() {
        registerMainAPI(AdiOMDb())
    }
}
