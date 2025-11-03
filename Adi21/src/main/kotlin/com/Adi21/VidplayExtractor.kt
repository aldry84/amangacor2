package com.Adi21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.ExtractorApi

class VidplayExtractor : ExtractorApi() {
    override val name = "Vidplay"
    override val mainUrl = "https://vidplay.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val doc = app.get(url).document
        val videoUrl = doc.select("video source").attr("src")

        return if (videoUrl.isNotBlank()) {
            listOf(
                ExtractorLink(
                    name = name,
                    source = name,
                    url = videoUrl,
                    referer = url,
                    quality = getQualityFromUrl(videoUrl),
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        } else {
            emptyList()
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            "1080
