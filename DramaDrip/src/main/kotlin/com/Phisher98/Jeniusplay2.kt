package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

class Jeniusplay2 : ExtractorApi() {
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
            Log.d("Jeniusplay2", "Starting extraction for: $url")
            
            val document = app.get(url, referer = "$mainUrl/").document
            val hash = url.split("/").last().substringAfter("data=")

            Log.d("Jeniusplay2", "Found hash: $hash")

            val response = app.post(
                url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text

            Log.d("Jeniusplay2", "API response: ${response.take(200)}...")

            val videoSource = tryParseJson<ResponseSource>(response)?.videoSource
            if (videoSource != null) {
                Log.d("Jeniusplay2", "Found video source: $videoSource")
                
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        this.name,
                        videoSource,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                    }
                )
            } else {
                Log.e("Jeniusplay2", "No video source found in response")
            }

            // Extract subtitles
            document.select("script").forEach { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    try {
                        val unpacked = getAndUnpack(script.data())
                        Log.d("Jeniusplay2", "Unpacked script: ${unpacked.take(200)}...")
                        
                        val subData = unpacked.substringAfter("\"tracks\":[").substringBefore("],")
                        tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    getLanguage(subtitle.label ?: ""),
                                    subtitle.file
                                )
                            )
                            Log.d("Jeniusplay2", "Found subtitle: ${subtitle.label}")
                        }
                    } catch (e: Exception) {
                        Log.e("Jeniusplay2", "Error extracting subtitles: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Jeniusplay2", "Extraction failed: ${e.message}")
            throw e
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            str.contains("english", true) || str.contains("inggris", true) -> "English"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )
}
