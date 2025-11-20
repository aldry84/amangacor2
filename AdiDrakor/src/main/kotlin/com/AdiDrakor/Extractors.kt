package com.AdiDrakor

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Jeniusplay2 : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url, referer = "$mainUrl/").document
            val hash = url.split("/").last().substringAfter("data=")

            val m3uLink = app.post(
                url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseSource>()?.videoSource

            if (m3uLink != null) {
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        m3uLink,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                    }
                )
            }

            document.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val unpacked = getAndUnpack(script.data())
                    val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                    
                    if (subData.isNotBlank()) {
                        tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                            // Menambahkan suppress agar tidak muncul peringatan (warning) saat kompilasi
                            @Suppress("DEPRECATION")
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    getLanguage(subtitle.label ?: ""),
                                    subtitle.file
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error jika diperlukan, atau biarkan gagal diam-diam agar provider lain bisa mencoba
            e.printStackTrace()
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str
                .contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean? = null,
        @JsonProperty("videoSource") val videoSource: String? = null,
        @JsonProperty("securedLink") val securedLink: String? = null,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String? = null,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String? = null,
    )
}
