package com.AdiDrakor

import android.os.Build // [PERBAIKAN] Ditambahkan agar tidak error saat cek versi Android
import com.fasterxml.jackson.annotation.JsonProperty
import com.AdiDrakor.AdiDrakorExtractor.invokeDramadrip
import com.AdiDrakor.AdiDrakorExtractor.invokeIdlix
import com.AdiDrakor.AdiDrakorExtractor.invokeKisskh
import com.AdiDrakor.AdiDrakorExtractor.invokeKisskhAsia
import com.AdiDrakor.AdiDrakorExtractor.invokeNepu
import com.AdiDrakor.AdiDrakorExtractor.invokeNinetv
import com.AdiDrakor.AdiDrakorExtractor.invokeRidomovies
import com.AdiDrakor.AdiDrakorExtractor.invokeShowflix
import com.AdiDrakor.AdiDrakorExtractor.invokeVidSrcXyz
import com.AdiDrakor.AdiDrakorExtractor.invokeVidsrccc
import com.AdiDrakor.AdiDrakorExtractor.invokeZshow
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink

open class AdiDrakor : TmdbProvider() {
    override var name = "AdiDrakor"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
    )

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        const val anilistAPI = "https://graphql.anilist.co"
        private const val apiKey = "b030404650f279792a8d3287232358e3"

        // --- DOMAINS ---
        const val idlixAPI = "https://tv6.idlixku.com"
        const val kissKhAPI = "https://kisskh.ovh"
        const val dramadripAPI = "https://dramadrip.com"
        const val ridomoviesAPI = "https://ridomovies.tv"
        const val showflixAPI = "https://showflix.store"
        const val nineTvAPI = "https://moviesapi.club"
        const val nepuAPI = "https://nepu.to"
        const val vidsrctoAPI = "https://vidsrc.cc"
        const val vidsrcxyzAPI = "https://vidsrc-embed.su"
        const val zshowAPI = "https://zshow.tv"

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

    // 8 Kategori Khusus Korea
    override val mainPage = mainPageOf(
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&sort_by=popularity.desc" to "Popular K-Dramas",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=ko&sort_by=popularity.desc" to "Popular Korean Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&sort_by=vote_average.desc&vote_count.gte=100" to "Top Rated K-Dramas",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=ko&sort_by=vote_average.desc&vote_count.gte=100" to "Top Rated Korean Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today K-Dramas",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=ko&primary_release_date.gte=${getDate().today}" to "Upcoming Korean Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko&with_genres=10749" to "Romance K-Dramas",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_original_language=ko&with_genres=28" to "Action Korean Movies"
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
            .parsedSafe<Results>()?.results?.mapNotNull { media -> media.toSearchResponse(type) } 
            ?: throw ErrorLoadingException("Invalid Json reponse")
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
            .parsedSafe<Results>()?.results?.mapNotNull { media -> media.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = try {
            if (url.startsWith("https://www.themoviedb.org/")) {
                val segments = url.removeSuffix("/").split("/")
                val id = segments.lastOrNull()?.toIntOrNull()
                val type = when {
                    url.contains("/movie/") -> "movie"
                    url.contains("/tv/") -> "tv"
                    else -> null
                }
                Data(id = id, type = type)
            } else {
                parseJson<Data>(url)
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Invalid URL or JSON data: ${e.message}")
        } ?: throw ErrorLoadingException("Invalid data format")

        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }
        val res = app.get(resUrl).parsedSafe<MediaDetail>() ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }
        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty().ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }
        val actors = res.credits?.cast?.mapNotNull { cast -> ActorData(Actor(cast.name ?: cast.originalName ?: return@mapNotNull null, getImageUrl(cast.profilePath)), roleString = cast.character) } ?: return null
        val recommendations = res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }
        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }

        return if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            data = LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                title = title,
                                year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                                orgTitle = orgTitle,
                                lastSeason = lastSeason,
                            ).toJson()
                        ) {
                            this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                        }.apply { this.addDate(eps.airDate) }
                    }
            }?.flatten() ?: listOf()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.backgroundPosterUrl = bgPoster; this.year = year
                this.plot = res.overview; this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average?.toString()); this.showStatus = getStatus(res.status)
                this.recommendations = recommendations; this.actors = actors
                addTrailer(trailer); addTMDbId(data.id.toString()); addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, LinkData(data.id, res.external_ids?.imdb_id, res.external_ids?.tvdb_id, data.type, title = title, year = year, orgTitle = orgTitle).toJson()) {
                this.posterUrl = poster; this.backgroundPosterUrl = bgPoster; this.comingSoon = isUpcoming(releaseDate)
                this.year = year; this.plot = res.overview; this.duration = res.runtime
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres; this.score = Score.from10(res.vote_average?.toString())
                this.recommendations = recommendations; this.actors = actors
                addTrailer(trailer); addTMDbId(data.id.toString()); addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val res = parseJson<LinkData>(data)
        
        // --- DAFTAR 10 EXTRACTOR TERBAIK DARI STREAMPLAY UNTUK ADIDRAKOR ---
        runAllAsync(
            // 1. Idlix (Jeniusplay) - Prioritas Utama
            { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            // 2. KissKh (Raja Drama Asia)
            { invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback) },
            // 3. DramaDrip (Spesialis)
            { invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            // 4. RidoMovies (Cepat)
            { invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            // 5. Vidsrccc (Stabil)
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) invokeVidsrccc(res.id, res.imdbId, res.season, res.episode, callback) },
            // 6. Showflix (Parse API)
            { invokeShowflix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            // 7. Nepu (Baru)
            { invokeNepu(res.title, res.year, res.season, res.episode, callback) },
            // 8. NineTV (Cadangan)
            { invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback) },
            // 9. VidSrcXYZ
            { invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback) },
            // 10. ZShow (Wordpress)
            { invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            // 11. KissKhAsia (Backup)
            { invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback) }
        )
        return true
    }

    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val orgTitle: String? = null,
        val lastSeason: Int? = null,
    )

    data class Data(val id: Int? = null, val type: String? = null)
    data class Results(@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf())
    data class Media(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("original_title") val originalTitle: String? = null, @JsonProperty("media_type") val mediaType: String? = null, @JsonProperty("poster_path") val posterPath: String? = null, @JsonProperty("vote_average") val voteAverage: Double? = null)
    data class Genres(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null)
    data class Keywords(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null)
    data class KeywordResults(@JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(), @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf())
    data class Seasons(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("season_number") val seasonNumber: Int? = null, @JsonProperty("air_date") val airDate: String? = null)
    data class Cast(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("original_name") val originalName: String? = null, @JsonProperty("character") val character: String? = null, @JsonProperty("known_for_department") val knownForDepartment: String? = null, @JsonProperty("profile_path") val profilePath: String? = null)
    data class Episodes(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("overview") val overview: String? = null, @JsonProperty("air_date") val airDate: String? = null, @JsonProperty("still_path") val stillPath: String? = null, @JsonProperty("vote_average") val voteAverage: Double? = null, @JsonProperty("episode_number") val episodeNumber: Int? = null, @JsonProperty("season_number") val seasonNumber: Int? = null)
    data class MediaDetailEpisodes(@JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf())
    data class Trailers(@JsonProperty("key") val key: String? = null)
    data class ResultsTrailer(@JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf())
    data class AltTitles(@JsonProperty("iso_3166_1") val iso_3166_1: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("type") val type: String? = null)
    data class ResultsAltTitles(@JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf())
    data class ExternalIds(@JsonProperty("imdb_id") val imdb_id: String? = null, @JsonProperty("tvdb_id") val tvdb_id: Int? = null)
    data class Credits(@JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf())
    data class ResultsRecommendations(@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf())
    data class LastEpisodeToAir(@JsonProperty("episode_number") val episode_number: Int? = null, @JsonProperty("season_number") val season_number: Int? = null)
    data class ProductionCountries(@JsonProperty("name") val name: String? = null)
    data class MediaDetail(@JsonProperty("id") val id: Int? = null, @JsonProperty("imdb_id") val imdbId: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("original_title") val originalTitle: String? = null, @JsonProperty("original_name") val originalName: String? = null, @JsonProperty("poster_path") val posterPath: String? = null, @JsonProperty("backdrop_path") val backdropPath: String? = null, @JsonProperty("release_date") val releaseDate: String? = null, @JsonProperty("first_air_date") val firstAirDate: String? = null, @JsonProperty("overview") val overview: String? = null, @JsonProperty("runtime") val runtime: Int? = null, @JsonProperty("vote_average") val vote_average: Any? = null, @JsonProperty("original_language") val original_language: String? = null, @JsonProperty("status") val status: String? = null, @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(), @JsonProperty("keywords") val keywords: KeywordResults? = null, @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null, @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(), @JsonProperty("videos") val videos: ResultsTrailer? = null, @JsonProperty("external_ids") val external_ids: ExternalIds? = null, @JsonProperty("credits") val credits: Credits? = null, @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null, @JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null, @JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf())
}
