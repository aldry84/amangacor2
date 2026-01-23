package com.Idlixku

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.MainAPI
import com.fasterxml.jackson.annotation.JsonProperty

class JeniusPlayExtractor : MainAPI() {

    suspend fun getVideo(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val videoId = url.substringAfter("/video/")
            val domain = "https://jeniusplay.com"

            val jsonResponse = app.post(
                "$domain/player/index.php?data=$videoId&do=getVideo",
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url
                ),
                data = mapOf("hash" to videoId, "r" to "")
            ).parsedSafe<JeniusResponse>()

            val playlistUrl = jsonResponse?.videoSource ?: return

            [span_7](start_span)// FIX: Menggunakan newExtractorLink agar tidak deprecated[span_7](end_span)
            callback.invoke(
                newExtractorLink(
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
    
    // Helper function untuk newExtractorLink (biasanya ada di MainAPI, tapi kita buat manual jika tidak ter-inherit)
    private fun newExtractorLink(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean
    ): ExtractorLink {
        return ExtractorLink(
            source,
            name,
            url,
            referer,
            quality,
            isM3u8
        )
    }
}
