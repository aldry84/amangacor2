package com.hexated

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdimovieboxPlugin : Plugin() {
    override fun load(context: Context) {
        // Semua provider harus diregistrasi seperti ini.
        // Jangan ubah daftar provider secara langsung.
        registerMainAPI(Adimoviebox())
    }
}
