package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {

    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers standar
    val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru",
        "$mainUrl/populer/page/" to "Populer",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/country/indonesia/page/" to "Indonesia"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val doc = app.get(url, headers = mainHeaders).document
        val home = doc.select("article.item").mapNotNull { toSearchResult(it) }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val doc = app.get(url, headers = mainHeaders).document
        return doc.select("article.item").mapNotNull { toSearchResult(it) }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = element.selectFirst("h2.entry-title a")?.attr("href") ?: return null
        
        val posterUrl = element.selectFirst("img")?.let { img ->
            img.attr("src").ifEmpty { img.attr("data-src") }
        }

        val quality = element.selectFirst(".gmr-quality-item a")?.text()
        
        val isTv = element.select(".gmr-posttype-item").text().contains("TV Show", true) || 
                   element.select(".gmr-numbeps").isNotEmpty()

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mainHeaders).document

        val title = doc.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val description = doc.selectFirst(".entry-content p")?.text()
        
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src") 
            ?: doc.selectFirst("div.post-thumbnail img")?.attr("src")
            
        val year = doc.select("div.gmr-moviedata a[href*='year']").text().toIntOrNull()
        
        // CONTEK ADICINEMAX: Menggunakan Score.from10() string rating
        val ratingText = doc.select("span[itemprop=ratingValue]").text()
        val scoreVal = Score.from10(ratingText) // Ini otomatis handle parsing String ke Double/Int

        val backdrop = doc.selectFirst("#muvipro_player_content_id img")?.attr("src") ?: poster

        val tags = doc.select("div.gmr-moviedata a[href*='genre']").map { it.text() }
        
        // CONTEK ADICINEMAX: Format Actors menggunakan ActorData
        val actors = doc.select("span[itemprop=actors] a").map { 
            ActorData(Actor(it.text(), null)) 
        }

        val recommendations = doc.select("div.idmuvi-core .row.grid-container article.item").mapNotNull { 
            toSearchResult(it) 
        }

        val episodeList = doc.select(".gmr-listseries a")
        
        if (episodeList.isNotEmpty()) {
            val episodes = episodeList.mapNotNull { 
                val epHref = it.attr("href")
                val epText = it.text()
                
                if (it.hasClass("gmr-all-serie") || epText.contains("Pilih Episode", true)) {
                    return@mapNotNull null
                }

                val cleanName = if(epText.startsWith("Eps", true)) {
                    epText.replace("Eps", "Episode ")
                } else {
                    epText
                }

                newEpisode(epHref) {
                    this.name = cleanName
                    this.episode = cleanName.filter { char -> char.isDigit() }.toIntOrNull()
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                
                // PERBAIKAN UTAMA: Pakai this.score bukan this.rating
                this.score = scoreVal 
                
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                
                // PERBAIKAN UTAMA: Pakai this.score bukan this.rating
                this.score = scoreVal
                
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
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

        // 1. Link Download
        doc.select("ul.gmr-download-list li a").forEach { link ->
            val href = link.attr("href")
            if (href.startsWith("http")) {
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }

        // 2. Iframe Utama
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            if (sourceUrl.startsWith("//")) sourceUrl = "https:$sourceUrl"
            
            if (!sourceUrl.contains("youtube.com") && !sourceUrl.contains("youtu.be")) {
                loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        }
        
        // 3. Tab Server Lain
        doc.select("ul.muvipro-player-tabs li a").forEach { tab ->
            var link = tab.attr("href")
            if (link.startsWith("/")) link = mainUrl + link
            
             if (link.startsWith("http") && link != data && !link.contains("#")) {
                 try {
                     val embedPage = app.get(link, headers = mainHeaders).document
                     val iframeSrc = embedPage.select("div.gmr-embed-responsive iframe").attr("src")
                     
                     if(iframeSrc.isNotBlank()) {
                         var realSrc = iframeSrc
                         if (realSrc.startsWith("//")) realSrc = "https:$realSrc"
                         
                         if (!realSrc.contains("youtube.com") && !realSrc.contains("youtu.be")) {
                             loadExtractor(realSrc, data, subtitleCallback, callback)
                         }
                     }
                 } catch (e: Exception) {
                 }
             }
        }

        return true
    }
}
