package com.AsianDrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object AsianDramaExtractors {
    
    // ==================== IDLIX EXTRACTOR ====================
    class IdlixExtractor : ExtractorApi() {
        override val name = "Idlix"
        override val mainUrl = "https://tv6.idlixku.com"
        override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            try {
                val document = app.get(url, referer = referer).document
                
                // Look for embedded video players
                document.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotBlank() && !src.contains("youtube")) {
                        loadExtractor(src, referer ?: mainUrl, subtitleCallback, callback)
                    }
                }

                // Look for direct video links in scripts
                document.select("script").forEach { script ->
                    val scriptData = script.data()
                    if (scriptData.contains("sources")) {
                        extractFromScript(scriptData, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private suspend fun extractFromScript(
            scriptData: String, 
            subtitleCallback: (SubtitleFile) -> Unit, 
            callback: (ExtractorLink) -> Unit
        ) {
            try {
                // Extract M3U8 URLs from JavaScript
                val m3u8Regex = """(https?://[^"'\]\s]+\.m3u8[^"'\]\s]*)""".toRegex()
                val matches = m3u8Regex.findAll(scriptData)
                
                matches.forEach { match ->
                    val url = match.value
                    // Use non-suspend version of callback
                    callback.invoke(
                        newExtractorLink(
                            name,
                            name,
                            url,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = mainUrl
                        }
                    )
                }

                // Extract subtitles
                val subRegex = """file":"([^"]+\.vtt)","label":"([^"]+)"""".toRegex()
                val subMatches = subRegex.findAll(scriptData)
                
                subMatches.forEach { match ->
                    val (subUrl, label) = match.destructured
                    subtitleCallback.invoke(
                        SubtitleFile(label, subUrl)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==================== MAPPLE EXTRACTOR ====================
    class MappleExtractor : ExtractorApi() {
        override val name = "Mapple"
        override val mainUrl = "https://mapple.uk"
        override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            try {
                val response = app.get(url, referer = referer)
                val document = response.document

                // Look for direct video links
                document.select("video source").forEach { source ->
                    val src = source.attr("src")
                    if (src.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                name,
                                name,
                                src,
                                ExtractorLinkType.VIDEO
                            ) {
                                this.referer = url
                            }
                        )
                    }
                }

                // Look for M3U8 in scripts
                document.select("script").forEach { script ->
                    val scriptData = script.data()
                    if (scriptData.contains(".m3u8")) {
                        val m3u8Regex = """(https?://[^"'\]\s]+\.m3u8[^"'\]\s]*)""".toRegex()
                        m3u8Regex.findAll(scriptData).forEach { match ->
                            callback.invoke(
                                newExtractorLink(
                                    name,
                                    name,
                                    match.value,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = url
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==================== WYZIE EXTRACTOR ====================
    class WyzieExtractor : ExtractorApi() {
        override val name = "Wyzie"
        override val mainUrl = "https://sub.wyzie.ru"
        override val requiresReferer = false

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            // Wyzie specializes in subtitles only
            try {
                // For Asian drama, we'll provide common subtitle languages
                val commonSubtitles = listOf(
                    "Indonesian" to "https://example.com/sub/id.vtt",
                    "English" to "https://example.com/sub/en.vtt",
                    "Chinese" to "https://example.com/sub/zh.vtt"
                )

                commonSubtitles.forEach { (lang, subUrl) ->
                    subtitleCallback.invoke(
                        SubtitleFile(lang, subUrl)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==================== GOMOVIES EXTRACTOR ====================
    class GomoviesExtractor : ExtractorApi() {
        override val name = "Gomovies"
        override val mainUrl = "https://gomovies-online.cam"
        override val requiresReferer = true

        override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            try {
                val document = app.get(url, referer = referer).document

                // Extract from embedded players
                document.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotBlank() && src.contains("gomovies")) {
                        // Process Gomovies embedded player
                        extractFromGomoviesEmbed(src, subtitleCallback, callback)
                    }
                }

                // Direct video links
                document.select("a[href*=.m3u8], a[href*=.mp4]").forEach { link ->
                    val href = link.attr("href")
                    if (href.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                name,
                                name,
                                href,
                                if (href.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = url
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private suspend fun extractFromGomoviesEmbed(
            url: String, 
            subtitleCallback: (SubtitleFile) -> Unit, 
            callback: (ExtractorLink) -> Unit
        ) {
            try {
                val document = app.get(url).document
                
                // Look for video sources in embedded content
                document.select("script").forEach { script ->
                    val scriptData = script.data()
                    if (scriptData.contains("sources")) {
                        val sourceRegex = """src:\s*["'](https?://[^"']+)["']""".toRegex()
                        sourceRegex.findAll(scriptData).forEach { match ->
                            val videoUrl = match.groupValues[1]
                            callback.invoke(
                                newExtractorLink(
                                    name,
                                    name,
                                    videoUrl,
                                    if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = url
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
