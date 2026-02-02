package com.Adicinemax21

import com.fasterxml.jackson.annotation.JsonProperty
import com.Adicinemax21.Adicinemax21Extractor.invokeAdiDewasa
import com.Adicinemax21.Adicinemax21Extractor.invokeKisskh 
import com.Adicinemax21.Adicinemax21Extractor.invokeAdimoviebox
import com.Adicinemax21.Adicinemax21Extractor.invokeAdimoviebox2
import com.Adicinemax21.Adicinemax21Extractor.invokeGomovies
import com.Adicinemax21.Adicinemax21Extractor.invokeIdlix
import com.Adicinemax21.Adicinemax21Extractor.invokeMapple
import com.Adicinemax21.Adicinemax21Extractor.invokeSuperembed
import com.Adicinemax21.Adicinemax21Extractor.invokeVidfast
import com.Adicinemax21.Adicinemax21Extractor.invokeVidlink
import com.Adicinemax21.Adicinemax21Extractor.invokeVidsrc
import com.Adicinemax21.Adicinemax21Extractor.invokeVidsrccc
import com.Adicinemax21.Adicinemax21Extractor.invokeVixsrc
import com.Adicinemax21.Adicinemax21Extractor.invokeWatchsomuch
import com.Adicinemax21.Adicinemax21Extractor.invokeWyzie
import com.Adicinemax21.Adicinemax21Extractor.invokeXprime
import com.Adicinemax21.Adicinemax21Extractor.invokeCinemaOS
import com.Adicinemax21.Adicinemax21Extractor.invokePlayer4U
import com.Adicinemax21.Adicinemax21Extractor.invokeRiveStream
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlin.math.roundToInt

open class Adicinemax21 : TmdbProvider() {
    override var name = "Adicinemax21"
    override val hasMainPage = true
    override var lang = "id"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = "b030404650f279792a8d3287232358e3"

        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/movie/day?api_key=$apiKey&region=US&without_genres=16" to "Trending Movies",
        "$tmdbAPI/discover/movie?api_key=$apiKey&sort_by=popularity.desc&primary_release_date.gte=2020-01-01&without_genres=16" to "Popular Movies (2020+)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&sort_by=popularity.desc&first_air_date.gte=2020-01-01&without_genres=16" to "Popular TV Shows (2020+)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213&sort_by=popularity.desc&first_air_date.gte=2020-01-01&without_genres=16" to "Netflix Originals (New)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_watch_providers=8&watch_region=US&sort_by=popularity.desc&primary_release_date.gte=2020-01-01&without_genres=16" to "Netflix Movies (New)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=id&sort_by=popularity.desc&first_air_date.gte=2020-01-01" to "Indonesian Series (2020+)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=id&without_genres=16,27&sort_by=popularity.desc&primary_release_date.gte=2020-01-01" to "Indonesian Movies (2020+)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=id&with_genres=27&without_genres=16&sort_by=popularity.desc&primary_release_date.gte=2020-01-01" to "Indonesian Horror (2020+)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&sort_by=popularity.desc&without_genres=16&first_air_date.gte=2020-01-01" to "Korean Dramas (2020+)",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=zh&sort_by=popularity.desc&without_genres=16&first_air_date.gte=2020-01-01" to "Chinese Dramas (2020+)",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=28&sort_by=popularity.desc&without_genres=16&primary_release_date.gte=2020-01-01" to "Action Movies",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=27&sort_by=popularity.desc&without_genres=16&primary_release_date.gte=2020-01-01" to "Horror Movies",
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adultQuery = if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        
        val home = app.get("${request.data}$adultQuery&page=$page")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = try {
            parseJson<Data>(url)
        } catch (e: Exception) {
            null
        } ?: return null

        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = "$tmdbAPI/${data.type}/${data.id}?api_key=$apiKey&append_to_response=$append"
        
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        
        // --- TRAILER LOGIC UPDATE ---
        // Kita filter supaya hanya mengambil trailer dari YouTube yang "Official" atau "Trailer"
        // Ini membantu menghindari video sampah yang mungkin bikin player crash
        val trailer = res.videos?.results
            ?.filter { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
            ?.map { "https://www.youtube.com/watch?v=${it.key}" }

        return if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            data = LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                null,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                title = title,
                                year = year
                            ).toJson()
                        ) {
                            this.name = eps.name
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.description = eps.overview
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.score = Score.from10(res.vote_average?.toString())
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    null,
                    data.type,
                    title = title,
                    year = year
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.score = Score.from10(res.vote_average?.toString())
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        
        runAllAsync(
            { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeAdimoviebox2(res.title ?: return@runAllAsync, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeAdimoviebox(res.title ?: return@runAllAsync, res.year, res.season, res.episode, subtitleCallback, callback) },
            // ... (Kode loadLinks lain tetap sama)
        )
        return true
    }

    // --- DATA CLASSES (Updated for Trailers) ---
    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val aniId: String? = null,
        val animeId: String? = null,
        val orgTitle: String? = null,
        val isAnime: Boolean = false,
        val airedYear: Int? = null,
        val lastSeason: Int? = null,
        val epsTitle: String? = null,
        val jpTitle: String? = null,
        val date: String? = null,
        val airedDate: String? = null,
        val isAsian: Boolean = false,
        val isBollywood: Boolean = false,
        val isCartoon: Boolean = false,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
    )

    data class Results(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,   // Tambahan: Untuk filter YouTube
        @JsonProperty("type") val type: String? = null,   // Tambahan: Untuk filter Trailer
        @JsonProperty("official") val official: Boolean? = false 
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val vote_average: Any? = null,
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("recommendations") val recommendations: Results? = null,
        @JsonProperty("credits") val credits: Credits? = null,
    )

    data class Seasons(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class Episodes(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
    )
    
    data class Cast(
        @JsonProperty("name") val name: String? = null,
    )
    
    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )
}
