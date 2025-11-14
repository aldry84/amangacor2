package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.api.Log

@CloudstreamPlugin
class DramaDripProvider: BasePlugin() {
    override fun load() {
        // Clear expired cache on plugin load
        clearExpiredCache()
        
        registerMainAPI(DramaDrip())
        registerExtractorAPI(Driveseed())
    }
    
    companion object {
        // TMDb Configuration - Consider using environment variables in production
        const val TMDB_API_KEY = "b030404650f279792a8d3287232358e3"
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
        
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        // Fallback domains in case GitHub is unavailable
        private val FALLBACK_DOMAINS = listOf(
            "https://dramadrip.com",
            "https://dramadrip.net",
            "https://dramadrip.org"
        )

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                    Log.d("DramaDrip", "Successfully fetched domains from GitHub")
                } catch (e: Exception) {
                    Log.e("DramaDrip", "Failed to fetch domains from GitHub: ${e.message}")
                    // Use fallback domains
                    cachedDomains = Domains(FALLBACK_DOMAINS.first())
                    Log.d("DramaDrip", "Using fallback domain: ${cachedDomains?.dramadrip}")
                }
            }
            return cachedDomains
        }

        suspend fun getAvailableDomain(): String {
            return try {
                val domains = getDomains()
                // Test if domain is reachable
                app.get("${domains?.dramadrip}/latest", timeout = 10)
                domains?.dramadrip ?: FALLBACK_DOMAINS.first()
            } catch (e: Exception) {
                Log.e("DramaDrip", "Primary domain unreachable, trying fallbacks")
                // Try fallback domains
                for (domain in FALLBACK_DOMAINS) {
                    try {
                        app.get("$domain/latest", timeout = 5)
                        Log.d("DramaDrip", "Using fallback domain: $domain")
                        return domain
                    } catch (e: Exception) {
                        Log.w("DramaDrip", "Fallback domain $domain failed: ${e.message}")
                    }
                }
                FALLBACK_DOMAINS.first() // Return first as last resort
            }
        }

        data class Domains(
            @JsonProperty("dramadrip")
            val dramadrip: String,
        )
    }
}
