package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

@Suppress("DEPRECATION") 
class JeniusPlayExtractor : ExtractorApi() {
    override val name = "JeniusPlay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    // PERBAIKAN: Menggunakan @param:JsonProperty untuk menghilangkan warning
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
        val id = url.substringAfter("/video/").substringBefore("/")
        val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"

        try {
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )

            val responseText = app.post(apiUrl, headers = headers).text
            
            // Gunakan AppUtils.tryParseJson agar aman
            val response = AppUtils.tryParseJson<JeniusResponse>(responseText)
            
            val videoUrl = response?.securedLink ?: response?.videoSource ?: return

            val link = ExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                referer = referer ?: mainUrl,
                quality = Qualities.Unknown.value,
                type = INFER_TYPE,
                headers = mapOf(),
                extractorData = null
            )
            
            callback.invoke(link)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
