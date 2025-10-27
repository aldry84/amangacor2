package com.adimoviemaze

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.parseQuality
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object AdimoviemazeParser {
    fun parseMovieItems(items: Elements): List<SearchResponse> {
        return items.mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val quality = parseQuality(this.selectFirst("div.quality")?.text())
        return newMovieSearchResponse(title, href, Adimoviemaze.getType(href)) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    fun parseEpisodes(document: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        document.select("div.season").forEach { season ->
            val seasonNum = season.selectFirst("h3")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 1
            season.select("div.episode a").forEachIndexed { index, ep ->
                val epHref = fixUrl(ep.attr("href"))
                val epName = ep.text().trim()
                val epNum = index + 1
                episodes.add(Episode(epHref, epName, seasonNum, epNum))
            }
        }
        return episodes
    }
}
