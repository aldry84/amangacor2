@file:Suppress("DEPRECATION")

package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
// IMPORT INI WAJIB ADA
import com.lagradost.cloudstream3.newExtractorLink

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

            // MENGGUNAKAN FACTORY FUNCTION TERBARU
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    referer ?: mainUrl,
                    Qualities.Unknown.value,
                    INFER_TYPE
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
