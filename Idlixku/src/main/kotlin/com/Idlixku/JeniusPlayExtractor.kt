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

        // 1. COBA API
        if (id.isNotEmpty()) {
            val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
            try {
                val headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url,
                )
                val text = app.post(apiUrl, headers = headers).text
                val json = tryParseJson<JeniusResponse>(text)
                foundLink = json?.securedLink ?: json?.videoSource
            } catch (e: Exception) {
                // Lanjut ke fallback
            }
        }

        // 2. FALLBACK SCRAPING HTML (Cari string "file":"..." atau "source":"...")
        if (foundLink.isNullOrEmpty()) {
            try {
                val document = app.get(url).text
                // Regex untuk menangkap URL di dalam tanda kutip setelah file: atau source:
                // Menangani 'file': "url", file: "url", dll.
                val regex = Regex("""(file|source)\s*[:=]\s*["']([^"']+)["']""")
                val match = regex.find(document)
                foundLink = match?.groupValues?.get(2)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!foundLink.isNullOrEmpty()) {
            // Fix URL jika formatnya //domain.com
            val finalUrl = if (foundLink!!.startsWith("//")) "https:$foundLink" else foundLink!!
            
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    INFER_TYPE
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
