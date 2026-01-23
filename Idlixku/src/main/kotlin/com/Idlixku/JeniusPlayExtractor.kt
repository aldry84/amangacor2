package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class JeniusPlayExtractor : ExtractorApi() { // Nama disamakan
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    data class ResponseSource(
        @param:JsonProperty("videoSource") val videoSource: String? = null,
        @param:JsonProperty("securedLink") val securedLink: String? = null
    )

    data class Tracks(
        @param:JsonProperty("file") val file: String,
        @param:JsonProperty("label") val label: String? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer)
        val document = res.document
        val hash = url.substringAfter("data=").substringBefore("&")

        val response = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to (referer ?: "")),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<ResponseSource>()

        val m3uLink = response?.securedLink ?: response?.videoSource

        if (!m3uLink.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        document.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(scriptData)
                if (unpacked.contains("\"tracks\":[")) {
                    val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                    AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                getLanguage(subtitle.label ?: ""),
                                subtitle.file
                            )
                        )
                    }
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            str.contains("english", true) -> "English"
            else -> str
        }
    }
}
