package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parsedSafe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

class JeniusPlayExtractor : ExtractorApi() {
    override val name = "JeniusPlay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    private data class JeniusResponse(
        @JsonProperty("videoSource") val videoSource: String?,
        @JsonProperty("securedLink") val securedLink: String?
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Ambil ID video dari URL (misal: /video/0e8990...)
        val id = url.substringAfter("/video/").substringBefore("/")
        val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"

        try {
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )

            // Tembak API JeniusPlay
            val response = app.post(apiUrl, headers = headers).parsedSafe<JeniusResponse>()
            
            // Ambil link master.txt (m3u8)
            val videoUrl = response?.securedLink ?: response?.videoSource ?: return

            // Kirim ke CloudStream
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    videoUrl,
                    referer ?: mainUrl,
                    quality = Qualities.Unknown.value,
                    type = INFER_TYPE 
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
