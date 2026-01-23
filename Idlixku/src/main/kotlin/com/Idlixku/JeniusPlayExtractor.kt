package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

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
        val id = url.substringAfter("/video/").substringBefore("/")
        val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"

        try {
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )

            val responseText = app.post(apiUrl, headers = headers).text
            val response = tryParseJson<JeniusResponse>(responseText)
            
            val videoUrl = response?.securedLink ?: response?.videoSource ?: return

            // MENGGUNAKAN GAYA ADIMOVIEBOX (newExtractorLink dengan lambda)
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    INFER_TYPE
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
