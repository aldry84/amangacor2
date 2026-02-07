package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val finalReferer = referer ?: "$mainUrl/"
        
        val response = app.get(url, referer = finalReferer)
        val playerScript = response.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()

        val sources = mutableListOf<ExtractorLink>()
        
        if (playerScript.isNotBlank()) {
            val m3u8Url = playerScript.substringAfter("var urlPlay = '").substringBefore("'")

            sources.add(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = finalReferer
                    // Pakai Unknown agar muncul 1 sumber saja di list
                    // Tapi Video Tracks di dalam player akan muncul banyak (1080, 720, dll)
                    this.quality = Qualities.Unknown.value
                    
                    // HEADER FIX 3001:
                    // Header ini akan dipakai player saat request chunk video selanjutnya
                    this.headers = mapOf(
                        "Referer" to finalReferer,
                        "Origin" to "https://emturbovid.com",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                }
            )
        }
        return sources
    }
}
