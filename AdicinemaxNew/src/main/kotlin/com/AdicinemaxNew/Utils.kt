package com.AdicinemaxNew

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import java.net.URI
import com.AdicinemaxNew.AdicinemaxNew.Companion.apiKey
import com.AdicinemaxNew.AdicinemaxNew.Companion.OFFICIAL_TMDB_URL
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.net.URL
import java.security.MessageDigest
import com.lagradost.cloudstream3.utils.getQualityFromName

// ASUMSI: Utils file ini berisi semua fungsi pembantu dari StreamPlayUtils.kt yang diperlukan,
// termasuk implementasi penuh dari GDFlix dan HubCloud sebagai ExtractorApi.

object Utils {
    // TMDB Proxy Logic (Diambil dari StreamPlay.kt)
    private var currentBaseUrl: String? = null
    // ASUMSI: REMOTE_PROXY_LIST ada di BuildConfig atau hardcode jika diperlukan

    // --- TMDB Logic (Perlu diimplementasikan penuh) ---
    suspend fun getApiBase(): String {
        // ... (Logika pemeriksaan ketersediaan TMDB resmi dan proxy)
        return currentBaseUrl ?: OFFICIAL_TMDB_URL 
    }
    
    fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }
    
    // --- Vidsrc Logic (Contoh) ---
    fun generateVrfAES(movieId: String, userId: String): String {
        // ... (Implementasi penuh generateVrfAES dari StreamPlayUtils)
        return "dummy_vrf_hash"
    }

    suspend fun extractIframeUrl(url: String): String? {
        // ... (Implementasi penuh extractIframeUrl dari StreamPlayUtils)
        return app.get(url).document.select("iframe").attr("src").takeIf { it.isNotEmpty() }
    }
    
    // --- Torrent Logic ---
    suspend fun generateMagnetLink(trackerUrl: String, hash: String?): String {
        // ... (Implementasi penuh generateMagnetLink dari StreamPlayTorrent.kt)
        val response = app.get(trackerUrl)
        val trackerList = response.text.trim().split("\n")
        return buildString {
            append("magnet:?xt=urn:btih:$hash")
            trackerList.forEach { tracker ->
                if (tracker.isNotBlank()) { append("&tr=").append(tracker.trim()) }
            }
        }
    }
    
    fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
    
    fun String.createSlug(): String? {
        return this.filter { it.isWhitespace() || it.isLetterOrDigit() }
            .trim()
            .replace("\\s+".toRegex(), "-")
            .lowercase()
    }
    
    // Placeholder untuk ekstrakto GDFlix dan HubCloud yang diwarisi
    class GDFlix : com.lagradost.cloudstream3.MainAPI.ExtractorApi() {
        override val name = "GDFlix"
        override val mainUrl = AdicinemaxExtractor.GDFlixAPI
        override val requiresReferer = false
        override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            // Logika GDFlix di sini
        }
    }

    class HubCloud : com.lagradost.cloudstream3.MainAPI.ExtractorApi() {
        override val name = "HubCloud"
        override val mainUrl = AdicinemaxExtractor.HubCloudAPI
        override val requiresReferer = false
        override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            // Logika HubCloud di sini
        }
    }
}
