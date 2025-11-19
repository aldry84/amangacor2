package com.AdiDrakor

import android.os.Build
import com.fasterxml.jackson.annotation.JsonProperty
import com.AdiDrakor.AdiDrakorExtractor.invokeAllMovieland
import com.AdiDrakor.AdiDrakorExtractor.invokeDramadrip
import com.AdiDrakor.AdiDrakor.Companion.kissKhAPI // (Only if needed, removed usage in loadLinks)
import com.AdiDrakor.AdiDrakorExtractor.invokeEmovies
import com.AdiDrakor.AdiDrakorExtractor.invokeExtramovies
import com.AdiDrakor.AdiDrakorExtractor.invokeIdlix
import com.AdiDrakor.AdiDrakorExtractor.invokeKisskhAsia
import com.AdiDrakor.AdiDrakorExtractor.invokeMappleTv
import com.AdiDrakor.AdiDrakorExtractor.invokeMoflix
import com.AdiDrakor.AdiDrakorExtractor.invokeMultimovies
import com.AdiDrakor.AdiDrakorExtractor.invokeNepu
import com.AdiDrakor.AdiDrakorExtractor.invokeNinetv
import com.AdiDrakor.AdiDrakorExtractor.invokeRidomovies
import com.AdiDrakor.AdiDrakorExtractor.invokeShowflix
import com.AdiDrakor.AdiDrakorExtractor.invokeSoapy
import com.AdiDrakor.AdiDrakorExtractor.invokeVidSrcXyz
import com.AdiDrakor.AdiDrakorExtractor.invokeVidzee
import com.AdiDrakor.AdiDrakorExtractor.invokeVidsrccc
import com.AdiDrakor.AdiDrakorExtractor.invokeWatch32
import com.AdiDrakor.AdiDrakorExtractor.invokeZoechip
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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    val wpRedisInterceptor by lazy { CloudflareKiller() }

    companion object {
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        const val anilistAPI = "https://graphql.anilist.co"
        private const val apiKey = "b030404650f279792a8d3287232358e3"

        // DOMAINS
        const val idlixAPI = "https://tv6.idlixku.com"
        const val kissKhAPI = "https://kisskh.co" // Updated but unused in loadLinks
        const val dramadripAPI = "https://dramadrip.com"
        const val ridomoviesAPI = "https://ridomovies.tv"
        const val showflixAPI = "https://showflix.store"
        const val nineTvAPI = "https://moviesapi.club"
        const val nepuAPI = "https://nepu.to"
        const val vidsrctoAPI = "https://vidsrc.cc"
        const val vidsrcxyzAPI = "https://vidsrc-embed.su"
        const val zshowAPI = "https://zshow.tv"
        
        // NEW DOMAINS
        const val moflixAPI = "https://moflix-stream.xyz"
        const val emoviesAPI = "https://emovies.si"
        const val zoechipAPI = "https://www1.zoechip.to"
        const val watch32API = "https://watch32.sx"
        const val soapyAPI = "https://soapy.to"
        const val multimoviesAPI = "https://multimovies.cloud"
        const val extramoviesAPI = "https://extramovies.garden"
        const val allmovielandAPI = "https://allmovieland.ac"
        const val mappleTvApi = "https://mapple.uk"

        fun getType(t: String?): TvType = when (t) { "movie" -> TvType.Movie else -> TvType.TvSeries }
        fun getStatus(t: String?): ShowStatus = when (t) { "Returning Series" -> ShowStatus.Ongoing else -> ShowStatus.Completed }
    }

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

    private fun getImageUrl(link: String?) = if (link == null) null else if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    private fun getOriImageUrl(link: String?) = if (link == null) null else if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adultQuery = if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("${request.data}$adultQuery&page=$page").parsedSafe<Results>()?.results?.mapNotNull { it.toSearchResponse(type) } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse {
        return newMovieSearchResponse(title ?: name ?: originalTitle ?: return newMovieSearchResponse("", "", TvType.Movie), Data(id = id, type = mediaType ?: type).toJson(), TvType.Movie) {
            this.posterUrl = getImageUrl(posterPath); this.score = Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)
    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}").parsedSafe<Results>()?.results?.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = try { if (url.startsWith("https://www.themoviedb.org/")) Data(url.removeSuffix("/").split("/").lastOrNull()?.toIntOrNull(), if (url.contains("/movie/")) "movie" else "tv") else parseJson<Data>(url) } catch (e: Exception) { null } ?: throw ErrorLoadingException("Invalid URL")
        val type = getType(data.type)
        val res = app.get(if (type == TvType.Movie) "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=alternative_titles,credits,external_ids,keywords,videos,recommendations" else "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=alternative_titles,credits,external_ids,keywords,videos,recommendations").parsedSafe<MediaDetail>() ?: throw ErrorLoadingException("Invalid Json Response")
        
        val title = res.title ?: res.name ?: return null
        val year = (res.releaseDate ?: res.firstAirDate)?.split("-")?.first()?.toIntOrNull()
        
        if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey").parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                    newEpisode(LinkData(data.id, res.external_ids?.imdb_id, res.external_ids?.tvdb_id, data.type, eps.seasonNumber, eps.episodeNumber, title = title, year = season.airDate?.split("-")?.first()?.toIntOrNull(), orgTitle = res.originalTitle?:res.originalName).toJson()) {
                        this.name = eps.name; this.season = eps.seasonNumber; this.episode = eps.episodeNumber; this.posterUrl = getImageUrl(eps.stillPath); this.score = Score.from10(eps.voteAverage); this.description = eps.overview; this.addDate(eps.airDate)
                    }
                }
            }?.flatten() ?: listOf()
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = getOriImageUrl(res.posterPath); this.backgroundPosterUrl = getOriImageUrl(res.backdropPath); this.year = year; this.plot = res.overview; this.tags = res.genres?.mapNotNull { it.name }; this.score = Score.from10(res.vote_average?.toString()); this.showStatus = getStatus(res.status); this.recommendations = res.recommendations?.results?.mapNotNull { it.toSearchResponse() }; this.actors = res.credits?.cast?.mapNotNull { ActorData(Actor(it.name?:it.originalName?:"", getImageUrl(it.profilePath)), roleString = it.character) }; addTrailer(res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }); addTMDbId(data.id.toString()); addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, LinkData(data.id, res.external_ids?.imdb_id, res.external_ids?.tvdb_id, data.type, title = title, year = year, orgTitle = res.originalTitle?:res.originalName).toJson()) {
                this.posterUrl = getOriImageUrl(res.posterPath); this.backgroundPosterUrl = getOriImageUrl(res.backdropPath); this.year = year; this.plot = res.overview; this.duration = res.runtime; this.tags = res.genres?.mapNotNull { it.name }; this.score = Score.from10(res.vote_average?.toString()); this.recommendations = res.recommendations?.results?.mapNotNull { it.toSearchResponse() }; this.actors = res.credits?.cast?.mapNotNull { ActorData(Actor(it.name?:it.originalName?:"", getImageUrl(it.profilePath)), roleString = it.character) }; addTrailer(res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }); addTMDbId(data.id.toString()); addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val res = parseJson<LinkData>(data)
        runAllAsync(
            { invokeIdlix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeMoflix(res.id, res.season, res.episode, callback) },
            { invokeEmovies(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeZoechip(res.title, res.year, res.season, res.episode, callback) },
            { invokeWatch32(res.title, res.season, res.episode, res.year, subtitleCallback, callback) },
            { invokeSoapy(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback) },
            { invokeExtramovies(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeAllMovieland(res.imdbId, res.season, res.episode, callback) },
            { invokeMappleTv(res.id, res.title, res.season, res.episode, callback) },
            { invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback) },
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) invokeVidsrccc(res.id, res.imdbId, res.season, res.episode, callback) },
            { invokeShowflix(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeNepu(res.title, res.year, res.season, res.episode, callback) },
            { invokeNinetv(res.id, res.season, res.episode, subtitleCallback, callback) },
            { invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback) },
            { invokeZshow(res.title, res.year, res.season, res.episode, subtitleCallback, callback) },
            { invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback) }
        )
        return true
    }

    data class LinkData(val id: Int? = null, val imdbId: String? = null, val tvdbId: Int? = null, val type: String? = null, val season: Int? = null, val episode: Int? = null, val title: String? = null, val year: Int? = null, val orgTitle: String? = null, val lastSeason: Int? = null)
    data class Data(val id: Int? = null, val type: String? = null)
    data class Results(@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf())
    data class Media(@JsonProperty("id") val id: Int? = null, @JsonProperty("name") val name: String? = null, @JsonProperty("title") val title: String? = null, @JsonProperty("original_title") val originalTitle: String? = null, @JsonProperty("original_name") val originalName: String? = null, @JsonProperty("media_type") val mediaType: String? = null, @JsonProperty("poster_path") val posterPath: String? = null, @JsonProperty("vote_average") val voteAverage: Double? = null)
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
