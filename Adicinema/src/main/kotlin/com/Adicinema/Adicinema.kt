package com.Adicinema

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class AdiCinema : MainAPI() {
    override var mainUrl = "https://vidsrc.to"
    override var name = "AdiCinema"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdb = "https://api.themoviedb.org/3"
    private val apiKey = "1d8730d33fc13ccbd8cdaaadb74892c7"

    // ======== DATA MODEL UNTUK SERIALISASI ========
    data class LoadData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("imdbId") val imdbId: String?,
        @JsonProperty("isMovie") val isMovie: Boolean,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null
    )

    // ======== SEARCH / HOME ========
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$tmdb/movie/popular?api_key=$apiKey&language=en-US&page=$page").text
        val json = parseJson<JsonMovieList>(res)
        val list = json.results.mapNotNull {
            newMovieSearchResponse(
                it.title ?: return@mapNotNull null,
                LoadData(it.id, it.imdb_id, true).toJson(),
                TvType.Movie
            ) {
                posterUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}"
            }
        }
        return newHomePageResponse("Popular Movies", list)
    }

    // ======== DETAIL FILM / SERIES ========
    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)

        val detailUrl = if (data.isMovie)
            "$tmdb/movie/${data.id}?api_key=$apiKey&language=en-US&append_to_response=videos"
        else
            "$tmdb/tv/${data.id}?api_key=$apiKey&language=en-US&append_to_response=videos"

        val res = app.get(detailUrl).text
        val json = parseJson<TmdbDetails>(res)

        val trailerKey = json.videos?.results?.firstOrNull()?.key
        val trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        return if (data.isMovie) {
            newMovieLoadResponse(json.title ?: "Unknown", data.toJson()) {
                posterUrl = "https://image.tmdb.org/t/p/w500${json.poster_path}"
                plot = json.overview
                addTrailer(trailerUrl)
            }
        } else {
            val episodes = json.seasons?.flatMap { season ->
                val seasonNum = season.season_number ?: return@flatMap emptyList()
                val epRes = app.get("$tmdb/tv/${data.id}/season/$seasonNum?api_key=$apiKey&language=en-US").text
                val epJson = parseJson<TmdbSeason>(epRes)
                epJson.episodes.mapNotNull { ep ->
                    newEpisode(ep.name ?: "Episode ${ep.episode_number}") {
                        this.season = seasonNum
                        this.episode = ep.episode_number
                        posterUrl = "https://image.tmdb.org/t/p/w500${ep.still_path}"
                        description = ep.overview
                        date = parseDate(ep.air_date)
                        score = Score(ep.vote_average ?: 0.0)
                    }
                }
            } ?: emptyList()

            newTvSeriesLoadResponse(json.name ?: "Unknown", data.toJson()) {
                posterUrl = "https://image.tmdb.org/t/p/w500${json.poster_path}"
                plot = json.overview
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }

    // ======== LINK EXTRACTOR DENGAN FALLBACK ========
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val imdbId = media.imdbId ?: return false

        val type = if (media.isMovie) "movie" else "tv"
        val path = if (media.isMovie) imdbId else "$imdbId/${media.season}/${media.episode}"

        val sources = listOf(
            "https://vidsrc.to/embed/$type/$path",
            "https://vidsrc.me/embed/$type/$path",
            "https://vidbinge.to/embed/$type/$path",
            "https://2embed.cc/embed/$type/$path",
            "https://streamwish.to/e/$imdbId"
        )

        var success = false
        for (source in sources) {
            try {
                val result = loadExtractor(source, referer = mainUrl, subtitleCallback, callback)
                if (result) {
                    println("✅ Link ditemukan di: $source")
                    success = true
                    break
                }
            } catch (e: Exception) {
                println("⚠️ Gagal memuat dari: $source (${e.message})")
                continue
            }
        }
        if (!success) println("❌ Tidak ada sumber yang menyediakan link untuk $imdbId")
        return success
    }

    // ======== MODEL TMDB ========
    data class JsonMovieList(
        @JsonProperty("results") val results: List<TmdbItem>
    )

    data class TmdbItem(
        @JsonProperty("id") val id: Int,
        @JsonProperty("imdb_id") val imdb_id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("poster_path") val poster_path: String?
    )

    data class TmdbDetails(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("poster_path") val poster_path: String?,
        @JsonProperty("videos") val videos: TmdbVideos?,
        @JsonProperty("seasons") val seasons: List<TmdbSeason>?
    )

    data class TmdbVideos(
        @JsonProperty("results") val results: List<TmdbVideo>
    )

    data class TmdbVideo(
        @JsonProperty("key") val key: String?
    )

    data class TmdbSeason(
        @JsonProperty("season_number") val season_number: Int?,
        @JsonProperty("episodes") val episodes: List<TmdbEpisode>
    )

    data class TmdbEpisode(
        @JsonProperty("name") val name: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("air_date") val air_date: String?,
        @JsonProperty("episode_number") val episode_number: Int?,
        @JsonProperty("vote_average") val vote_average: Double?,
        @JsonProperty("still_path") val still_path: String?
    )
}
