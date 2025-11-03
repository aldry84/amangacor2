package com.AdicinemaxNew

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
// Hapus import android.content.Context

@CloudstreamPlugin
class AdicinemaxNewPlugin: Plugin() {
    
    // Menggunakan load() tanpa parameter: Ini adalah signature yang valid
    // untuk plugin cross-platform/non-Android.
    override fun load() {
        registerMainAPI(Adicinemax()) 
    }
}
