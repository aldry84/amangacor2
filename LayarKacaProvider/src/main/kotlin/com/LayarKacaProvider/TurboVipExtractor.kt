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
        // Gunakan referer dari parameter atau default ke mainUrl
        val finalReferer = referer ?: "$mainUrl/"
        
        // 1. Ambil halaman playernya
        val response = app.get(url, referer = finalReferer)
        val document = response.document
        
        // 2. Cari link m3u8 di dalam script (biasanya variabel urlPlay atau file:)
        // Kita pakai Regex yang lebih ganas biar kena polanya
        val scriptHtml = document.select("script").html()
        
        val sources = mutableListOf<ExtractorLink>()
        
        // Regex untuk mencari link .m3u8 di dalam teks JavaScript
        val m3u8Regex = Regex("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']")
        val match = m3u8Regex.find(scriptHtml)
        
        if (match != null) {
            val m3u8Url = match.groupValues[1]

            // 3. Header Sakti Anti-Error 3001 (Sesuai cURL kamu)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to "https://turbovidhls.com/",
                "Origin" to "https://turbovidhls.com"
            )

            sources.add(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://turbovidhls.com/"
                    this.quality = Qualities.Unknown.value // Biar player deteksi 1080p/720p sendiri
                    this.headers = headers
                }
            )
        }
        return sources
    }
}
