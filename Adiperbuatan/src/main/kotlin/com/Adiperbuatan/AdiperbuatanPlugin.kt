package com.adiperbuatan // Diubah dari com.dramafull

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdiperbuatanPlugin: BasePlugin() { // Diubah dari DramaFullPlugin
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Adiperbuatan()) // Diubah dari DramaFull()
    }
}
