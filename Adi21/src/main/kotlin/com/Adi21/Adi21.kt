package com.Adi21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class Adi21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "Adi21Hybrid"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    private val apiKey = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val vidSrcBase = "https://vidsrc.cc/v2/embed"

    override val mainPage: List<MainPageData> = mainPageOf(
        "movie_popular,Popular Movies" to "Movie Popular",
        "movie_top_rated,Top Rated Movies" to "Movie Top Rated",
        "movie_now_playing,Now Playing" to "Movie Now Playing",
        "tv_popular,Popular TV" to "TV Popular",
        "tv_top_rated,Top Rated TV" to "TV Top Rated"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val key = request.name.split(",").first()
        val type = if (key.startsWith("tv_")) "tv" else "movie"
        val endpoint = key.substringAfter("_")
        val url = "$mainUrl/$type/$endpoint?api_key=$apiKey&language=en-US&page=$page"

        val res = app.get(url).parsedSafe<TMDBListResponse>()?.results ?: return newHomePageResponse(request.name, listOf())
        val list = res.map {
            newMovieSearchResponse(it.titleOrName(), it.id.toString(), if (it.isTv()) TvType.TvSeries else TvType.Movie) {
                this.posterUrl = it.posterFullPath()
                this.plot = it.overview
            }
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search/multi?api_key=$apiKey&language=en-US&query=$encoded&page=1&include_adult=false"
        val res = app.get(url).parsedSafe<TMDBListResponse>()?.results ?: return listOf()

        return res.filter { it.media_type != "person" }.map {
            newMovieSearchResponse(it.titleOrName(), it.id.toString(), if (it.isTv()) TvType.TvSeries else TvType.Movie) {
                this.posterUrl = it.posterFullPath()
                this.plot = it.overview
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val isTv = url.contains("tv")

        val detailsUrl = if (isTv)
            "$mainUrl/tv/$id?api_key=$apiKey&language=en-US"
        else
            "$mainUrl/movie/$id?api_key=$apiKey&language=en-US"

        val details = app.get(detailsUrl).parsedSafe<TMDBDetail>() ?: throw ErrorLoadingException("No detail")

        val title = details.titleOrName()
        val poster = details.posterFullPath()
        val year = details.getYear()
        val score = Score.from10(details.vote_average?.toString())
        val trailerKey = getTrailerKey(id, isTv)
        val trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        val data = LoadData(id = id, isTv = isTv)

        return newMovieLoadResponse(title, "tmdb://$id", if (isTv) TvType.TvSeries else TvType.Movie, data.toJson()) {
            this.posterUrl = poster
            this.year = year
            this.plot = details.overview
            this.score = score
            addTrailer(trailerUrl, addRaw = true)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val info = parseJson<LoadData>(data)
        val tmdbId = info.id ?: return false
        val isTv = info.isTv == true

        val embedUrl = if (isTv)
            "$vidSrcBase/tv/$tmdbId/1/1"
        else
            "$vidSrcBase/movie/$tmdbId"

        // Tambahkan link video utama
        callback.invoke(
            ExtractorLink(
                name,
                "Vidsrc",
                embedUrl,
                "https://vidsrc.cc/",
                Qualities.Unknown.value,
                false
            )
        )

        // Coba muat subtitle otomatis dari vidsrc API
        try {
            val subsUrl = "https://vidsrc.cc/v2/api/subs/$tmdbId.json"
            val subResponse = app.get(subsUrl).parsedSafe<VidsrcSubtitleResponse>()
            subResponse?.subs?.forEach {
                subtitleCallback.invoke(
                    newSubtitleFile(
                        it.lang ?: "Unknown",
                        it.file ?: return@forEach
                    )
                )
            }
        } catch (_: Exception) {
            // Jika gagal ambil subtitle, gunakan fallback
            subtitleCallback.invoke(
                newSubtitleFile("English", "https://example.com/empty.vtt")
            )
        }

        return true
    }

    private suspend fun getTrailerKey(id: String, isTv: Boolean): String? {
        val url = if (isTv)
            "$mainUrl/tv/$id/videos?api_key=$apiKey&language=en-US"
        else
            "$mainUrl/movie/$id/videos?api_key=$apiKey&language=en-US"

        val res = app.get(url).parsedSafe<TMDBVideosResponse>() ?: return null
        return res.results?.firstOrNull { it.site == "YouTube" && it.type?.contains("Trailer", true) == true }?.key
    }
}

/* ---- Data classes ---- */
data class TMDBListResponse(@JsonProperty("results") val results: ArrayList<TMDBItem>? = arrayListOf())

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

data class VidsrcSubtitleResponse(
    @JsonProperty("subs") val subs: ArrayList<SubtitleItem>? = arrayListOf()
)

data class SubtitleItem(
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("file") val file: String? = null
)

data class LoadData(
    val id: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isTv: Boolean? = null
)
