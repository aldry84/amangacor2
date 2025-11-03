package com.Adicinemax

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdicinemaxPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan kelas Adicinemax
        registerMainAPI(Adicinemax()) 
    }
}
