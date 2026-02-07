package com.LayarKacaProvider

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

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
            
            // Tentukan Origin berdasarkan Referer (Embed URL)
            // Ini penting karena cURL kamu menunjukkan Origin: https://turbovidhls.com
            val originUrl = try {
                val uri = URI(finalReferer)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) {
                "$mainUrl"
            }

            // Headers Sakti Anti-Error 3001
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to finalReferer,
                "Origin" to originUrl // Header kunci dari analisa cURL kamu
            )

            // Gunakan Single Link (Rapi) tapi tipe M3U8
            // Player akan otomatis mendeteksi track 1080p, 720p, 480p di dalamnya
            sources.add(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = finalReferer
                    this.quality = Qualities.Unknown.value // Biarkan player yang menentukan kualitas
                    this.headers = headers
                }
            )
        }
        return sources
    }
}
