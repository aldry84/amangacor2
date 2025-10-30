package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.encodeUrl
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class Adicinema : MainAPI() {
    private val apiKey = "1d8730d33fc13ccbd8cdaaadb74892c7"

    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "Adicinema"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override var lang = "en"
    override val instantLinkLoading = true

    override val mainPage = mainPageOf(
        "$mainUrl/trending/movie/day?api_key=$apiKey" to "Trending Movies",
        "$mainUrl/movie/popular?api_key=$apiKey" to "Popular Movies",
        "$mainUrl/movie/top_rated?api_key=$apiKey" to "Top Rated Movies",
        "$mainUrl/tv/popular?api_key=$apiKey" to "Popular TV Shows",
        "$mainUrl/tv/top_rated?api_key=$apiKey" to "Top Rated TV Shows",
        "$mainUrl/discover/movie?api_key=$apiKey&with_genres=16" to "Animation"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val url = "${request.data}&page=$page"
        val response = app.get(url).parsedSafe<TmdbListResponse>()
        val items = response?.results?.mapNotNull { it.toSearchResponse(this) }
        return newHomePageResponse(request.name, items ?: emptyList())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query) ?: emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/search/multi?api_key=$apiKey&query=${query.encodeUrl()}"
        val response = app.get(url).parsedSafe<TmdbListResponse>()
        return response?.results?.mapNotNull { it.toSearchResponse(this) }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val isTv = url.contains("/tv/")
        val endpoint = if (isTv) "tv" else "movie"

        val detailUrl = "$mainUrl/$endpoint/$id?api_key=$apiKey&append_to_response=videos,credits,recommendations"
        val detail = app.get(detailUrl).parsedSafe<TmdbDetail>() ?: throw ErrorLoadingException("No Data")

        val title = detail.title ?: detail.name ?: "Unknown"
        val poster = "https://image.tmdb.org/t/p/w500${detail.posterPath}"
        val description = detail.overview
        val year = detail.releaseDate?.take(4)?.toIntOrNull()
        val tags = detail.genres?.mapNotNull { it.name }
        val score = Score.from10(detail.voteAverage?.toString())
        val trailer = detail.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.key?.let {
            "https://www.youtube.com/watch?v=$it"
        }

        val actors = detail.credits?.cast?.map {
            ActorData(Actor(it.name ?: "", "https://image.tmdb.org/t/p/w500${it.profilePath}"))
        }

        val recommendations = detail.recommendations?.results?.mapNotNull { it.toSearchResponse(this) }

        val type = if (isTv) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            val episodes = detail.seasons?.flatMap { season ->
                (1..(season.episodeCount ?: 0)).map { ep ->
                    val episode = newEpisode(
                        LoadData(id, seasonNumber = season.seasonNumber, episodeNumber = ep).toJson()
                    )
                    episode.name = "Episode $ep"
                    episode.season = season.seasonNumber
                    episode.episode = ep
                    episode
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(title, url, type, data = id) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // TMDb tidak menyediakan streaming langsung.
        return false
    }

    // ==== Data Class TMDb ====

    data class TmdbListResponse(
        @JsonProperty("results") val results: List<TmdbItem>? = null
    )

    data class TmdbItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null
    ) {
        fun toSearchResponse(provider: Adicinema): SearchResponse? {
            val realType = when (mediaType) {
                "tv" -> TvType.TvSeries
                "movie" -> TvType.Movie
                else -> TvType.Movie
            }
            val realTitle = title ?: name ?: return null
            val realPoster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

            return provider.newMovieSearchResponse(realTitle, "${provider.mainUrl}/$mediaType/$id", realType) {
                this.posterUrl = realPoster
                this.score = Score.from10(voteAverage?.toString())
            }
        }
    }

    data class TmdbDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("genres") val genres: List<Genre>? = null,
        @JsonProperty("videos") val videos: Videos? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: TmdbListResponse? = null,
        @JsonProperty("seasons") val seasons: List<Season>? = null,
    ) {
        data class Genre(@JsonProperty("name") val name: String? = null)
        data class Videos(@JsonProperty("results") val results: List<Video>? = null)
        data class Video(
            @JsonProperty("key") val key: String? = null,
            @JsonProperty("site") val site: String? = null,
            @JsonProperty("type") val type: String? = null,
        )
        data class Credits(@JsonProperty("cast") val cast: List<Cast>? = null)
        data class Cast(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("profile_path") val profilePath: String? = null,
        )
        data class Season(
            @JsonProperty("season_number") val seasonNumber: Int? = null,
            @JsonProperty("episode_count") val episodeCount: Int? = null,
        )
    }

    data class LoadData(
        val id: String,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null
    )
}
