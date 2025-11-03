package com.AdicinemaxNew

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
// HAPUS import android.content.Context (Karena ini adalah import Android)

@CloudstreamPlugin
class AdicinemaxNewPlugin: Plugin() {
    // GANTI 'Context' dengan 'Any'
    override fun load(context: Any) {
        registerMainAPI(Adicinemax()) 
    }
}
