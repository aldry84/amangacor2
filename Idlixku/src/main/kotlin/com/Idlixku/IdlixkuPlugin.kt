package com.Idlixku

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IdlixkuPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(IdlixkuProvider())
    }
}
