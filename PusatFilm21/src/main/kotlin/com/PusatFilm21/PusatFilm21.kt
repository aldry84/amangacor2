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
    
    // User-Agent Chrome Desktop kadang lebih stabil untuk scraping HTML struktur
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val mainHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = listOf(
            Pair("$mainUrl/trending/page/$page/", "Trending"),
            Pair("$mainUrl/film-terbaru/page/$page/", "Film Terbaru"),
            Pair("$mainUrl/movies/page/$page/", "Movies"),
            Pair("$mainUrl/tv-show/page/$page/", "TV Show"),
            Pair("$mainUrl/drama-korea/page/$page/", "Drama Korea")
        )

        val homeSets = items.mapNotNull { (url, name) ->
            try {
                Log.d(TAG, "Requesting: $url")
                val doc = app.get(url, headers = mainHeaders).document
                
                // UPDATE SELECTOR DI SINI
                // Kita cari elemen yang umum dipakai tema WordPress streaming
                var movies = doc.select("article.item-list").mapNotNull { toSearchResult(it) }
                
                if (movies.isEmpty()) {
                    movies = doc.select("div.ml-item").mapNotNull { toSearchResult(it) }
                }
                
                if (movies.isEmpty()) {
                     // Selector cadangan terakhir (biasanya grid item)
                    movies = doc.select("div#gmr-main-load article").mapNotNull { toSearchResult(it) }
                }

                if (movies.isNotEmpty()) {
                    Log.d(TAG, "Found ${movies.size} items for $name")
                    HomePageList(name, movies)
                } else {
                    Log.e(TAG, "No items found for $name")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching $name: ${e.message}")
                null
            }
        }
        
        return newHomePageResponse(homeSets)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        // Coba berbagai kemungkinan lokasi Link dan Judul
        val linkElement = element.selectFirst("h2.entry-title a") 
            ?: element.selectFirst("a.ml-mask")
            ?: element.selectFirst("a.gmr-watch-button")
            ?: return null
            
        val href = linkElement.attr("href")
        val title = linkElement.attr("title").ifEmpty { linkElement.text() }
        
        val imgElement = element.selectFirst("img")
        val posterUrl = imgElement?.attr("data-original")
            ?: imgElement?.attr("data-src")
            ?: imgElement?.attr("src")

        val isSeries = href.contains("/tv-show/") || 
                       href.contains("/drama-") || 
                       href.contains("series") || 
                       href.contains("/tv/") || 
                       href.contains("/eps/")

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

    // Bagian Search, Load, dan LoadLinks biarkan sama seperti sebelumnya 
    // karena yang bermasalah hanya tampilan Halaman Utama (Main Page).
    
    // ... (Paste kode Search, Load, LoadLinks dari versi sebelumnya di sini) ...
    // Agar tidak kepanjangan, saya sertakan Search/Load/LoadLinks versi singkat di bawah ini
    // tapi sebaiknya gunakan yang dari "SOLUSI FINAL (All-in-One)" sebelumnya.

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
            val response = app.get(url, headers = mapOf("X-Requested-With" to "XMLHttpRequest", "User-Agent" to userAgent)).parsedSafe<SearchResponseJson>()
            response?.suggestions?.mapNotNull { item ->
                val title = item.value
                val href = item.url
                val posterUrl = if (item.thumb != null) Regex("""src="([^"]+)"""").find(item.thumb)?.groupValues?.get(1) else null
                val isSeries = href.contains("/tv/")
                if (isSeries) newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
                else newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mainHeaders).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src")
        val desc = doc.select("div.entry-content p").text()
        val isSeries = url.contains("/tv/") || url.contains("/series/")
        if (isSeries) {
            val episodes = ArrayList<Episode>()
            doc.select("span.gmr-eps-list a").forEach { episodes.add(newEpisode(it.attr("href")){ this.name = it.text() }) }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { this.posterUrl = poster; this.plot = desc }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) { this.posterUrl = poster; this.plot = desc }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data, headers = mainHeaders).document
        doc.select("iframe").forEach { 
            var src = it.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            loadExtractor(src, data, subtitleCallback, callback)
        }
        return true
    }
}
