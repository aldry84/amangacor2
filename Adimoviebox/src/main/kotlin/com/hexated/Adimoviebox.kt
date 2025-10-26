package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Adimoviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "Adimoviebox"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = document.select("div.movie-item").mapNotNull { element ->
            val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")
            MovieSearchResponse(
                name = title,
                url = fixUrl(href),
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = fixUrlNull(poster),
            )
        }
        return newHomePageResponse("Latest Movies", items)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") ?: "Unknown"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val isSeries = document.select("div.episode-list").isNotEmpty()

        return if (isSeries) {
            val episodes = document.select("div.episode-item").mapNotNull {
                val name = it.text()
                val link = it.attr("data-url") ?: return@mapNotNull null
                Episode(
                    data = fixUrl(link),
                    name = name,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, fixUrl(url)) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }
}
