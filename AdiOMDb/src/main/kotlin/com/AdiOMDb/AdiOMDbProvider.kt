package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.PluginRepository
import android.content.Context

@CloudstreamPlugin
class AdiOMDbProvider: PluginRepository() {
    override fun load(context: Context) {
        // Mendaftarkan MainAPI
        registerMainAPI(AdiOMDb())
    }
}
