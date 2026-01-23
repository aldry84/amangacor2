package com.Idlixku

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class JeniusPlayExtractor : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    // 1. PINDAHKAN DATA CLASS DARI HEXATED KE SINI (Agar tidak perlu import luar)
    data class ResponseSource(
        @param:JsonProperty("videoSource") val videoSource: String? = null,
        @param:JsonProperty("securedLink") val securedLink: String? = null
    )

    data class Tracks(
        @param:JsonProperty("file") val file: String,
        @param:JsonProperty("label") val label: String? = null,
        @param:JsonProperty("kind") val kind: String? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        
        // Ambil Hash ID
        val hash = if (url.contains("data=")) {
            url.substringAfter("data=").substringBefore("&")
        } else {
            url.split("/").last()
        }

        // Request Link Video (M3U8)
        val response = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to (referer ?: "https://tv12.idlixku.com/")),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<ResponseSource>()

        val m3uLink = response?.securedLink ?: response?.videoSource

        if (!m3uLink.isNullOrEmpty()) {
            // Gunakan format newExtractorLink yang kita tahu berhasil di compiler kamu
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                }
            )
        }

        // --- LOGIKA SUBTITLE (UNPACKER) ---
        // Mencari subtitle di dalam script yang ter-enkripsi (P.A.C.K.E.R)
        document.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("eval(function(p,a,c,k,e,d)")) {
                try {
                    // Unpack script JS
                    val unpacked = JsUnpacker.unpackAndGet(scriptData) ?: ""
                    if (unpacked.contains("\"tracks\":[")) {
                        val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                        // Parse JSON Subtitle
                        tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                            subtitleCallback.invoke(
                                newSubtitleFile(
                                    getLanguage(subtitle.label ?: ""),
                                    subtitle.file
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
