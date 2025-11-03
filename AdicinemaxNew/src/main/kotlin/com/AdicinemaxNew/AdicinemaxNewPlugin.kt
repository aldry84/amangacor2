package com.AdicinemaxNew // PACKAGE BARU UNIK

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdicinemaxNewPlugin: BasePlugin() { 
    
    override fun load() {
        // MEMANGGIL KELAS API DENGAN NAMA BARU
        registerMainAPI(AdicinemaxProvider()) 
    }
}
