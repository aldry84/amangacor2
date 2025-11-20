package com.AdiDrakor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@CloudstreamPlugin
class AdiDrakorPlugin : Plugin() {
    override fun load(context: Context) {
        // ==========================================
        // PERBAIKAN: COROUTINE SCOPE & IMPORTS
        // ==========================================
        // Menggunakan GlobalScope.launch karena ioSafe tidak tersedia
        // Ini menjalankan update domain di background
        
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            DomainManager.updateDomains()
        }

        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AdiDrakor())
        registerExtractorAPI(Jeniusplay2())
    }
}
