// File: Adi21.kt

package com.Adi21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.ExtractorApi // Impor yang benar untuk Extractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson // Memastikan toJson terimpor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.Qualities // Mungkin perlu untuk konstanta kualitas

// --- KONSTANTA YANG DIPERBARUI ---
class Adi21 : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org"
    override var name = "Adi21 TMDB Source"
    override val instantLinkLoading = true
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val TMDB_API_KEY = "1cfadd9dbfc534abf6de40e1e7eaf4c7"
    private val VIDSRC_URL = "https://vidsrc.cc"

    private fun tmdbApi(path: String, page: Int = 1) =
        "$mainUrl/3/$path?api_key=$TMDB_API_KEY&page=$page"

    private fun String?.toPosterUrl(): String? {
        return if (this != null) "https://image.tmdb.org/t/p/w500$this" else null
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "trending/movie/day" to "Movies Trending Today",
        "trending/tv/day" to "TV Series Trending Today",
        "movie/popular" to "Popular Movies",
        "tv/popular" to "Popular TV Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val url = tmdbApi(request.data, page)

        val response = app.get(url).parsed<TmdbApiResult>()

        val mediaList = response.results.mapNotNull { item ->
            val type = if (item.mediaType == "movie" || request.data.contains("movie")) TvType.Movie else TvType.TvSeries

            newTvSeriesSearchResponse(item.title ?: item.name ?: return@mapNotNull null, item.tmdbId.toString(), type) {
                this.posterUrl = item.posterPath.toPosterUrl()
                // PERBAIKAN: Menggunakan rating (0-100)
                this.rating = item.voteAverage?.times(10)?.toInt()
                // PERBAIKAN: Menghapus 'this.set = "TMDB"' karena properti ini tidak ada
            }
        }

        return newHomePageResponse(request.name, mediaList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = tmdbApi("search/multi") + "&query=$query"

        return app.get(url).parsed<TmdbApiResult>().results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .mapNotNull { item ->
                val type = if (item.mediaType == "movie") TvType.Movie else TvType.TvSeries
                newTvSeriesSearchResponse(item.title ?: item.name ?: return@mapNotNull null, item.tmdbId.toString(), type) {
                    this.posterUrl = item.posterPath.toPosterUrl()
                    // PERBAIKAN: Menggunakan rating (0-100)
                    this.rating = item.voteAverage?.times(10)?.toInt()
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val tmdbId = url
        val typePath = if (tmdbId.contains("-S")) "tv" else "movie"

        val detailUrl = tmdbApi("$typePath/$tmdbId")
        val response = app.get(detailUrl).parsedSafe<TmdbDetail>()?.data ?: throw ErrorLoadingException("Failed to load TMDB detail")

        val title = response.title ?: response.name ?: ""
        val poster = response.posterPath.toPosterUrl()
        val tags = response.genres?.mapNotNull { it.name }
        val year = response.releaseDate?.substringBefore("-")?.toIntOrNull() ?: response.firstAirDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (typePath == "tv") TvType.TvSeries else TvType.Movie
        val description = response.overview
        // PERBAIKAN: Menggunakan .toRating() atau konversi manual
        val score = response.voteAverage?.times(10)?.toInt()

        val trailerResponse = app.get(tmdbApi("$typePath/$tmdbId/videos")).parsedSafe<TmdbVideos>()
        val trailer = trailerResponse?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.key

        // PERBAIKAN: Menggunakan toJson() yang diimpor
        val tmdbLink = LoadData(tmdbId, tvType == TvType.TvSeries).toJson()

        if (tvType == TvType.TvSeries) {
            val episodes = response.seasons?.flatMapIndexed { _, season ->
                if (season.seasonNumber == 0 || season.episodeCount == 0) return@flatMapIndexed emptyList()

                (1..season.episodeCount).map { episodeNum ->
                    // PERBAIKAN: Menghapus lambda yang salah dan memindahkan season/episode ke properti episode
                    newEpisode(tmdbLink) {
                        this.name = "S${season.seasonNumber}E$episodeNum"
                        this.season = season.seasonNumber // PERBAIKAN: Unresolved reference 'season'
                        this.episode = episodeNum // PERBAIKAN: Unresolved reference 'episode'
                    }
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, tmdbId, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = score
                if (trailer != null) addTrailer("https://www.youtube.com/watch?v=$trailer", addRaw = true)
            }
        } else {
            return newMovieLoadResponse(title, tmdbId, TvType.Movie, tmdbLink) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = score
                if (trailer != null) addTrailer("https://www.youtube.com/watch?v=$trailer", addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val tmdbId = media.id ?: return false
        val isTv = media.isTv ?: return false

        val typePath = if (isTv) "tv" else "movie"
        val seasonNum = media.season ?: 0
        val episodeNum = media.episode ?: 0

        val urlEmbed = if (isTv) {
            "$VIDSRC_URL/v2/embed/$typePath/$tmdbId/$seasonNum/$episodeNum"
        } else {
            "$VIDSRC_URL/v2/embed/$typePath/$tmdbId"
        }
        
        // Menggunakan ExtractorApi yang sudah diperbaiki
        return Adi21Extractor(VIDSRC_URL).get(urlEmbed, Qualities.Unknown.value, subtitleCallback, callback)
    }
}

// --- EXTRACTOR YANG DIPERBAIKI (Menggunakan ExtractorApi) ---
class Adi21Extractor(override val mainUrl: String) : ExtractorApi() {
    // PERBAIKAN: Mewarisi ExtractorApi
    override val name = "Adi21-vidsrc"
    // PERBAIKAN: Menambahkan supportedTypes
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries) 
    
    // PERBAIKAN: Signature fungsi get sesuai dengan ExtractorApi
    override suspend fun get(
        url: String, 
        quality: Int, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val response = app.get(url).text
        
        // --- LOGIKA UTAMA EKSTRAKSI VIDEO (Placeholder) ---
        val videoUrl = "https://example.com/placeholder_video.m3u8"
        if (videoUrl.isNotBlank()) {
             callback.invoke(
                newExtractorLink(
                    this.name,
                    "Video dari vidsrc.cc",
                    videoUrl, 
                    url, // referer
                    getQualityFromName("720p")
                )
            )
        }
        
        // --- IMPLEMENTASI SUBTITEL KUSTOM ---
        val subFileUrl = url.substringAfter("sub.file=").substringBefore("&")
        val subLabel = url.substringAfter("sub.label=").substringBefore("&")
        val subJsonUrl = url.substringAfter("sub.info=").substringBefore("&")

        // 1. Metode File Tunggal (sub.file)
        if (subFileUrl.isNotBlank() && subFileUrl != url) {
            val label = if (subLabel.isNotBlank() && subLabel != url) subLabel else "Custom Subtitle"
            subtitleCallback.invoke(newSubtitleFile(label, subFileUrl))
        }
        
        // 2. Metode File Berganda (sub.json)
        if (subJsonUrl.isNotBlank() && subJsonUrl != url) {
            try {
                val jsonResponse = app.get(subJsonUrl).parsedSafe<List<CustomSubtitleItem>>() 
                
                jsonResponse?.forEach { sub ->
                    if (sub.file.isNullOrBlank() || sub.label.isNullOrBlank()) return@forEach
                    subtitleCallback.invoke(newSubtitleFile(sub.label, sub.file))
                }
            } catch (e: Exception) {
                // logError(e) 
            }
        }

        return true
    }
}

// --- Data Classes dan Helpers (Tetap sama) ---

// ... (Data class TmdbApiResult, TmdbItem, TmdbDetail, TmdbVideos, LoadData, CustomSubtitleItem)
// ... (Saya menghilangkan data classes yang tidak perlu diulang di sini, tetapi anggap sudah ada di file)

data class CustomSubtitleItem(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("kind") val kind: String? = null
)

// Data Class untuk menyimpan ID TMDB yang akan di-parse ke loadLinks
data class LoadData(
    val id: String? = null, // ID TMDB
    val isTv: Boolean? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

// Detail Film/Serial
data class TmdbDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("genres") val genres: List<Genre>? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("seasons") val seasons: List<Season>? = null,
) {
    data class Genre(@JsonProperty("name") val name: String? = null)
    data class Season(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int = 0, // Default 0
    )
    val data: TmdbDetail
        get() = this
}

// Data Video/Trailer TMDB
data class TmdbVideos(
    @JsonProperty("results") val results: List<VideoItem>? = null
) {
    data class VideoItem(
        @JsonProperty("site") val site: String? = null, // e.g., "YouTube"
        @JsonProperty("type") val type: String? = null, // e.g., "Trailer"
        @JsonProperty("key") val key: String? = null // Video ID
    )
}

// Hasil Pencarian/Homepage
data class TmdbApiResult(
    @JsonProperty("results") val results: List<TmdbItem>
)

data class TmdbItem(
    @JsonProperty("id") val tmdbId: Int,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
)
