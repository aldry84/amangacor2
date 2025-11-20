package com.AdiDrakor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ioSafe
import android.content.Context

@CloudstreamPlugin
class AdiDrakorPlugin : Plugin() {
    override fun load(context: Context) {
        // ==========================================
        // PERBAIKAN: UPDATE DOMAIN SAAT STARTUP
        // ==========================================
        // Menjalankan update domain di background thread agar tidak memblokir UI
        ioSafe {
            DomainManager.updateDomains()
        }

        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AdiDrakor())
        registerExtractorAPI(Jeniusplay2())
    }
}
