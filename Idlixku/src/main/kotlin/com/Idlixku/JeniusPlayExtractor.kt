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
        // PERBAIKAN: Logic ekstraksi ID yang lebih tangguh
        // Coba ambil dari parameter "data" dulu, kalau tidak ada baru dari path
        val id = if (url.contains("data=")) {
            url.substringAfter("data=").substringBefore("&")
        } else {
            url.substringAfter("/video/").substringBefore("/")
        }
        
        val apiUrl = "$mainUrl/player/index.php?data=$id&do=getVideo"

        runCatching {
            val headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )

            val response = app.post(apiUrl, headers = headers).parsedSafe<JeniusResponse>()
            
            val videoUrl = response?.securedLink ?: response?.videoSource ?: return@runCatching

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
        }.onFailure {
            it.printStackTrace()
        }
    }
}
