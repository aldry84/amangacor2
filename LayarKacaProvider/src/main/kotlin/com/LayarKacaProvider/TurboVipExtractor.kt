package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class TurboVipExtractor : ExtractorApi() {
    override var name = "TurboVip"
    override var mainUrl = "https://turbovidhls.com" 
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Gunakan referer default ke mainUrl jika kosong
        val finalReferer = referer ?: "$mainUrl/"
        
        // 1. Ambil halaman player (turbovidhls.com/t/...)
        val response = app.get(url, referer = finalReferer)
        val document = response.document
        val scriptHtml = document.select("script").html()
        
        val sources = mutableListOf<ExtractorLink>()
        
        // 2. Cari link m3u8 (cdn1.turboviplay.com) di dalam script
        // Regex ini mencari string yang berakhiran .m3u8 di dalam tanda kutip
        val m3u8Regex = Regex("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']")
        val match = m3u8Regex.find(scriptHtml)
        
        if (match != null) {
            val m3u8Url = match.groupValues[1]

            // 3. Header KUNCI (Hasil Analisa cURL Kamu)
            // Header ini akan dipakai player saat buka Master Playlist (cdn1)
            // DAN saat buka Child Playlist (c16/turbosplayer)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                "Referer" to "https://turbovidhls.com/",
                "Origin" to "https://turbovidhls.com" // <--- Ini yang bikin anti error 3001
            )

            sources.add(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://turbovidhls.com/"
                    // PENTING: Gunakan Unknown agar player CloudStream memproses resolusi (480p/720p) secara otomatis
                    // Jadi nanti tampilan rapi (1 sumber), tapi di setting player ada pilihan kualitasnya.
                    this.quality = Qualities.Unknown.value 
                    this.headers = headers
                }
            )
        }
        return sources
    }
}
