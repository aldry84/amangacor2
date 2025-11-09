package com.AdicinemaxNew

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

open class AdicinemaxNew(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    // API Configuration
    private val tmdbAPI by lazy { runBlocking { getApiBase() } }
    private val wpRedisInterceptor by lazy { CloudflareKiller() }
    
    companion object {
        // API URLs dengan fungsi masing-masing
        private const val OFFICIAL_TMDB_URL = "https://api.themoviedb.org/3"
        private const val CINEMETA_URL = "https://v3-cinemeta.strem.io"
        private const val PROXY_LIST_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Proxylist.txt"
        private const val VIDSRC_CC = "https://vidsrc.cc"
        private const val VIDSRC_XYZ = "https://vidsrc.xyz"
        
        // API Key TMDB
        private const val apiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
        
        private var currentBaseUrl: String? = null
        private val gson = Gson()

        /**
         * Fungsi: Sistem fallback proxy untuk TMDB API
         * Prioritas: TMDB Official -> Proxy List -> TMDB Official (fallback)
         */
        suspend fun getApiBase(): String {
            currentBaseUrl?.let { return it }

            // 1. Coba TMDB official terlebih dahulu
            if (isOfficialAvailable()) {
                currentBaseUrl = OFFICIAL_TMDB_URL
                Log.d("TMDB", "✅ Using official TMDB API")
                return OFFICIAL_TMDB_URL
            }

            // 2. Jika gagal, coba proxy dari remote list
            val proxies = fetchProxyList()
            for (proxy in proxies) {
                if (isProxyWorking(proxy)) {
                    currentBaseUrl = proxy
                    Log.d("TMDB", "✅ Switched to proxy: $proxy")
                    return proxy
                }
            }

            // 3. Final fallback ke official
            Log.e("TMDB", "❌ No proxy worked, fallback to official")
            return OFFICIAL_TMDB_URL
        }

        private suspend fun isOfficialAvailable(): Boolean {
            val testUrl = "$OFFICIAL_TMDB_URL/movie/1290879?api_key=$apiKey&append_to_response=alternative_titles,credits,external_ids,videos,recommendations"

            return try {
                val response = app.get(
                    testUrl,
                    timeout = 2000,
                    headers = mapOf(
                        "Cache-Control" to "no-cache",
                        "Pragma" to "no-cache"
                    )
                )
                response.okhttpResponse.code in listOf(200, 304)
            } catch (e: Exception) {
                Log.d("TMDB", "Official TMDB unavailable: ${e.message}")
                false
            }
        }

        private suspend fun isProxyWorking(proxyUrl: String): Boolean {
            val testUrl = "$proxyUrl/movie/1290879?api_key=$apiKey&append_to_response=alternative_titles,credits,external_ids,videos,recommendations"

            return try {
                val response = app.get(
                    testUrl,
                    timeout = 2000,
                    headers = mapOf(
                        "Cache-Control" to "no-cache",
                        "Pragma" to "no-cache"
                    )
                )
                response.okhttpResponse.code in listOf(200, 304)
            } catch (e: Exception) {
                Log.d("TMDB", "Proxy failed: $proxyUrl -> ${e.message}")
                false
            }
        }

        /**
         * Fungsi: Mengambil daftar proxy dari GitHub
         * Format: JSON dengan array "proxies"
         */
        private suspend fun fetchProxyList(): List<String> = try {
            val response = app.get(PROXY_LIST_URL).text
            val json = JSONObject(response)
            val arr = json.getJSONArray("proxies")

            List(arr.length()) { arr.getString(it).trim().removeSuffix("/") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("TMDB", "Error fetching proxy list: ${e.message}")
            emptyList()
        }

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

    // Halaman utama dengan konten dari TMDB
    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "/trending/movie/week?api_key=$apiKey&region=US" to "Popular Movies",
        "/trending/tv/week?api_key=$apiKey&region=US" to "Popular TV Shows",
        "/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix Originals",
        "/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon Originals"
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
        val home = app.get("$tmdbAPI${request.data}&page=$page", timeout = 10000)
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json response")
        
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=false")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }

        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        
        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.original_language == "ja" || res.original_language == "zh")

        val actors = res.credits?.cast?.mapNotNull { cast ->
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                Actor(name, getImageUrl(cast.profilePath)), 
                roleString = cast.character
            )
        } ?: emptyList()

        val recommendations = res.recommendations?.results?.mapNotNull { media -> 
            media.toSearchResponse() 
        }

        val trailer = res.videos?.results.orEmpty()
            .filter { it.type == "Trailer" }
            .map { "https://www.youtube.com/watch?v=${it.key}" }
            .firstOrNull()

        /**
         * Fungsi: Integrasi Cinemeta untuk konten anime
         * Mengambil metadata spesifik anime dari Cinemeta
         */
        if (isAnime && type == TvType.TvSeries) {
            val animeType = if (data.type.contains("tv", true)) "series" else "movie"
            val imdbId = res.external_ids?.imdb_id.orEmpty()
            
            val cineJsonText = app.get("$CINEMETA_URL/meta/$animeType/$imdbId.json").text
            val cinejson = runCatching {
                gson.fromJson(cineJsonText, CinemetaRes::class.java)
            }.getOrNull()
            
            val animevideos = cinejson?.meta?.videos
            val jptitle = cinejson?.meta?.name

            val animeepisodes = animevideos
                ?.filter { it.season != 0 }
                ?.map { video ->
                    newEpisode(
                        LinkData(
                            id = data.id,
                            imdbId = res.external_ids?.imdb_id,
                            tvdbId = res.external_ids?.tvdb_id,
                            type = data.type,
                            season = video.season,
                            episode = video.number,
                            epid = null,
                            title = title,
                            year = video.released?.split("-")?.firstOrNull()?.toIntOrNull(),
                            orgTitle = orgTitle,
                            isAnime = true,
                            epsTitle = video.title, // <-- PERBAIKAN #2
                            jpTitle = jptitle,
                            date = video.released
                        ).toJson()
                    ) {
                        this.name = video.title // <-- PERBAIKAN #2
                        this.season = video.season
                        this.episode = video.number
                        this.posterUrl = video.thumbnail
                        this.score = Score.from10(video.rating)
                        this.description = video.description
                    }.apply {
                        this.addDate(video.released)
                    }
                } ?: emptyList()

            return newAnimeLoadResponse(title, url, TvType.Anime) {
                addEpisodes(DubStatus.Subbed, animeepisodes)
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = genres
                this.score = Score.from10((res.vote_average as? Number)?.toDouble()) // <-- PERBAIKAN #1
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                val seasonNumber = season.seasonNumber ?: return@mapNotNull null
                app.get("$tmdbAPI/tv/${data.id}/season/$seasonNumber?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(
                            LinkData(
                                data.id,
                                res.external_ids?.imdb_id,
                                res.external_ids?.tvdb_id,
                                data.type,
                                eps.seasonNumber,
                                eps.episodeNumber,
                                eps.id,
                                title = title,
                                year = year,
                                orgTitle = orgTitle
                            ).toJson()
                        ) {
                            this.name = eps.name
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath)
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                        }.apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = genres
                this.score = Score.from10((res.vote_average as? Number)?.toDouble()) // <-- PERBAIKAN #1
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    res.external_ids?.tvdb_id,
                    data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
                this.score = Score.from10((res.vote_average as? Number)?.toDouble()) // <-- PERBAIKAN #1
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)
        
        // Prioritaskan Vidsrc.cc sebagai primary extractor
        // !! KESALAHAN #3 DI SINI. Pastikan 'AdicinemaxNewExtractor' ada.
        AdicinemaxNewExtractor.invokeVidsrcCc(
            id = res.id,
            imdbId = res.imdbId,
            season = res.season,
            episode = res.episode,
            callback = callback
        )

        // Vidsrc.xyz sebagai backup extractor
        // !! KESALAHAN #3 DI SINI. Pastikan 'AdicinemaxNewExtractor' ada.
        AdicinemaxNewExtractor.invokeVidsrcXyz(
            id = res.imdbId,
            season = res.season,
            episode = res.episode,
            callback = callback
        )

        return true
    }

    // Data classes untuk parsing response
    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val epid: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val orgTitle: String? = null,
        val isAnime: Boolean = false,
        val epsTitle: String? = null,
        val jpTitle: String? = null,
        val date: String? = null,
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

    data class CinemetaRes(
        @JsonProperty("meta") val meta: CinemetaMeta? = null
    )

    data class CinemetaMeta(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("videos") val videos: List<CinemetaVideo>? = null
    )

    data class CinemetaVideo(
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("description") val description: String? = null
    )

    data class Genres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class Cast(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("tvdb_id") val tvdb_id: Int? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val vote_average: Any? = null,
        @JsonProperty("original_language") val original_language: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    )

    data class Seasons(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )
}
