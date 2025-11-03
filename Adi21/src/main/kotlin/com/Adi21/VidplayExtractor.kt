package com.Adi21

import com.lagradost.cloudstream3.*
// Fix: Import correct base class 'Extractor'
import com.lagradost.cloudstream3.extractors.Extractor
// Fix: Import ExtractorLink
import com.lagradost.cloudstream3.extractors.ExtractorLink
// Fix: Import Qualities
import com.lagradost.cloudstream3.utils.Qualities

// Fix: Inherit from Extractor
class VidplayExtractor : Extractor() {
    override var name = "Vidplay"
    override var mainUrl = "https://vidplay.to"
    override var requiresReferer = false

    // Fix: Add suspend modifier, ensure override is correct
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val videoUrl = doc.select("video source").attr("src")

        return if (videoUrl.isNotBlank()) {
            listOf(
                // Fix: Use newExtractorLink factory method
                newExtractorLink(
                    source = name,
                    name = name,
                    url = videoUrl,
                    referer = url,
                    quality = getQualityFromUrl(videoUrl),
                ) {
                    isM3u8 = videoUrl.endsWith(".m3u8")
                }
            )
        } else {
            emptyList()
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            "1080" in url -> 1080
            "720" in url -> 720
            "480" in url -> 480
            "360" in url -> 360
            // Fix: Use Qualities enum
            else -> Qualities.Unknown.value
        }
    }
}
