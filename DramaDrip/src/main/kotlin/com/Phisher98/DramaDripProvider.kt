package com.Phisher98

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DramaDripProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(DramaDrip())
        // Hapus baris berikut:
        // registerExtractorAPI(Driveseed())
        
        // Tambahkan Jeniusplay2 extractor
        registerExtractorAPI(Jeniusplay2())
    }
    
    companion object {
        // TMDb Configuration - tetap sama
        const val TMDB_API_KEY = "b030404650f279792a8d3287232358e3"
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
        
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
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
