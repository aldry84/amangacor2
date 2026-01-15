package com.PusatFilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import android.util.Log

class PusatFilm21 : MainAPI() {
    override var mainUrl = "https://v2.pusatfilm21info.net"
    override var name = "PusatFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    private val TAG = "PusatFilm21_DEBUG"
    
    // User-Agent yang terbukti lolos (dari log curl kamu)
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

    private val mainHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    )

    private val ajaxHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/",
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/javascript, */*; q=0.01"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        
        // Daftar URL yang valid
        val urls = listOf(
            Pair("$mainUrl/trending/page/$page/", "Trending"),
            Pair("$mainUrl/film-terbaru/page/$page/", "Film Terbaru"), // URL dari log curl
            Pair("$mainUrl/latest-series/page/$page/", "Series Terbaru"),
            Pair("$mainUrl/drama-korea/page/$page/", "Drama Korea")
        )

        for ((url, name) in urls) {
            try {
                val response = app.get(url, headers = mainHeaders)
                val doc = response.document
                
                // Selector Utama (Sesuai Log Curl)
                // Situs ini menggunakan struktur article dengan class "item-list"
                var movies = doc.select("article.item-list").mapNotNull { toSearchResult(it) }
                
                // Selector Cadangan 1 (Grid Item)
                if (movies.isEmpty()) {
                    movies = doc.select("div.ml-item").mapNotNull { toSearchResult(it) }
                }

                // Selector Cadangan 2 (Grid Item Alternatif)
                if (movies.isEmpty()) {
                     movies = doc.select("div#gmr-main-load article").mapNotNull { toSearchResult(it) }
                }

                if (movies.isNotEmpty()) {
                    items.add(HomePageList(name, movies))
                    Log.d(TAG, "Success: Found ${movies.size} items for $name")
                } else {
                    Log.e(TAG, "Failed: No items found for $name using selectors.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching $name: ${e.message}")
            }
        }

        if (items.isEmpty()) throw ErrorLoadingException("Website terbuka tapi selector gagal. Cek log PusatFilm21_DEBUG.")
        
        return newHomePageResponse(items)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        // Logika Pengambilan Data (Scraping)
        // 1. Link & Judul
        val linkElement = element.selectFirst("h2.entry-title a") 
            ?: element.selectFirst("a.ml-mask")
            ?: return null
            
        val href = linkElement.attr("href")
        val title = linkElement.attr("title").ifEmpty { linkElement.text() }
        
        // 2. Gambar
        val imgElement = element.selectFirst("img")
        val posterUrl = imgElement?.attr("data-original")
            ?: imgElement?.attr("data-src")
            ?: imgElement?.attr("src")

        // 3. Deteksi Tipe (Series/Movie)
        val isSeries = href.contains("/tv/") || 
                       href.contains("/series/") || 
                       href.contains("/drama-") ||
                       href.contains("season")

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
        val cleanQuery = query.replace(" ", "%20")
        val url = "$mainUrl/wp-admin/admin-ajax.php?action=muvipro_core_ajax_search_movie&query=$cleanQuery"
        
        return try {
            val response = app.get(url, headers = ajaxHeaders).parsedSafe<SearchResponseJson>()
            response?.suggestions?.mapNotNull { item ->
                val title = item.value
                val href = item.url
                val posterUrl = if (item.thumb != null) Regex("""src="([^"]+)"""").find(item.thumb)?.groupValues?.get(1) else null
                
                val isSeries = href.contains("/tv/") || href.contains("/series/") || href.contains("/drama-")
                if (isSeries) newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
                else newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mainHeaders).document
        
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src") 
            ?: doc.selectFirst("div.mvic-thumb img")?.attr("src")
        val description = doc.select("div.entry-content p").text() 
            ?: doc.select("div.desc").text()
        val trailerUrl = doc.select("iframe[src*='youtube.com']").attr("src")

        val isSeries = url.contains("/tv/") || url.contains("/series/") || url.contains("/drama-")

        if (isSeries) {
            val episodes = ArrayList<Episode>()
            // Selector Episode (Berdasarkan log curl)
            doc.select("span.gmr-eps-list a").forEach { ep ->
                episodes.add(newEpisode(ep.attr("href")) { this.name = ep.text() })
            }
            if (episodes.isEmpty()) {
                doc.select("div.tv-eps a").forEach { ep ->
                    episodes.add(newEpisode(ep.attr("href")) { this.name = ep.text() })
                }
            }
            episodes.reverse()

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
