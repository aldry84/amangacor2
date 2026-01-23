package com.Idlixku

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.MainAPI
import com.fasterxml.jackson.annotation.JsonProperty

class JeniusPlayExtractor : MainAPI() { // FIX: Extend MainAPI

    suspend fun getVideo(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val videoId = url.substringAfter("/video/")
            val domain = "https://jeniusplay.com"

            // FIX: 'app' sekarang bisa diakses karena extend MainAPI
            val jsonResponse = app.post(
                "$domain/player/index.php?data=$videoId&do=getVideo",
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url
                ),
                data = mapOf("hash" to videoId, "r" to "")
            ).parsedSafe<JeniusResponse>()

            val playlistUrl = jsonResponse?.videoSource ?: return

            // FIX: Generate Extractor Link
            callback.invoke(
                ExtractorLink(
                    source = "JeniusPlay",
                    name = "JeniusPlay (Auto)",
                    url = playlistUrl,
                    referer = domain,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class JeniusResponse(
        @JsonProperty("videoSource") val videoSource: String?,
        @JsonProperty("securedLink") val securedLink: String?
    )
}
