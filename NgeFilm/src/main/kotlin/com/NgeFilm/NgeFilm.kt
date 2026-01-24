package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {
    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("#gmr-main-load article.item-infinite").mapNotNull {
            toSearchResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url).document
        
        return document.select("#gmr-main-load article.item-infinite").mapNotNull {
            toSearchResult(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".gmr-movie-view .attachment-thumbnail")?.attr("src") 
            ?: document.selectFirst(".gmr-movie-view .attachment-thumbnail")?.attr("data-src")
            ?: "" // Fix: Pastikan tidak null

        val plot = document.select(".entry-content p").text()
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        
        val tags = document.select(".gmr-movie-on a[rel='category tag']").map { it.text() }
        
        // Fix: Gunakan ActorData
        val actors = document.select("[itemprop='actor'] span[itemprop='name']").map { 
            ActorData(Actor(it.text())) 
        }

        val recommendations = document.select("#gmr-related-post article").mapNotNull {
            toSearchResult(it)
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = tags
            this.actors = actors
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            if (sourceUrl.startsWith("//")) sourceUrl = "https:$sourceUrl"

            if (sourceUrl.contains("rpmlive.online")) {
                RpmLive().getUrl(sourceUrl, data, subtitleCallback, callback)
            } else {
                loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().replace("Nonton ", "").trim()
        val url = fixUrl(titleElement.attr("href"))
        
        val imgElement = element.selectFirst(".content-thumbnail img")
        // FIX CRITICAL ERROR DI SINI: Tambahkan ?: "" agar tidak null
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src") ?: ""
        
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }
}
