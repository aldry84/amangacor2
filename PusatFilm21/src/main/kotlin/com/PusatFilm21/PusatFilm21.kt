package com.PusatFilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import android.util.Log // Import untuk logging

class PusatFilm21 : MainAPI() {
    override var mainUrl = "https://v2.pusatfilm21info.net"
    override var name = "PusatFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val TAG = "PusatFilm21_DEBUG" // Tag untuk filter di Logcat

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val mainHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/",
        "Origin" to mainUrl,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1"
    )

    private val ajaxHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/",
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/javascript, */*; q=0.01"
    )

    @Suppress("DEPRECATION")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        Log.d(TAG, "Mencoba memuat halaman utama...")
        
        val items = listOf(
            Pair("$mainUrl/trending/page/$page/", "Trending"),
            Pair("$mainUrl/movies/page/$page/", "Movies"),
            Pair("$mainUrl/tv-show/page/$page/", "TV Show"),
            Pair("$mainUrl/drama-korea/page/$page/", "Drama Korea"),
            Pair("$mainUrl/drama-china/page/$page/", "Drama China")
        )

        val homeSets = items.mapNotNull { (url, name) ->
            try {
                Log.d(TAG, "Request ke: $url")
                val response = app.get(url, headers = mainHeaders)
                Log.d(TAG, "Response Code untuk $name: ${response.code}")

                val doc = response.document
                var movies = doc.select("div.ml-item").mapNotNull { toSearchResult(it) }

                if (movies.isEmpty()) {
                    Log.d(TAG, "Selector utama kosong untuk $name, mencoba backup...")
                    movies = doc.select("article.item-list").mapNotNull { toSearchResult(it) }
                }

                if (movies.isNotEmpty()) {
                    Log.d(TAG, "Berhasil menemukan ${movies.size} film untuk $name")
                    HomePageList(name, movies)
                } else {
                    Log.e(TAG, "Gagal menemukan film untuk $name. HTML mungkin berubah atau diblokir.")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saat memuat $name: ${e.message}")
                null
            }
        }
        
        return newHomePageResponse(homeSets)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val linkElement = element.selectFirst("a.ml-mask") ?: element.selectFirst("a")
        val href = linkElement?.attr("href")
        val title = linkElement?.attr("title")?.ifEmpty { element.select("span.mli-info").text() }
        val imgElement = element.selectFirst("img")
        val posterUrl = imgElement?.attr("data-original") ?: imgElement?.attr("data-src") ?: imgElement?.attr("src")

        if (href == null || title == null) return null

        val isSeries = href.contains("/tv-show/") || href.contains("/drama-") || href.contains("series") || href.contains("/tv/") || href.contains("/eps/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    // JSON Search
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
        val cleanQuery = query.replace(" ", "%20")
        val url = "$mainUrl/wp-admin/admin-ajax.php?action=muvipro_core_ajax_search_movie&query=$cleanQuery"
        Log.d(TAG, "Searching: $url")

        return try {
            val response = app.get(url, headers = ajaxHeaders).parsedSafe<SearchResponseJson>()
            Log.d(TAG, "Search Result: ${response?.suggestions?.size} items found")
            
            response?.suggestions?.mapNotNull { item ->
                val title = item.value
                val href = item.url
                val posterUrl = if (item.thumb != null) Regex("""src="([^"]+)"""").find(item.thumb)?.groupValues?.get(1) else null
                
                val isSeries = href.contains("/tv-show/") || href.contains("/drama-") || href.contains("/tv/") || href.contains("/eps/")
                if (isSeries) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search Error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "Loading detail: $url")
        val doc = app.get(url, headers = mainHeaders).document
        
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src") ?: doc.selectFirst("div.mvic-thumb img")?.attr("src")
        val description = doc.select("div.entry-content p").text() ?: doc.select("div.desc").text()
        val trailerUrl = doc.select("iframe[src*='youtube.com']").attr("src")

        Log.d(TAG, "Title: $title")

        val isSeries = url.contains("/tv-show/") || url.contains("/drama-") || url.contains("series") || url.contains("/tv/") || url.contains("/eps/")

        if (isSeries) {
            val episodes = ArrayList<Episode>()
            doc.select("span.gmr-eps-list a").forEach { ep ->
                episodes.add(newEpisode(ep.attr("href")) { this.name = ep.text() })
            }
            if (episodes.isEmpty()) {
                doc.select("div.tv-eps a").forEach { ep ->
                    episodes.add(newEpisode(ep.attr("href")) { this.name = ep.text() })
                }
            }
            episodes.reverse()
            Log.d(TAG, "Episodes found: ${episodes.size}")
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                addTrailer(trailerUrl)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                addTrailer(trailerUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(TAG, "Loading links for: $data")
        val doc = app.get(data, headers = mainHeaders).document
        
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            if (sourceUrl.startsWith("//")) sourceUrl = "https:$sourceUrl"
            Log.d(TAG, "Found Iframe: $sourceUrl")
            loadExtractor(sourceUrl, data, subtitleCallback, callback)
        }
        
        doc.select("a.gmr-player-link").forEach { link ->
             val sourceUrl = link.attr("href")
             Log.d(TAG, "Found Player Link: $sourceUrl")
             loadExtractor(sourceUrl, data, subtitleCallback, callback)
        }
        
        return true
    }
}
