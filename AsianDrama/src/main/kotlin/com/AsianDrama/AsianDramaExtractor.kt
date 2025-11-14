package com.AsianDrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object AsianDramaExtractor {

    suspend fun extractForMovie(
        data: AsianDrama.MovieStreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Priority 1: Use TMDB/IMDB IDs for reliable sources
        data.tmdbId?.let { tmdbId ->
            extractFromVidsrc(tmdbId, data.imdbId, "movie", null, null, subtitleCallback, callback)
        }

        // Priority 2: Use title-based search as fallback
        data.title?.let { title ->
            searchAndExtract(title, "movie", subtitleCallback, callback)
        }
    }

    suspend fun extractForEpisode(
        data: AsianDrama.EpisodeStreamData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Priority 1: Direct episode URL from DramaDrip
        data.episodeUrl?.let { url ->
            extractFromDirectUrl(url, subtitleCallback, callback)
        }

        // Priority 2: Use TMDB/IMDB IDs
        data.tmdbId?.let { tmdbId ->
            extractFromVidsrc(tmdbId, data.imdbId, "tv", data.season, data.episode, subtitleCallback, callback)
        }

        // Priority 3: Use title-based search
        data.title?.let { title ->
            searchAndExtract("$title S${data.season}E${data.episode}", "tv", subtitleCallback, callback)
        }
    }

    private suspend fun extractFromVidsrc(
        tmdbId: Int,
        imdbId: String?,
        type: String,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val url = if (type == "movie") {
                "https://vidsrc.cc/v2/embed/movie/$tmdbId"
            } else {
                "https://vidsrc.cc/v2/embed/tv/$tmdbId/${season ?: 1}/${episode ?: 1}"
            }

            val document = app.get(url).document
            
            // Extract from iframes
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }

            // Extract direct video links
            document.select("script").forEach { script ->
                val scriptContent = script.html()
                if (scriptContent.contains("source")) {
                    extractFromScript(scriptContent, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            // Continue with other methods
        }
    }

    private suspend fun extractFromDirectUrl(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            
            // Look for direct video links
            document.select("video source").forEach { source ->
                val videoUrl = source.attr("src")
                if (videoUrl.isNotBlank() && (videoUrl.endsWith(".mp4") || videoUrl.contains("m3u8"))) {
                    callback.invoke(
                        newExtractorLink(
                            "Direct",
                            "Direct Video",
                            videoUrl,
                            if (videoUrl.endsWith(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                        )
                    )
                }
            }

            // Look for iframes
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }

            // Look for embedded players
            document.select("a[href*='watch'], a[href*='play'], a[href*='stream']").forEach { link ->
                val href = link.attr("href")
                if (href.isNotBlank()) {
                    loadExtractor(href, url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            // Continue with other methods
        }
    }

    private suspend fun searchAndExtract(
        query: String,
        type: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Use external API to search for streaming links
            val searchUrl = "https://vidsrc.cc/v2/search/$query"
            val document = app.get(searchUrl).document
            
            document.select("a[href*='/embed/']").forEach { result ->
                val embedUrl = result.attr("href")
                if (embedUrl.isNotBlank()) {
                    loadExtractor("https://vidsrc.cc$embedUrl", searchUrl, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            // Fallback to multi-source search
            fallbackSearch(query, type, subtitleCallback, callback)
        }
    }

    private suspend fun fallbackSearch(
        query: String,
        type: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sources = listOf(
            "https://vidsrc.xyz/embed/$type?imdb=$query",
            "https://vidsrc.me/embed/$type?tmdb=$query",
            "https://vidsrc.net/embed/$type?imdb=$query"
        )

        sources.forEach { sourceUrl ->
            try {
                loadExtractor(sourceUrl, sourceUrl, subtitleCallback, callback)
            } catch (e: Exception) {
                // Continue to next source
            }
        }
    }

    private fun extractFromScript(
        scriptContent: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Extract MP4 links
        val mp4Links = Regex("""["']?file["']?\s*:\s*["']([^"']+\.mp4[^"']*)["']""").findAll(scriptContent)
        mp4Links.forEach { match ->
            val url = match.groupValues[1]
            callback.invoke(
                newExtractorLink(
                    "Direct MP4",
                    "Direct MP4",
                    url,
                    ExtractorLinkType.VIDEO
                )
            )
        }

        // Extract M3U8 links
        val m3u8Links = Regex("""["']?file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").findAll(scriptContent)
        m3u8Links.forEach { match ->
            val url = match.groupValues[1]
            callback.invoke(
                newExtractorLink(
                    "M3U8",
                    "M3U8 Stream",
                    url,
                    ExtractorLinkType.M3U8
                )
            )
        }

        // Extract subtitles
        val subtitleLinks = Regex("""["']?subtitle["']?\s*:\s*["']([^"']+\.vtt[^"']*)["']""").findAll(scriptContent)
        subtitleLinks.forEach { match ->
            val url = match.groupValues[1]
            subtitleCallback.invoke(
                SubtitleFile(
                    "English",
                    url
                )
            )
        }
    }
}
