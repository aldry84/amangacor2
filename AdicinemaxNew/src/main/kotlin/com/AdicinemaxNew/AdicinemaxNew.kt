package com.AdicinemaxNew

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class AdicinemaxNew : MainAPI() {
    override var mainUrl = "https://vidsrc-embed.ru"
    override var name = "AdicinemaxNew"
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    
    companion object {
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    // Main page structure seperti di Adimoviebox
    override val mainPage = mainPageOf(
        "movie,latest" to "Latest Movies",
        "tv,latest" to "Latest TV Shows", 
        "episode,latest" to "Latest Episodes",
        "movie,trending" to "Trending Movies",
        "tv,trending" to "Trending TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val params = request.data.split(",")
        val type = params[0]
        val category = params[1]
        
        val items = when {
            type == "movie" && category == "latest" -> parseLatestMovies(page)
            type == "tv" && category == "latest" -> parseLatestTVShows(page)
            type == "episode" && category == "latest" -> parseLatestEpisodes(page)
            type == "movie" && category == "trending" -> getTMDBTrending("movie", page)
            type == "tv" && category == "trending" -> getTMDBTrending("tv", page)
            else -> emptyList()
        }
        
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return searchTMDB(query)
    }

    override suspend fun loadLinks(
        data: String, 
        isCasting: Boolean, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        
        // PERBAIKAN: Handle null values dengan safe calls
        val embedUrl = buildEmbedUrl(media.type ?: "", media.tmdbId ?: "", media.imdbId ?: "", media.season, media.episode)
        
        return if (embedUrl.isNotBlank()) {
            getStreamLinks(embedUrl, mainUrl, subtitleCallback, callback)
        } else {
            false
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val media = parseJson<LoadData>(url)
        
        // PERBAIKAN: Handle null values dengan safe calls
        return if ((media.type ?: "") == "movie") {
            loadMovieContent(media.tmdbId ?: "", media.imdbId ?: "")
        } else {
            loadTVContent(media.tmdbId ?: "", media.imdbId ?: "")
        }
    }

    // Vidsrc API Functions - Diperbaiki dengan error handling yang lebih baik
    private suspend fun parseLatestMovies(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/movies/latest/page-$page.json"
            val response = app.get(url).text
            val json = parseJson<VidsrcResponse>(response)
            
            json.result?.mapNotNull { item ->
                parseVidsrcMovieResult(item)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun parseLatestTVShows(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/tvshows/latest/page-$page.json"
            val response = app.get(url).text
            val json = parseJson<VidsrcResponse>(response)
            
            json.result?.mapNotNull { item ->
                parseVidsrcTvResult(item)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun parseLatestEpisodes(page: Int): List<SearchResponse> {
        return try {
            val url = "$mainUrl/episodes/latest/page-$page.json"
            val response = app.get(url).text
            val json = parseJson<VidsrcResponse>(response)
            
            json.result?.mapNotNull { item ->
                parseVidsrcEpisodeResult(item)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseVidsrcMovieResult(item: VidsrcItem): SearchResponse? {
        return try {
            val tmdbId = item.tmdb_id.takeIf { it.isNotBlank() } ?: ""
            val imdbId = item.imdb_id.takeIf { it.isNotBlank() } ?: ""
            val title = item.title?.trim()
            val posterPath = item.poster
            
            if (title.isNullOrEmpty() || title == "n/A") return null
            
            val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.year?.take(4)?.toIntOrNull()
            val data = LoadData(
                type = "movie",
                tmdbId = tmdbId,
                imdbId = imdbId
            )
            
            newMovieSearchResponse(title, data.toJson(), TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVidsrcTvResult(item: VidsrcItem): SearchResponse? {
        return try {
            val tmdbId = item.tmdb_id.takeIf { it.isNotBlank() } ?: ""
            val imdbId = item.imdb_id.takeIf { it.isNotBlank() } ?: ""
            val title = item.title?.trim()
            val posterPath = item.poster
            
            if (title.isNullOrEmpty() || title == "n/A") return null
            
            val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.year?.take(4)?.toIntOrNull()
            val data = LoadData(
                type = "tv",
                tmdbId = tmdbId,
                imdbId = imdbId
            )
            
            newTvSeriesSearchResponse(title, data.toJson(), TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVidsrcEpisodeResult(item: VidsrcItem): SearchResponse? {
        return try {
            val tmdbId = item.tmdb_id.takeIf { it.isNotBlank() } ?: ""
            val imdbId = item.imdb_id.takeIf { it.isNotBlank() } ?: ""
            val title = item.title?.trim()
            val season = item.season ?: "1"
            val episode = item.episode ?: "1"
            val posterPath = item.poster
            
            if (title.isNullOrEmpty() || title == "n/A") return null
            
            val posterUrl = if (posterPath.isNotBlank() && posterPath != "n/A") {
                "$TMDB_IMAGE_BASE$posterPath"
            } else {
                ""
            }
            
            val year = item.year?.take(4)?.toIntOrNull()
            val data = LoadData(
                type = "tv",
                tmdbId = tmdbId,
                imdbId = imdbId,
                season = season.toIntOrNull(),
                episode = episode.toIntOrNull()
            )
            
            newTvSeriesSearchResponse("$title S${season}E${episode}", data.toJson(), TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } catch (e: Exception) {
            null
        }
    }

    // TMDB Functions - Diperbaiki dengan struktur data class
    private suspend fun getTMDBTrending(mediaType: String, page: Int): List<SearchResponse> {
        return try {
            val url = "$TMDB_BASE_URL/trending/$mediaType/week?api_key=$tmdbApiKey&page=$page"
            val response = app.get(url).text
            val json = parseJson<TMDBResponse>(response)
            
            json.results?.mapNotNull { item ->
                parseTMDBResult(item, mediaType)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchTMDB(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/multi?api_key=$tmdbApiKey&query=$encodedQuery&page=1"
            val response = app.get(url).text
            val json = parseJson<TMDBResponse>(response)
            
            json.results?.mapNotNull { item ->
                val mediaType = item.media_type
                if (mediaType == "movie" || mediaType == "tv") {
                    parseTMDBResult(item, mediaType)
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun parseTMDBResult(item: TMDBItem, mediaType: String): SearchResponse? {
        return try {
            val id = item.id.toString()
            val title = when (mediaType) {
                "movie" -> item.title
                "tv" -> item.name
                else -> return null
            } ?: return null
            
            val posterPath = item.poster_path
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            
            val releaseDate = when (mediaType) {
                "movie" -> item.release_date
                "tv" -> item.first_air_date
                else -> null
            }
            
            // PERBAIKAN: Menambahkan score dari vote_average seperti di Adimoviebox
            val score = item.vote_average?.let { 
                Score.from10(it.toString()) 
            }
            
            // Get IMDB ID
            val imdbId = getIMDBId(mediaType, id)
            
            val data = LoadData(
                type = mediaType,
                tmdbId = id,
                imdbId = imdbId
            )
            
            if (mediaType == "movie") {
                newMovieSearchResponse(title, data.toJson(), TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = releaseDate?.take(4)?.toIntOrNull()
                    this.score = score // PERBAIKAN: Menambahkan score
                }
            } else {
                newTvSeriesSearchResponse(title, data.toJson(), TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = releaseDate?.take(4)?.toIntOrNull()
                    this.score = score // PERBAIKAN: Menambahkan score
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getIMDBId(mediaType: String, tmdbId: String): String {
        return try {
            val url = "$TMDB_BASE_URL/$mediaType/$tmdbId/external_ids?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<ExternalIdsResponse>(response)
            json.imdb_id.takeIf { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun loadMovieContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/movie/$tmdbId?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<TMDBMovieDetail>(response)
            
            val title = json.title ?: return null
            val posterPath = json.poster_path
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val overview = json.overview ?: "No description available"
            val releaseDate = json.release_date
            val runtime = json.runtime ?: 0
            val genres = json.genres?.map { it.name } ?: emptyList()

            // PERBAIKAN: Menambahkan score dari vote_average seperti di Adimoviebox
            val score = json.vote_average?.let { 
                Score.from10(it.toString()) 
            }

            // Get cast - PERBAIKAN: Mengembalikan List<Actor> bukan List<ActorData>
            val cast = getMovieCast(tmdbId)
            
            val data = LoadData(
                type = "movie",
                tmdbId = tmdbId,
                imdbId = imdbId
            )

            newMovieLoadResponse(title, data.toJson(), TvType.Movie, data.toJson()) {
                this.posterUrl = posterUrl
                this.year = releaseDate?.take(4)?.toIntOrNull()
                this.plot = overview
                this.duration = runtime
                this.tags = genres
                this.score = score // PERBAIKAN: Menambahkan score
                // PERBAIKAN: addActors sekarang menerima List<Actor>
                addActors(cast)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadTVContent(tmdbId: String, imdbId: String): LoadResponse? {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<TMDBTVDetail>(response)
            
            val title = json.name ?: return null
            val posterPath = json.poster_path
            val posterUrl = if (posterPath.isNotBlank()) "$TMDB_IMAGE_BASE$posterPath" else ""
            val overview = json.overview ?: "No description available"
            val firstAirDate = json.first_air_date
            val numberOfSeasons = json.number_of_seasons ?: 0
            val genres = json.genres?.map { it.name } ?: emptyList()

            // PERBAIKAN: Menambahkan score dari vote_average seperti di Adimoviebox
            val score = json.vote_average?.let { 
                Score.from10(it.toString()) 
            }

            // Get cast - PERBAIKAN: Mengembalikan List<Actor> bukan List<ActorData>
            val cast = getTVCast(tmdbId)
            
            // Get episodes for all seasons
            val allEpisodes = mutableListOf<Episode>()
            
            for (seasonNumber in 1..numberOfSeasons) {
                try {
                    val seasonEpisodes = getSeasonEpisodes(tmdbId, seasonNumber, imdbId)
                    allEpisodes.addAll(seasonEpisodes)
                } catch (e: Exception) {
                    continue
                }
            }
            
            val data = LoadData(
                type = "tv", 
                tmdbId = tmdbId,
                imdbId = imdbId
            )

            newTvSeriesLoadResponse(title, data.toJson(), TvType.TvSeries, allEpisodes) {
                this.posterUrl = posterUrl
                this.year = firstAirDate?.take(4)?.toIntOrNull()
                this.plot = overview
                this.tags = genres
                this.score = score // PERBAIKAN: Menambahkan score
                // PERBAIKAN: addActors sekarang menerima List<Actor>
                addActors(cast)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDate(dateString: String?): Long? {
        return try {
            if (dateString.isNullOrBlank()) return null
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getSeasonEpisodes(tmdbId: String, seasonNumber: Int, imdbId: String): List<Episode> {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<TMDBSeasonDetail>(response)
            val episodesArray = json.episodes ?: return emptyList()
            
            val episodes = mutableListOf<Episode>()
            
            for (episode in episodesArray) {
                val episodeNumber = episode.episode_number ?: 0
                if (episodeNumber == 0) continue
                
                val episodeTitle = episode.name ?: "Episode $episodeNumber"
                val overview = episode.overview ?: "No description available"
                val stillPath = episode.still_path
                val stillUrl = if (stillPath.isNotBlank()) "$TMDB_IMAGE_BASE$stillPath" else ""
                val airDate = episode.air_date ?: ""
                
                val data = LoadData(
                    type = "tv",
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    season = seasonNumber,
                    episode = episodeNumber
                )
                
                episodes.add(
                    newEpisode(data.toJson()) {
                        this.name = episodeTitle
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.posterUrl = stillUrl
                        this.description = overview
                        this.date = parseDate(airDate)
                    }
                )
            }
            
            episodes
        } catch (e: Exception) {
            emptyList()
        }
    }

    // PERBAIKAN: Mengembalikan List<Actor> dengan constructor yang benar
    private suspend fun getMovieCast(tmdbId: String): List<Actor> {
        return try {
            val url = "$TMDB_BASE_URL/movie/$tmdbId/credits?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<TMDBCreditsResponse>(response)
            
            json.cast?.take(10)?.mapNotNull { cast ->
                Actor(
                    cast.name ?: return@mapNotNull null,
                    if (cast.profile_path.isNotBlank()) "$TMDB_IMAGE_BASE${cast.profile_path}" else null
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // PERBAIKAN: Mengembalikan List<Actor> dengan constructor yang benar
    private suspend fun getTVCast(tmdbId: String): List<Actor> {
        return try {
            val url = "$TMDB_BASE_URL/tv/$tmdbId/credits?api_key=$tmdbApiKey"
            val response = app.get(url).text
            val json = parseJson<TMDBCreditsResponse>(response)
            
            json.cast?.take(10)?.mapNotNull { cast ->
                Actor(
                    cast.name ?: return@mapNotNull null,
                    if (cast.profile_path.isNotBlank()) "$TMDB_IMAGE_BASE${cast.profile_path}" else null
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Utility Functions
    private fun buildEmbedUrl(type: String, tmdbId: String, imdbId: String, season: Int?, episode: Int?): String {
        return when (type) {
            "movie" -> {
                when {
                    imdbId.isNotBlank() -> "$mainUrl/embed/movie?imdb=$imdbId"
                    tmdbId.isNotBlank() -> "$mainUrl/embed/movie?tmdb=$tmdbId"
                    else -> ""
                }
            }
            "tv" -> {
                if (season != null && episode != null) {
                    when {
                        imdbId.isNotBlank() -> "$mainUrl/embed/tv?imdb=$imdbId&season=$season&episode=$episode"
                        tmdbId.isNotBlank() -> "$mainUrl/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
                        else -> ""
                    }
                } else {
                    when {
                        imdbId.isNotBlank() -> "$mainUrl/embed/tv?imdb=$imdbId"
                        tmdbId.isNotBlank() -> "$mainUrl/embed/tv?tmdb=$tmdbId"
                        else -> ""
                    }
                }
            }
            else -> ""
        }
    }

    private suspend fun getStreamLinks(
        embedUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // Dapatkan HTML dari embed URL
            val document = app.get(embedUrl, referer = referer).document
            
            // Cari iframe utama
            val iframe = document.selectFirst("iframe")
            val iframeSrc = iframe?.attr("src")
            
            if (iframeSrc != null) {
                // Jika iframeSrc adalah URL lengkap
                if (iframeSrc.startsWith("http")) {
                    loadExtractor(iframeSrc, embedUrl, subtitleCallback, callback)
                } else {
                    // Jika iframeSrc relative, buat URL lengkap
                    val fullIframeUrl = if (iframeSrc.startsWith("//")) {
                        "https:${iframeSrc}"
                    } else if (iframeSrc.startsWith("/")) {
                        "$mainUrl${iframeSrc}"
                    } else {
                        "$referer/$iframeSrc"
                    }
                    loadExtractor(fullIframeUrl, embedUrl, subtitleCallback, callback)
                }
                true
            } else {
                // Coba cari video player langsung
                val videoElement = document.selectFirst("video")
                val videoSource = videoElement?.selectFirst("source[src]")
                val videoUrl = videoSource?.attr("src")
                
                if (!videoUrl.isNullOrBlank()) {
                    // PERBAIKAN: Gunakan ExtractorLink constructor langsung dengan type yang benar
                    val isM3u8 = videoUrl.contains(".m3u8")
                    
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "Vidsrc Direct", 
                            url = videoUrl,
                            referer = referer,
                            quality = getQualityFromUrl(videoUrl),
                            isM3u8 = isM3u8
                        )
                    )
                    true
                } else {
                    // Fallback ke extractor biasa
                    loadExtractor(embedUrl, referer, subtitleCallback, callback)
                    true
                }
            }
        } catch (e: Exception) {
            // Fallback ke extractor biasa jika ada error
            try {
                loadExtractor(embedUrl, referer, subtitleCallback, callback)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            url.contains("240") -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
    }
}

// Data Classes untuk struktur JSON yang lebih baik
data class LoadData(
    val type: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

data class VidsrcResponse(
    @JsonProperty("result") val result: List<VidsrcItem>? = null
)

data class VidsrcItem(
    @JsonProperty("tmdb_id") val tmdb_id: String = "",
    @JsonProperty("imdb_id") val imdb_id: String = "",
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String = "",
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("season") val season: String? = null,
    @JsonProperty("episode") val episode: String? = null
)

data class TMDBResponse(
    @JsonProperty("results") val results: List<TMDBItem>? = null
)

data class TMDBItem(
    @JsonProperty("id") val id: Int = 0,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val poster_path: String = "",
    @JsonProperty("release_date") val release_date: String? = null,
    @JsonProperty("first_air_date") val first_air_date: String? = null,
    @JsonProperty("media_type") val media_type: String = "",
    @JsonProperty("vote_average") val vote_average: Double? = null // PERBAIKAN: Ditambahkan untuk score
)

data class ExternalIdsResponse(
    @JsonProperty("imdb_id") val imdb_id: String = ""
)

data class TMDBMovieDetail(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster_path") val poster_path: String = "",
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("release_date") val release_date: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val vote_average: Double? = null, // PERBAIKAN: Ditambahkan untuk score
    @JsonProperty("genres") val genres: List<Genre>? = null
)

data class TMDBTVDetail(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val poster_path: String = "",
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("first_air_date") val first_air_date: String? = null,
    @JsonProperty("number_of_seasons") val number_of_seasons: Int? = null,
    @JsonProperty("vote_average") val vote_average: Double? = null, // PERBAIKAN: Ditambahkan untuk score
    @JsonProperty("genres") val genres: List<Genre>? = null
)

data class TMDBSeasonDetail(
    @JsonProperty("episodes") val episodes: List<TMDBEpisode>? = null
)

data class TMDBEpisode(
    @JsonProperty("episode_number") val episode_number: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("still_path") val still_path: String = "",
    @JsonProperty("air_date") val air_date: String? = null
)

data class TMDBCreditsResponse(
    @JsonProperty("cast") val cast: List<TMDBCast>? = null
)

data class TMDBCast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("profile_path") val profile_path: String = ""
)

data class Genre(
    @JsonProperty("name") val name: String = ""
)
