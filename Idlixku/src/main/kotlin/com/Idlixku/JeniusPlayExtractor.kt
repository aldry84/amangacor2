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

        // 1. Ambil link melalui API POST (Metode paling valid sekarang)
        if (id.isNotEmpty()) {
            val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"
            try {
                // Header harus lengkap sesuai cURL kamu
                val headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url,
                    "Origin" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )

                val bodyData = mapOf(
                    "hash" to id,
                    "r" to (referer ?: "https://tv12.idlixku.com/")
                )

                val text = app.post(apiUrl, headers = headers, data = bodyData).text
                val json = tryParseJson<JeniusResponse>(text)
                foundLink = json?.securedLink ?: json?.videoSource
            } catch (e: Exception) { }
        }

        // 2. Fallback: Cari link /m3/ atau master.txt di HTML
        if (foundLink.isNullOrEmpty()) {
            try {
                val document = app.get(url).text
                val regexM3 = Regex("""["'](https?://jeniusplay\.com/m3/[^"']+)["']""")
                val regexMaster = Regex("""["']([^"']+\/master\.txt[^"']*)["']""")
                
                foundLink = regexM3.find(document)?.groupValues?.get(1)
                    ?: regexMaster.find(document)?.groupValues?.get(1)
            } catch (e: Exception) { }
        }

        if (!foundLink.isNullOrEmpty()) {
            var finalUrl = foundLink!!
            if (finalUrl.startsWith("//")) finalUrl = "https:$finalUrl"

            // Berdasarkan temuan kamu, link /m3/ atau master.txt PASTI M3U8
            val isHls = finalUrl.contains("/m3/") || 
                        finalUrl.contains("master.txt") || 
                        finalUrl.contains("/hls/")

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    finalUrl,
                    url, // Referer sangat penting di sini
                    Qualities.Unknown.value,
                    if (isHls) ExtractorLinkType.M3U8 else INFER_TYPE
                ) {
                    // Tambahkan header khusus agar server jeniusplay tidak memblokir player
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                        "Origin" to "https://jeniusplay.com"
                    )
                }
            )
        }
    }
}
