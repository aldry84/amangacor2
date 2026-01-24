package com.Klikxxi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KlikxxiPlugin: Plugin() {
    override fun load(context: Context) {
        // PERBAIKAN: Meregistrasi KlikxxiProvider()
        registerMainAPI(KlikxxiProvider())
    }
}
