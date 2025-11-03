package com.AdicinemaxNew // PACKAGE BARU

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdicinemaxNewPlugin: Plugin() { // NAMA KELAS PLUGIN BARU
    override fun load(context: Context) {
        // Mendaftarkan kelas Adicinemax (yang package-nya sudah diubah)
        registerMainAPI(Adicinemax()) 
    }
}
