package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin // <-- Ganti PluginRepository
import android.content.Context

@CloudstreamPlugin
class AdiOMDbProvider: Plugin() { // <-- Mengganti warisan ke Plugin()
    override fun load() { // <-- Fungsi load() tanpa parameter Context
        // Mendaftarkan MainAPI
        registerMainAPI(AdiOMDb())
    }
}
