package com.AdicinemaxNew

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile

class VidsrcExtractor {
    companion object {
        suspend fun getStreamLinks(
            embedUrl: String,
            referer: String,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            return try {
                // Dapatkan HTML dari embed URL
                val document = app.get(embedUrl, referer = referer).document
                
                // Cari iframe utama
                val iframe = document.selectFirst("iframe")
                val iframeSrc = iframe?.attr("src")
                
                if (iframeSrc != null) {
                    // Jika iframeSrc adalah URL lengkap
                    if (iframeSrc.startsWith("http")) {
                        loadExtractor(iframeSrc, embedUrl, subtitleCallback, callback)
                    } else {
                        // Jika iframeSrc relative, buat URL lengkap
                        val fullIframeUrl = if (iframeSrc.startsWith("//")) {
                            "https:${iframeSrc}"
                        } else if (iframeSrc.startsWith("/")) {
                            "https://vidsrc-embed.ru${iframeSrc}"
                        } else {
                            "$referer/$iframeSrc"
                        }
                        loadExtractor(fullIframeUrl, embedUrl, subtitleCallback, callback)
                    }
                    true
                } else {
                    // Coba cari video player langsung
                    val videoElement = document.selectFirst("video")
                    val videoSource = videoElement?.selectFirst("source[src]")
                    val videoUrl = videoSource?.attr("src")
                    
                    if (videoUrl != null) {
                        callback(
                            ExtractorLink(
                                "Vidsrc",
                                "Vidsrc Direct",
                                videoUrl,
                                referer,
                                getQualityFromUrl(videoUrl),
                                isM3u8 = videoUrl.contains(".m3u8")
                            )
                        )
                        true
                    } else {
                        // Fallback ke extractor biasa
                        loadExtractor(embedUrl, referer, subtitleCallback, callback)
                        true
                    }
                }
            } catch (e: Exception) {
                // Fallback ke extractor biasa jika ada error
                try {
                    loadExtractor(embedUrl, referer, subtitleCallback, callback)
                    true
                } catch (e2: Exception) {
                    false
                }
            }
        }
        
        private fun getQualityFromUrl(url: String): Qualities {
            return when {
                url.contains("1080") -> Qualities.P1080
                url.contains("720") -> Qualities.P720
                url.contains("480") -> Qualities.P480
                url.contains("360") -> Qualities.P360
                url.contains("240") -> Qualities.P240
                else -> Qualities.Unknown
            }
        }
    }
}
