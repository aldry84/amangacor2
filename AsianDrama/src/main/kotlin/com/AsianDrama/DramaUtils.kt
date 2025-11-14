package com.AsianDrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Document

object DramaUtils {

    // Get enhanced metadata from Cinemeta
    suspend fun getCinemetaMetadata(tmdbId: Int?, type: String): CinemetaMeta? {
        if (tmdbId == null) return null
        try {
            val response = app.get("https://v3-cinemeta.strem.io/meta/$type/$tmdbId.json")
            return response.parsedSafe<CinemetaResponse>()?.meta
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Extract TMDB/IMDB IDs from DramaDrip page
    fun extractIdsFromDocument(document: Document): Pair<Int?, String?> {
        var tmdbId: Int? = null
        var imdbId: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }
            if (tmdbId == null && "themoviedb.org" in text) {
                tmdbId = Regex("/(movie|tv)/(\\d+)").find(text)?.groupValues?.get(2)?.toIntOrNull()
            }
        }

        return Pair(tmdbId, imdbId)
    }

    // Extract streaming links from DramaDrip page
    fun extractStreamingLinksFromDocument(document: Document): List<String> {
        val links = mutableListOf<String>()
        
        document.select("div.wp-block-button > a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank() && !href.contains("javascript")) {
                links.add(href)
            }
        }
        
        return links
    }

    // Extract episodes for TV series
    fun extractEpisodesFromDocument(document: Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        document.select("div.su-accordion h2").forEachIndexed { index, seasonHeader ->
            val seasonText = seasonHeader.text()
            if (!seasonText.contains("ZIP", ignoreCase = true)) {
                val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                    .find(seasonText)
                val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

                // Extract episode links from this season
                val episodeLinks = seasonHeader.nextElementSibling()
                    ?.select("div.wp-block-button a")
                    ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                    ?: emptyList()

                episodeLinks.forEachIndexed { epIndex, link ->
                    episodes.add(
                        newEpisode(link) {
                            this.name = "Episode ${epIndex + 1}"
                            this.season = season
                            this.episode = epIndex + 1
                        }
                    )
                }
            }
        }
        
        return episodes
    }
}
