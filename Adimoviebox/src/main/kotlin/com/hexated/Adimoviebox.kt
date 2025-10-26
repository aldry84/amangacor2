package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "AdiMoviebox"
    override val hasMainPage = true
    override var lang = "en"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = document.select("div.film-poster").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("data-src")
            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.Movie,
                poster,
                null
            )
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val document = app.get(url).document
        return document.select("div.film-poster").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("data-src")
            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.Movie,
                poster,
                null
            )
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.film-title")?.text() ?: return null
        val poster = document.selectFirst("img.film-poster-img")?.attr("src")
        val year = document.selectFirst("div.film-meta")?.text()?.takeLast(4)?.toIntOrNull()
        val plot = document.selectFirst("div.description")?.text()

        val isSeries = document.select("div.episode-list").isNotEmpty()
        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, poster) {
                this.plot = plot
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }
}
