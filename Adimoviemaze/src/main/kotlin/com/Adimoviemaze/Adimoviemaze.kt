package com.adimoviemaze

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.ArrayList

class Adimoviemaze : MainAPI() {
    override var mainUrl = "https://watch32.sx"
    override var name = "AdiMovieMaze"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"

    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("/tv/") -> TvType.TvSeries
                else -> TvType.Movie
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val homeSections = ArrayList<HomePageList>()

        // Featured or Popular Section
        val featured = AdimoviemazeParser.parseMovieItems(document.select("div#popular-movies > div.row > div.item"))
        if (featured.isNotEmpty()) {
            homeSections.add(HomePageList("Popular Movies", featured, isHorizontalImages = true))
        }

        // Latest Movies Section
        val latest = AdimoviemazeParser.parseMovieItems(document.select("div#latest-movies > div.row > div.item"))
        if (latest.isNotEmpty()) {
            homeSections.add(HomePageList("Latest Releases", latest, isHorizontalImages = true))
        }

        return HomePageResponse(homeSections)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val document = app.get(searchUrl).document
        return AdimoviemazeParser.parseMovieItems(document.select("div.item"))
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val tags = document.select("div.genres a").map { it.text() }
        val year = document.selectFirst("div.year")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.plot")?.text()?.trim()
        val trailer = fixUrlNull(document.selectFirst("a.trailer")?.attr("href"))

        val recommendations = AdimoviemazeParser.parseMovieItems(document.select("div.related div.item"))

        val actors = document.select("div.cast a").map {
            Actor(it.text(), fixUrlNull(it.selectFirst("img")?.attr("src")))
        }

        val isTvSeries = url.contains("/tv/")

        return if (isTvSeries) {
            val episodes = AdimoviemazeParser.parseEpisodes(document)
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return Extractors.loadLinksFromUrl(data, subtitleCallback, callback)
    }
}
