package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Adicinema : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "AdiCinema"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = false
    private val apiKey = "1d8730d33fc13ccbd8cdaaadb74892c7"
    private val imageUrl = "https://image.tmdb.org/t/p/w500"

    override val mainPage = mainPageOf(
        "$mainUrl/movie/popular?api_key=$apiKey&language=en-US&page=1" to "Film Populer",
        "$mainUrl/tv/popular?api_key=$apiKey&language=en-US&page=1" to "Serial Populer"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(request.data)
        val json = tryParseJson<TMDBList>(res.text) ?: return newHomePageResponse(request.name, listOf())
        val results = json.results.mapNotNull { media ->
            val title = media.title ?: media.name ?: return@mapNotNull null
            val poster = media.poster_path?.let { "$imageUrl$it" }
            val href = if (media.media_type == "tv" || media.first_air_date != null) {
                "$mainUrl/tv/${media.id}?api_key=$apiKey&language=en-US"
            } else {
                "$mainUrl/movie/${media.id}?api_key=$apiKey&language=en-US"
            }

            newTvSeriesSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.year = media.release_date?.take(4)?.toIntOrNull()
            }
        }
        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/multi?api_key=$apiKey&language=en-US&query=${query.encodeUrl()}"
        val res = app.get(url)
        val json = tryParseJson<TMDBList>(res.text) ?: return emptyList()

        return json.results.mapNotNull { media ->
            val title = media.title ?: media.name ?: return@mapNotNull null
            val poster = media.poster_path?.let { "$imageUrl$it" }
            val link = if (media.media_type == "tv" || media.first_air_date != null) {
                "$mainUrl/tv/${media.id}?api_key=$apiKey&language=en-US"
            } else {
                "$mainUrl/movie/${media.id}?api_key=$apiKey&language=en-US"
            }

            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        val detail = tryParseJson<TMDBDetail>(res.text) ?: throw ErrorLoadingException("Gagal parse JSON")

        val title = detail.title ?: detail.name ?: "Tidak diketahui"
        val poster = detail.poster_path?.let { "$imageUrl$it" }
        val desc = detail.overview ?: ""
        val rating = detail.vote_average?.div(10.0)

        return if (detail.first_air_date != null) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes = listOf()) {
                this.posterUrl = poster
                this.plot = desc
                this.year = detail.first_air_date?.take(4)?.toIntOrNull()
                this.rating = rating
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, dataUrl = url) {
                this.posterUrl = poster
                this.plot = desc
                this.year = detail.release_date?.take(4)?.toIntOrNull()
                this.rating = rating
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Tidak ada link langsung dari TMDB, biasanya hanya metadata
        // Kamu bisa menambahkan extractor eksternal di sini jika ingin
        return true
    }

    // ======= JSON Model Classes =======
    data class TMDBList(
        @JsonProperty("results") val results: List<TMDBMedia> = emptyList()
    )

    data class TMDBMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster_path") val poster_path: String?,
        @JsonProperty("release_date") val release_date: String?,
        @JsonProperty("first_air_date") val first_air_date: String?,
        @JsonProperty("media_type") val media_type: String? = null
    )

    data class TMDBDetail(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("poster_path") val poster_path: String?,
        @JsonProperty("release_date") val release_date: String?,
        @JsonProperty("first_air_date") val first_air_date: String?,
        @JsonProperty("vote_average") val vote_average: Double?
    )
}
