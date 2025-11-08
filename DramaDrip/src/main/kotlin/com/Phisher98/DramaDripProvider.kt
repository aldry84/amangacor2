// DramaDrip/src/main/kotlin/com/Phisher98/DramaDripProvider.kt

package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DramaDripProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(DramaDrip())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(VidSrcEmbedExtractor())
    }
    
    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        private var cachedDomains: Domains? = null
        private var cacheTimestamp: Long = 0
        private const val CACHE_DURATION = 30 * 60 * 1000 // 30 minutes

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            val now = System.currentTimeMillis()
            if (forceRefresh || cachedDomains == null || now - cacheTimestamp > CACHE_DURATION) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                    cacheTimestamp = now
                    Log.d("DramaDrip", "Domains cache updated: ${cachedDomains?.dramadrip}")
                } catch (e: Exception) {
                    Log.e("DramaDrip", "Failed to fetch domains: ${e.message}")
                    // Return cached version even if expired if fetch fails
                    if (cachedDomains == null) return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            @JsonProperty("dramadrip")
            val dramadrip: String,
        )
    }
}
