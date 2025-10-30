package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Adicinema : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3" // Perbaikan: URL dasar TMDB
    override var name = "AdiCinema+Player"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = false
    private val apiKey = "1d8730d33fc13ccbd8cdaaadb74892c7"
    private val imageUrl = "https://image.tmdb.org/t/p/w500" // Perbaikan: URL dasar gambar

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
            if (media.media_type == "tv" || media.first_air_date != null) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { // Perbaikan: TvType.TvSeries
                    this.posterUrl = poster
                    this.year = media.release_date?.take(4)?.toIntOrNull()
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { // Perbaikan: TvType.Movie
                    this.posterUrl = poster
                }
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
            if (media.media_type == "tv" || media.first_air_date != null) {
                newTvSeriesSearchResponse(title, link, TvType.TvSeries) { // Perbaikan: TvType.TvSeries
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(title, link, TvType.Movie) { // Perbaikan: TvType.Movie
                    this.posterUrl = poster
                }
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
        return newMovieLoadResponse(title, url, TvType.Movie) { // dataUrl dihapus
            this.posterUrl = poster
            this.plot = desc
            this.year = detail.release_date?.take(4)?.toIntOrNull()
            this.rating = rating
        }
    }

    /**
     * Mencari link streaming otomatis berdasarkan judul film.
     * Sumber: VidSrc, 2embed, MoviesAPI, dll.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val titleQuery = data.encodeUrl()
        // Coba cari link di beberapa provider umum
        val possibleUrls = listOf(
            "https://vidsrc.to/embed/movie?title=$titleQuery",
            "https://2embed.cc/embed/$titleQuery",
            "https://moviesapi.club/player/$titleQuery",
            "https://autoembed.cc/movie/$titleQuery"
        )
        for (source in possibleUrls) {
            try {
                val res = app.get(source)
                if (res.code == 200 && res.text.contains("iframe", true)) {
                    val doc = Jsoup.parse(res.text)
                    val iframe = doc.selectFirst("iframe")?.attr("src")
                    if (iframe != null) {
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                this.name,
                                iframe,
                                iframe,
                                Qualities.Unknown,
                                isM3u8 = iframe.contains(".m3u8")
                            )
                        )
                        return true
                    }
                }
            } catch (e: Exception) {
                println("Error loading links from $source: ${e.message}") // Catat kesalahan
                continue
            }
        }
        return false
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
