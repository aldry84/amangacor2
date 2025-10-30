package com.AdiOMDb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin 
import com.lagradost.cloudstream3.plugins.Plugin // Import ini seharusnya com.lagradost.cloudstream3.plugins.Plugin jika di versi Cloudstream yang lebih baru

@CloudstreamPlugin
class AdiOMDbProvider: BasePlugin() { // Mengubah Plugin() menjadi BasePlugin() jika menggunakan Cloudstream 4.x
    // Menggunakan BasePlugin() untuk konsistensi dengan versi Cloudstream yang lebih baru
    override fun load() { 
        registerMainAPI(AdiOMDb())
    }
}
