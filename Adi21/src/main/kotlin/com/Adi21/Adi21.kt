package com.Adi21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Adi21Hybrid:
 * - Metadata (search, details, main page) from TMDB
 * - Streaming embed links from Vidsrc.cc
 *
 * Security: Provide TMDB API key via environment variable TMDB_API_KEY
 * or replace "<YOUR_TMDB_API_KEY>" locally BEFORE building.
 */

class Adi21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "Adi21"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Secure way to read API key at runtime:
    // 1) Set environment variable TMDB_API_KEY
    // 2) Or replace "<YOUR_TMDB_API_KEY>" locally (not recommended for public repos)
    private val apiKey: String by lazy {
        System.getenv("TMDB_API_KEY") ?: "<1cfadd9dbfc534abf6de40e1e7eaf4c7>"
    }

    // Vidsrc base for embed:
    private val vidSrcBase = "https://vidsrc.cc/v2/embed"

    override val mainPage: List<MainPageData> = mainPageOf(
        "movie_popular,Popular Movies" to "Movie Popular",
        "movie_top_rated,Top Rated Movies" to "Movie Top Rated",
        "movie_now_playing,Now Playing" to "Movie Now Playing",
        "tv_popular,Popular TV" to "TV Popular",
        "tv_top_rated,Top Rated TV" to "TV Top Rated"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val parts = request.name.split(",")
        val key = parts.first()
        val title = request.name
        val (type, endpoint) = when {
            key.startsWith("movie_") -> Pair("movie", key.removePrefix("movie_"))
            key.startsWith("tv_") -> Pair("tv", key.removePrefix("tv_"))
            else -> Pair("movie", "popular")
        }
        val tmdbEndpoint = when (endpoint) {
            "popular" -> "/$type/popular"
            "top_rated" -> "/$type/top_rated"
            "now_playing" -> "/movie/now_playing"
            else -> "/$type/popular"
        }

        val url = "$mainUrl$tmdbEndpoint?api_key=$apiKey&language=en-US&page=$page"
        val resp = app.get(url).parsedSafe<TMDBListResponse>()
        val items = resp?.results ?: emptyList()

        val home = items.map { m ->
            newMovieSearchResponse(
                m.titleOrName(),
                m.id.toString(),
                if (m.isTv()) TvType.TvSeries else TvType.Movie,
                false
            ) {
                this.posterUrl = m.posterFullPath()
                // FIX 1: Mengubah 'plot' kembali ke 'description' di SearchResponse builder (Baris 82)
                this.description = m.overview 
            }
        }

        return newHomePageResponse(title, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/multi"
        val resp = app.get(
            url,
            params = mapOf(
                "api_key" to apiKey,
                "language" to "en-US",
                "query" to query,
                "page" to "1",
                "include_adult" to "false"
            )
        ).parsedSafe<TMDBListResponse>()
        val items = resp?.results ?: emptyList()
        return items.mapNotNull { m ->
            // Filter out non-movie/tv if needed
            if (m.media_type == "person") return@mapNotNull null
            newMovieSearchResponse(
                m.titleOrName(),
                m.id.toString(),
                if (m.isTv()) TvType.TvSeries else TvType.Movie,
                false
            ) {
                this.posterUrl = m.posterFullPath()
                // FIX 1: Mengubah 'plot' kembali ke 'description' di SearchResponse builder (Baris 115)
                this.description = m.overview 
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Here url is expected to be TMDB id or tmdb://movie/12345 or tmdb://tv/12345
        val idRaw = url.substringAfterLast("/").substringAfter("://")
        val (isTv, idStr) = if (url.contains("tmdb://tv") || url.startsWith("tv/")) {
            Pair(true, idRaw)
        } else if (url.contains("tmdb://movie") || url.startsWith("movie/")) {
            Pair(false, idRaw)
        } else {
            // fallback: assume numeric id and movie
            Pair(false, idRaw)
        }

        val id = idStr
        // URL detail perbaikan
        val detailsUrl = if (isTv) "$mainUrl/tv/$id?api_key=$apiKey&language=en-US" else "$mainUrl/movie/$id?api_key=$apiKey&language=en-US"
        val details = app.get(detailsUrl).parsedSafe<TMDBDetail>() ?: throw ErrorLoadingException("No detail")

        val title = details.titleOrName()
        val poster = details.posterFullPath()
        val year = details.getYear()
        val description = details.overview
        val score = Score.from10(details.vote_average?.toString())
        val trailerKey = getTrailerKey(id, isTv) // helper below
        val trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        // Build LoadData that loadLinks expects: we'll include TMDB id and type flag
        val data = LoadData(
            id = id,
            season = null,
            episode = null,
            detailPath = null,
            isTv = if (isTv) true else false
        )

        return if (isTv) {
            // For TV we still require season/episode selection in UI; we will provide a simple placeholder
            // Generate a single "series" load response — episodes are requested later by the player UI
            newMovieLoadResponse(title, "tmdb://tv/$id", TvType.TvSeries, data.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description 
                this.score = score
                addTrailer(trailerUrl, addRaw = true)
            }
        } else {
            newMovieLoadResponse(title, "tmdb://movie/$id", TvType.Movie, data.toJson()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description 
                this.score = score
                addTrailer(trailerUrl, addRaw = true)
            }
        }
    }

    // loadLinks: uses vidsrc embed
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val tmdbId = media.id ?: return false
        val isTv = media.isTv == true

        val embedUrl = if (isTv) {
            val season = media.season ?: 1
            val episode = media.episode ?: 1
            "$vidSrcBase/tv/$tmdbId/$season/$episode"
        } else {
            "$vidSrcBase/movie/$tmdbId"
        }

        callback.invoke(
            newExtractorLink(
                this.name,
                "Vidsrc",
                embedUrl,
                INFER_TYPE
            ) {
                // FIX 2: Menghapus 'val' yang menyebabkan 'val cannot be reassigned' (Baris 204)
                this.referer = "https://vidsrc.cc/" 
                this.quality = Qualities.Unknown.value
                this.isM3u8 = false 
            }
        )

        // Vidsrc sometimes hosts subtitles inside the embed — if you have a subtitle extraction step,
        // you can implement it here (scrape /call an endpoint). For now we return true.
        return true
    }

    // Helper: fetch YouTube trailer key from TMDB /videos endpoint
    private suspend fun getTrailerKey(id: String, isTv: Boolean): String? {
        val url = if (isTv) "$mainUrl/tv/$id/videos?api_key=$apiKey&language=en-US" else "$mainUrl/movie/$id/videos?api_key=$apiKey&language=en-US"
        val resp = app.get(url).parsedSafe<TMDBVideosResponse>() ?: return null
        return resp.results?.firstOrNull { it.site == "YouTube" && it.type?.contains("Trailer", true) == true }?.key
    }
}

/* --- Data classes for TMDB minimal parsing --- */

data class TMDBListResponse(
    @JsonProperty("results") val results: ArrayList<TMDBItem>? = arrayListOf()
)

data class TMDBItem(
    @JsonProperty("id") val id: Long = 0L,
    @JsonProperty("media_type") val media_type: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val poster_path: String? = null,
    @JsonProperty("vote_average") val vote_average: Double? = null
) {
    fun titleOrName(): String = title ?: name ?: ""
    fun isTv(): Boolean = (media_type == "tv" || name != null)
    fun posterFullPath(): String? = poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class TMDBDetail(
    @JsonProperty("id") val id: Long = 0L,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val poster_path: String? = null,
    @JsonProperty("vote_average") val vote_average: Double? = null,
    @JsonProperty("release_date") val release_date: String? = null,
    @JsonProperty("first_air_date") val first_air_date: String? = null
) {
    fun titleOrName(): String = title ?: name ?: ""
    fun posterFullPath(): String? = poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getYear(): Int? = (release_date ?: first_air_date)?.substringBefore("-")?.toIntOrNull()
}

data class TMDBVideosResponse(
    @JsonProperty("results") val results: ArrayList<TMDBVideo>? = arrayListOf()
)

data class TMDBVideo(
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("key") val key: String? = null
)

/* --- LoadData extended to include isTv flag --- */
data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null,
    val isTv: Boolean? = null
)
