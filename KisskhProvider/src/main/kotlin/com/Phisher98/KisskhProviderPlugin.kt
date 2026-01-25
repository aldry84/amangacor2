package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class KisskhProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(KisskhProvider())
    }
}
