package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.app

@CloudstreamPlugin
class Movie21Provider: BasePlugin() {
    override fun load() {
        registerMainAPI(Movie21())
    }
    companion object {
        // URL untuk domain jika diperlukan (hanya untuk referensi)
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    // Hanya untuk memuat base URL streaming jika diperlukan
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>() 
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            // Asumsi kunci ini ada di domains.json Anda
            @JsonProperty("movie21")
            val movie21: String, 
        )
    }
}
