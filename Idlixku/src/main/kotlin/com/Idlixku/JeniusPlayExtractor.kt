package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.* import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class JeniusPlayExtractor : ExtractorApi() {
    override val name = "JeniusPlay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    private data class JeniusResponse(
        @param:JsonProperty("videoSource") val videoSource: String?,
        @param:JsonProperty("securedLink") val securedLink: String?
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = when {
            url.contains("data=") -> url.substringAfter("data=").substringBefore("&")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("/")
            url.contains("/video/") -> url.substringAfter("/video/").substringBefore("/")
            else -> ""
        }

        var foundLink: String? = null

        // 1. COBA API (Prioritas Utama)
        if (id.isNotEmpty()) {
            val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
            try {
                val headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url,
                    "Origin" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )
                val text = app.post(apiUrl, headers = headers).text
                val json = tryParseJson<JeniusResponse>(text)
                foundLink = json?.securedLink ?: json?.videoSource
            } catch (e: Exception) {
                // Lanjut ke fallback
            }
        }

        // 2. FALLBACK SCRAPING HTML (Regex lebih agresif)
        if (foundLink.isNullOrEmpty()) {
            try {
                val document = app.get(url).text
                
                // Regex 1: Format standar file: "url"
                val regex1 = Regex("""(file|source)\s*[:=]\s*["']([^"']+)["']""")
                
                // Regex 2: Format master.txt langsung
                val regex2 = Regex("""["']([^"']+\/master\.txt[^"']*)["']""")
                
                // Regex 3: Format m3u8 umum
                val regex3 = Regex("""["']([^"']+\.m3u8[^"']*)["']""")

                foundLink = regex1.find(document)?.groupValues?.get(2)
                    ?: regex2.find(document)?.groupValues?.get(1)
                    ?: regex3.find(document)?.groupValues?.get(1)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!foundLink.isNullOrEmpty()) {
            var finalUrl = foundLink!!
            // Fix protocol //domain.com -> https://domain.com
            if (finalUrl.startsWith("//")) {
                finalUrl = "https:$finalUrl"
            }

            // Cek apakah ini M3U8 (termasuk .txt yang kamu temukan)
            val isM3u8 = finalUrl.contains(".m3u8") || 
                         finalUrl.contains("master.txt") || 
                         finalUrl.contains("/hls/")

            // PERBAIKAN DI SINI: Gunakan INFER_TYPE, bukan ExtractorLinkType.INFER
            val type = if (isM3u8) ExtractorLinkType.M3U8 else INFER_TYPE

            // PERBAIKAN DI SINI: Referer dan Quality masuk ke dalam lambda
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    type
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
