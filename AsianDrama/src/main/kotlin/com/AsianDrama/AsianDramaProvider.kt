package com.AsianDrama

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AsianDramaProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AsianDrama())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Jeniusplay2())
    }
}
