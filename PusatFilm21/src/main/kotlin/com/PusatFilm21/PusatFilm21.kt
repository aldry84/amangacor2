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

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val mainHeaders = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "$mainUrl/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        
        // Kita tes satu URL dulu yang pasti ada
        val urls = listOf(
            Pair("$mainUrl/film-terbaru/page/$page/", "Film Terbaru"), // URL yang valid dari log curl
            Pair("$mainUrl/trending/page/$page/", "Trending")
        )

        for ((url, name) in urls) {
            try {
                val response = app.get(url, headers = mainHeaders)
                val doc = response.document
                
                // Selector yang lebih umum
                val movies = doc.select("article.item-list, div.ml-item").mapNotNull {
                    toSearchResult(it)
                }

                if (movies.isNotEmpty()) {
                    items.add(HomePageList(name, movies))
                }
            } catch (e: Exception) {
                // Log error ke console
                e.printStackTrace()
            }
        }

        if (items.isEmpty()) throw ErrorLoadingException("Tidak ada film ditemukan. Cek koneksi/blokir.")

        return newHomePageResponse(items)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val linkElement = element.selectFirst("a.ml-mask") ?: element.selectFirst("a") ?: return null
        val href = linkElement.attr("href")
        val title = linkElement.attr("title").ifEmpty { element.select("h2.entry-title").text() }
        val imgElement = element.selectFirst("img")
        val posterUrl = imgElement?.attr("data-original") 
            ?: imgElement?.attr("data-src") 
            ?: imgElement?.attr("src")

        val isSeries = href.contains("/tv/") || href.contains("/series/") || href.contains("season")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    // ... (Bagian Search, Load, LoadLinks biarkan sama dulu atau copy dari kode sebelumnya) ...
    // Untuk mempersingkat, saya fokuskan perbaikan di Main Page dulu. 
    // Pastikan fungsi search, load, dan loadLinks dari kode sebelumnya tetap ada di bawah sini.
    
    // ==============================
    // 2. SEARCH (Pencarian Cepat via JSON)
    // ==============================
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
                
                val posterUrl = if (item.thumb != null) {
                    Regex("""src="([^"]+)"""").find(item.thumb)?.groupValues?.get(1)
                } else null

                val isSeries = href.contains("/tv/") || href.contains("/series/")

                if (isSeries) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==============================
    // 3. LOAD (Detail Film)
    // ==============================
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mainHeaders).document
        
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "Unknown"
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src")
            ?: doc.selectFirst("div.mvic-thumb img")?.attr("src")
        val description = doc.select("div.entry-content p").text() 
            ?: doc.select("div.desc").text()

        val trailerUrl = doc.select("iframe[src*='youtube.com']").attr("src")

        val isSeries = url.contains("/tv/") || url.contains("/series/") || url.contains("season")

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

    // ==============================
    // 4. LOAD LINKS (Video Player)
    // ==============================
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
