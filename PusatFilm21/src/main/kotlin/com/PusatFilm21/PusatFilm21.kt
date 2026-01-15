package com.PusatFilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

class PusatFilm21 : MainAPI() {
    override var mainUrl = "https://v2.pusatfilm21info.net"
    override var name = "PusatFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    private val ajaxHeaders = mainHeaders + mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "*/*"
    )

    @Suppress("DEPRECATION")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = listOf(
            Pair("$mainUrl/trending/page/$page/", "Trending"),
            Pair("$mainUrl/movies/page/$page/", "Movies"),
            Pair("$mainUrl/tv-show/page/$page/", "TV Show"),
            Pair("$mainUrl/drama-korea/page/$page/", "Drama Korea"),
            Pair("$mainUrl/drama-china/page/$page/", "Drama China"),
            Pair("$mainUrl/west-series/page/$page/", "West Series"),
            Pair("$mainUrl/series-netflix/page/$page/", "Netflix Series"),
            Pair("$mainUrl/genre/action/page/$page/", "Action"),
            Pair("$mainUrl/genre/horror/page/$page/", "Horror")
        )

        val homeSets = items.mapNotNull { (url, name) ->
            try {
                val doc = app.get(url, headers = mainHeaders).document
                val movies = doc.select("div.ml-item").mapNotNull { element ->
                    toSearchResult(element)
                }
                if (movies.isNotEmpty()) HomePageList(name, movies) else null
            } catch (e: Exception) {
                null
            }
        }
        
        return newHomePageResponse(homeSets)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val linkElement = element.selectFirst("a.ml-mask") ?: return null
        val href = linkElement.attr("href")
        val title = linkElement.attr("title").ifEmpty { 
             element.select("span.mli-info").text() 
        }
        
        val imgElement = element.selectFirst("img")
        val posterUrl = imgElement?.attr("data-original")
            ?: imgElement?.attr("data-src")
            ?: imgElement?.attr("src")

        // FIX: Tambahkan "/tv/" agar Avatar series terdeteksi sebagai Series
        val isSeries = href.contains("/tv-show/") || href.contains("/drama-") || href.contains("series") || href.contains("/tv/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    data class SearchResponseJson(
        @JsonProperty("suggestions") val suggestions: List<Suggestion>?
    )

    data class Suggestion(
        @JsonProperty("value") val value: String,
        @JsonProperty("data") val data: String?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("url") val url: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wp-admin/admin-ajax.php?action=muvipro_core_ajax_search_movie&query=$query"
        try {
            val response = app.get(url, headers = ajaxHeaders).parsedSafe<SearchResponseJson>()
            return response?.suggestions?.mapNotNull { item ->
                val title = item.value
                val href = item.url
                val posterUrl = if (item.thumb != null) {
                    Regex("""src="([^"]+)"""").find(item.thumb)?.groupValues?.get(1)
                } else null

                // FIX: Tambahkan "/tv/" di sini juga
                val isSeries = href.contains("/tv-show/") || href.contains("/drama-") || href.contains("/tv/")
                if (isSeries) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mainHeaders).document
        
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src")
            ?: doc.selectFirst("div.mvic-thumb img")?.attr("src")
        val description = doc.select("div.entry-content p").text() 
            ?: doc.select("div.desc").text()

        // FIX: Tambahkan "/tv/" di sini juga
        val isSeries = url.contains("/tv-show/") || url.contains("/drama-") || url.contains("series") || url.contains("/tv/")

        // Ambil Trailer YouTube jika ada
        val trailerUrl = doc.select("iframe[src*='youtube.com']").attr("src")

        if (isSeries) {
            val episodes = ArrayList<Episode>()
            
            doc.select("span.gmr-eps-list a").forEach { ep ->
                episodes.add(newEpisode(ep.attr("href")) {
                    this.name = ep.text()
                })
            }
            
            if (episodes.isEmpty()) {
                doc.select("div.tv-eps a").forEach { ep ->
                    episodes.add(newEpisode(ep.attr("href")) {
                        this.name = ep.text()
                    })
                }
            }
            
            episodes.reverse()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                addTrailer(trailerUrl) // Menambahkan trailer
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                addTrailer(trailerUrl) // Menambahkan trailer
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mainHeaders).document
        
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            if (sourceUrl.startsWith("//")) sourceUrl = "https:$sourceUrl"
            loadExtractor(sourceUrl, data, subtitleCallback, callback)
        }
        
        doc.select("a.gmr-player-link").forEach { link ->
             val sourceUrl = link.attr("href")
             loadExtractor(sourceUrl, data, subtitleCallback, callback)
        }
        
        return true
    }
}
