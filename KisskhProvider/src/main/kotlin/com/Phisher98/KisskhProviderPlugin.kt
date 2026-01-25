package com.Phisher98 // Pastikan package ini SAMA dengan KisskhProvider.kt

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class KisskhProviderPlugin: BasePlugin() {
    override fun load() {
        // Pastikan nama kelasnya benar: KisskhProvider
        registerMainAPI(KisskhProvider())
    }
}
