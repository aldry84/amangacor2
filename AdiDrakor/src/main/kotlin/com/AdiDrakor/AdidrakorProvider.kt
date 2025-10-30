package com.AdiDrakor

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdidrakorProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(AdiDrakor())
    }
}
