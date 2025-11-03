package com.Adi21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*

class VidplayExtractor : ExtractorApi() {
    override val name = "Vidplay"
    override val mainUrl = "https://vidplay.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val videoUrl = doc.select("video source").attr("src")

        return if (videoUrl.isNotBlank()) {
            listOf(
                newExtractorLink(
                    source = name,
                    name = "Vidplay",
                    url = videoUrl,
                    referer = url,
                    quality = getQualityFromName(videoUrl),
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        } else {
            emptyList()
        }
    }

    private fun getQualityFromName(url: String): Int {
        return when {
            "1080" in url -> Qualities.FHD.value
            "720" in url -> Qualities.HD.value
            "480" in url -> Qualities.SD.value
            "360" in url -> Qualities.SD.value
            else -> Qualities.Unknown.value
        }
    }
}
