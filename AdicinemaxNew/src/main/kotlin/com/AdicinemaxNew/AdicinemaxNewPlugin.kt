package com.AdicinemaxNew 

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdicinemaxNewPlugin: BasePlugin() { 
    
    override fun load() {
        // PANGGIL KELAS DENGAN NAMA BARU
        registerMainAPI(AdicinemaxProvider()) 
    }
}
