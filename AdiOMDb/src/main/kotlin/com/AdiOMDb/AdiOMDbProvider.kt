package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AdiOMDbProvider: Plugin() { // Ganti PluginRepository menjadi Plugin()
    override fun load() { // Hapus parameter 'context' dan kata kunci 'override' pada 'load' (atau pastikan 'load()' adalah fungsi yang benar untuk di-override)
        // Mendaftarkan MainAPI
        registerMainAPI(AdiOMDb())
    }
}
